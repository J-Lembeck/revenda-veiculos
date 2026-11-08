package com.revenda.sales.infrastructure.adapters.in.web;

import com.revenda.common.paging.web.PageResponse;
import com.revenda.common.security.ActorContext;
import com.revenda.common.security.ActorContextHolder;
import com.revenda.common.security.ActorType;
import com.revenda.common.security.Role;
import com.revenda.sales.domain.model.PurchaseDetails;
import com.revenda.sales.domain.ports.in.CancelPurchaseUseCase;
import com.revenda.sales.domain.ports.in.ConfirmPickupUseCase;
import com.revenda.sales.domain.ports.in.CreatePurchaseUseCase;
import com.revenda.sales.domain.ports.in.GetTransferDocumentUseCase;
import com.revenda.sales.domain.ports.in.ViewPurchasesUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Compras", description = "A compra de um veículo, de ponta a ponta: reservar, receber o código de "
        + "pagamento, acompanhar e desistir. O pagamento só se confirma pelo aviso assinado do gateway.")
@RestController
@RequestMapping("/purchases")
@RequiredArgsConstructor
class PurchaseController {

    private final CreatePurchaseUseCase createPurchase;
    private final ViewPurchasesUseCase viewPurchases;
    private final CancelPurchaseUseCase cancelPurchase;
    private final ConfirmPickupUseCase confirmPickup;
    private final GetTransferDocumentUseCase transferDocument;

    @Operation(summary = "Comprar: reservar e receber o código de pagamento", description = "UC-08 (DP-02). Numa "
            + "chamada só: confere o cadastro (RN-05), reserva o veículo pelo preço do momento (RN-09) e emite o Pix, "
            + "que vale por `PAYMENT_WINDOW` (30 minutos por padrão, RN-11). Uma compra em andamento por cliente "
            + "(RN-10). Se o estoque ou o gateway não respondem, a compra fica `FAILED`, a reserva e a cobrança são "
            + "desfeitas pelo outbox e a resposta é `503` — tente de novo com uma `Idempotency-Key` nova. Grava a "
            + "auditoria `PURCHASE_CREATED`.",
            parameters = @Parameter(in = ParameterIn.HEADER, name = "Idempotency-Key", required = true,
                    description = "Chave do pedido, de 16 a 80 letras, números, `-` ou `_`: gere um UUID novo a cada "
                            + "compra. Repetir a chave com o mesmo corpo devolve a compra como ela está, sem reservar "
                            + "de novo — `201`, ou o mesmo `409`/`503` da primeira vez; com outro corpo, `422`.",
                    schema = @Schema(type = "string", pattern = "^[A-Za-z0-9_-]{16,80}$")))
    @ApiResponse(responseCode = "201", description = "Reservado e com código: `AWAITING_PAYMENT`, com o Pix e o "
            + "vencimento em `payment`.",
            headers = @Header(name = "Location", description = "`/purchases/{id}`, onde a compra é acompanhada"))
    @ApiResponse(responseCode = "400", description = "`VALIDATION_FAILED` com `vehicleId` `REQUIRED` ou `TYPE_INVALID`; "
            + "`Idempotency-Key` fora do formato vem em `fields` com `FORMAT_INVALID`.")
    @ApiResponse(responseCode = "409", description = "`VEHICLE_UNAVAILABLE` — outra pessoa reservou antes, ou o "
            + "veículo não está à venda (a compra fica `REJECTED`); `ACTIVE_PURCHASE_EXISTS` — já há uma compra em "
            + "andamento; `PURCHASE_IN_PROGRESS` — a repetição chegou com a primeira ainda reservando.")
    @ApiResponse(responseCode = "422", description = "`CUSTOMER_PROFILE_REQUIRED` — a conta não tem cadastro de "
            + "comprador; `CUSTOMER_UNDERAGE` — menor de 18 anos; `IDEMPOTENCY_KEY_REUSED` — a mesma chave com outro "
            + "corpo.")
    @ApiResponse(responseCode = "428", description = "`IDEMPOTENCY_KEY_REQUIRED` — sem `Idempotency-Key`; nada é "
            + "reservado.")
    @ApiResponse(responseCode = "503", description = "`DEPENDENCY_UNAVAILABLE` — o cadastro, o estoque ou o gateway não "
            + "respondeu; nada fica preso (a compra, se começou, fica `FAILED` e a reserva é desfeita).")
    @PostMapping
    ResponseEntity<PurchaseResponse> create(@Parameter(hidden = true) @AuthenticationPrincipal Jwt token,
            @Valid @RequestBody PurchaseRequest request) {
        PurchaseDetails details = createPurchase.create(request.toCommand(token.getSubject()));
        return ResponseEntity.created(URI.create("/purchases/" + details.purchase().id()))
                .body(PurchaseResponse.of(details));
    }

    @Operation(summary = "Listar compras", description = "UC-13. O cliente vê só as próprias; a equipe (`SELLER`, "
            + "`ADMIN`), todas. A mais recente primeiro, com o filtro opcional de `status`. Os mesmos campos do "
            + "detalhe, sem a linha do tempo.")
    @ApiResponse(responseCode = "200", description = "A página de compras.")
    @ApiResponse(responseCode = "400", description = "`VALIDATION_FAILED` — `page` negativa, `size` fora de 1 a 100 "
            + "(`OUT_OF_RANGE`) ou `status` desconhecido (`TYPE_INVALID`).")
    @GetMapping
    PageResponse<PurchaseSummaryResponse> list(@Valid @ParameterObject PurchaseListParams params) {
        return PageResponse.of(viewPurchases.list(params.status(), params.toPaging(), viewer()),
                PurchaseSummaryResponse::of);
    }

    @Operation(summary = "Ver a compra com a linha do tempo", description = "UC-13. O dono vê a própria compra; a "
            + "equipe, qualquer uma. A linha do tempo traz cada mudança, com o status, o pagamento e o código do "
            + "motivo (`reason`). Compra de outra conta responde `404`, como a que não existe (RN-15).")
    @ApiResponse(responseCode = "200", description = "A compra.")
    @ApiResponse(responseCode = "400", description = "`VALIDATION_FAILED` com `id` `TYPE_INVALID`.")
    @ApiResponse(responseCode = "404", description = "`NOT_FOUND` — não existe, ou é de outro cliente.")
    @GetMapping("/{id}")
    PurchaseResponse view(@Parameter(description = "Id da compra") @PathVariable UUID id) {
        return PurchaseResponse.of(viewPurchases.view(id, viewer()));
    }

    @Operation(summary = "Desistir da compra", description = "UC-11 (RN-13). Só o dono. Aguardando o pagamento: a "
            + "cobrança é cancelada e o veículo volta à venda. Paga: o valor é estornado inteiro (o pagamento fica "
            + "`REFUND_REQUESTED` até o gateway confirmar, `REFUNDED`) e o veículo volta à venda. As duas pelo outbox, "
            + "com retentativa. Grava a auditoria `PURCHASE_CANCELLED`. Não cria nada: sem `Idempotency-Key`; repetir "
            + "responde `409`.")
    @ApiResponse(responseCode = "200", description = "Cancelada: `CANCELLED` por `CUSTOMER_GAVE_UP`.")
    @ApiResponse(responseCode = "400", description = "`VALIDATION_FAILED` com `id` `TYPE_INVALID`.")
    @ApiResponse(responseCode = "404", description = "`NOT_FOUND` — não existe, ou é de outro cliente.")
    @ApiResponse(responseCode = "409", description = "`PURCHASE_IN_PROGRESS` — ainda reservando o veículo ou emitindo a "
            + "cobrança (segundos), tente de novo; `PURCHASE_NOT_CANCELLABLE` — já encerrada ou retirada (depois da "
            + "retirada é devolução, fora do escopo).")
    @PostMapping("/{id}/cancellation")
    PurchaseResponse cancel(@Parameter(hidden = true) @AuthenticationPrincipal Jwt token,
            @Parameter(description = "Id da compra") @PathVariable UUID id) {
        return PurchaseResponse.of(cancelPurchase.cancel(id, token.getSubject()));
    }

    @Operation(summary = "Confirmar a retirada", description = "UC-12 (RN-14). Só a equipe, com a compra paga. O CPF do "
            + "documento que a pessoa apresentou na loja vai no corpo (nunca na URL) e é conferido no cadastro pelo "
            + "blind index, sem revelar o CPF de lá. É o pivô da saga: a compra fica `COMPLETED`, e a baixa no estoque "
            + "(`DELIVERED`) e o documento de transferência saem em seguida pelo outbox — o documento fica pronto em "
            + "segundos em `GET /purchases/{id}/document`. Grava `PURCHASE_PICKUP_CONFIRMED`; o CPF que não é o do "
            + "comprador grava `PURCHASE_PICKUP_REJECTED`, com quem tentou. Não cria nada: sem `Idempotency-Key`; "
            + "repetir responde `409`.")
    @ApiResponse(responseCode = "200", description = "Retirada: `COMPLETED`.")
    @ApiResponse(responseCode = "400", description = "`VALIDATION_FAILED` com `id` `TYPE_INVALID`, ou `cpf` "
            + "`REQUIRED` ou `CPF_INVALID` (dígitos que não conferem); nada é conferido.")
    @ApiResponse(responseCode = "404", description = "`NOT_FOUND` — a compra não existe.")
    @ApiResponse(responseCode = "409", description = "`PURCHASE_NOT_PAID` — a compra não está paga: ainda aguarda o "
            + "pagamento, foi encerrada ou já foi retirada.")
    @ApiResponse(responseCode = "422", description = "`IDENTITY_MISMATCH` — o CPF apresentado não é o do comprador; "
            + "a compra continua paga.")
    @ApiResponse(responseCode = "503", description = "`DEPENDENCY_UNAVAILABLE` — o cadastro não respondeu; nada "
            + "mudou, tente de novo.")
    @PostMapping("/{id}/pickup")
    PurchaseResponse pickup(@Parameter(description = "Id da compra") @PathVariable UUID id,
            @Valid @RequestBody PickupRequest request) {
        return PurchaseResponse.of(confirmPickup.confirm(id, request.presentedCpf()));
    }

    @Operation(summary = "Baixar o documento de transferência", description = "DP-06. O dono da compra e a equipe. "
            + "Devolve um link assinado do PDF no bucket privado, que vale 5 minutos e é gerado a cada pedido (R16); o "
            + "PDF tem os campos do ATPV-e e não substitui o documento do Detran. Cada link entregue grava "
            + "`PURCHASE_DOCUMENT_DOWNLOADED`. Compra de outra conta responde `404`, como a que não existe (RN-15).")
    @ApiResponse(responseCode = "200", description = "O link e até quando ele vale; `Cache-Control: no-store`.")
    @ApiResponse(responseCode = "400", description = "`VALIDATION_FAILED` com `id` `TYPE_INVALID`.")
    @ApiResponse(responseCode = "404", description = "`NOT_FOUND` — não existe, ou é de outro cliente.")
    @ApiResponse(responseCode = "409", description = "`DOCUMENT_NOT_READY` — a compra ainda não foi retirada, ou "
            + "acabou de ser e o documento sai em segundos; tente de novo.")
    @GetMapping("/{id}/document")
    ResponseEntity<DocumentLinkResponse> document(@Parameter(description = "Id da compra") @PathVariable UUID id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(DocumentLinkResponse.of(transferDocument.link(id, viewer())));
    }

    private static ViewPurchasesUseCase.Viewer viewer() {
        ActorContext actor = ActorContextHolder.get();
        boolean staff = actor.type() == ActorType.USER && (actor.role() == Role.SELLER || actor.role() == Role.ADMIN);
        return new ViewPurchasesUseCase.Viewer(actor.id(), staff);
    }
}
