package com.revenda.sales.infrastructure.adapters.in.web;

import com.revenda.common.paging.web.PageResponse;
import com.revenda.common.security.ActorContext;
import com.revenda.common.security.ActorContextHolder;
import com.revenda.common.security.ActorType;
import com.revenda.common.security.Role;
import com.revenda.sales.domain.model.PurchaseDetails;
import com.revenda.sales.domain.ports.in.CancelPurchaseUseCase;
import com.revenda.sales.domain.ports.in.CreatePurchaseUseCase;
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

    private static ViewPurchasesUseCase.Viewer viewer() {
        ActorContext actor = ActorContextHolder.get();
        boolean staff = actor.type() == ActorType.USER && (actor.role() == Role.SELLER || actor.role() == Role.ADMIN);
        return new ViewPurchasesUseCase.Viewer(actor.id(), staff);
    }
}
