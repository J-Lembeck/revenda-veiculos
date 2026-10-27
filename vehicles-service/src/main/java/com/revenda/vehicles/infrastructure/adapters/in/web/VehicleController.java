package com.revenda.vehicles.infrastructure.adapters.in.web;

import com.revenda.common.security.ActorContext;
import com.revenda.common.security.ActorContextHolder;
import com.revenda.common.security.ActorType;
import com.revenda.common.security.Role;
import com.revenda.vehicles.domain.model.Vehicle;
import com.revenda.vehicles.domain.ports.in.EditVehicleCommand;
import com.revenda.vehicles.domain.ports.in.EditVehicleUseCase;
import com.revenda.vehicles.domain.ports.in.ListSoldVehiclesUseCase;
import com.revenda.vehicles.domain.ports.in.ListVehiclesForSaleUseCase;
import com.revenda.vehicles.domain.ports.in.RegisterVehicleCommand;
import com.revenda.vehicles.domain.ports.in.RegisterVehicleUseCase;
import com.revenda.vehicles.domain.ports.in.ViewVehicleUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.groups.Default;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Veículos", description = "Cadastro e edição pela equipe; vitrine e detalhe para qualquer um; vendidos "
        + "para a equipe.")
@RestController
@RequestMapping("/vehicles")
@RequiredArgsConstructor
class VehicleController {

    private static final String VALIDATION = "`VALIDATION_FAILED`, com cada campo em `fields`: `REQUIRED`, "
            + "`SIZE_INVALID`, `TYPE_INVALID`, `OUT_OF_RANGE` em `mileageKm` e as regras da RN-01 — `PLATE_INVALID`, "
            + "`VIN_INVALID`, `RENAVAM_INVALID`, `YEAR_INVALID` em `manufactureYear` ou `modelYear`, `PRICE_INVALID`.";
    private static final String DUPLICATE = "`PLATE_ALREADY_REGISTERED`, `VIN_ALREADY_REGISTERED` ou "
            + "`RENAVAM_ALREADY_REGISTERED` — o campo já é de outro veículo do estoque.";
    private static final String PAGE = "`VALIDATION_FAILED` — `page` negativa, `size` fora de 1 a 100 ou filtro "
            + "negativo (`OUT_OF_RANGE`); valor que não é número (`TYPE_INVALID`).";

    private final RegisterVehicleUseCase registerVehicle;
    private final EditVehicleUseCase editVehicle;
    private final ListVehiclesForSaleUseCase listVehiclesForSale;
    private final ViewVehicleUseCase viewVehicle;
    private final ListSoldVehiclesUseCase listSoldVehicles;

    @Operation(summary = "Cadastrar veículo", description = "UC-01. Entra à venda (`AVAILABLE`), na versão 0. Todas "
            + "as regras da RN-01 são conferidas de uma vez. Grava a auditoria `VEHICLE_CREATED`.",
            parameters = @Parameter(in = ParameterIn.HEADER, name = "Idempotency-Key", required = true,
                    description = "Chave do pedido, de 16 a 80 letras, números, `-` ou `_`: gere um UUID novo a "
                            + "cada cadastro. Repetir a chave com o mesmo corpo devolve a mesma resposta sem "
                            + "cadastrar de novo; com outro corpo, `422`.",
                    schema = @Schema(type = "string", pattern = "^[A-Za-z0-9_-]{16,80}$")))
    @ApiResponse(responseCode = "201", description = "Cadastrado.",
            headers = @Header(name = "Location", description = "`/vehicles/{id}` do veículo novo"))
    @ApiResponse(responseCode = "400", description = VALIDATION
            + " `Idempotency-Key` fora do formato vem em `fields` com `FORMAT_INVALID`.")
    @ApiResponse(responseCode = "409", description = DUPLICATE)
    @ApiResponse(responseCode = "422", description = "`IDEMPOTENCY_KEY_REUSED` — a mesma chave com outro corpo.")
    @ApiResponse(responseCode = "428", description = "`IDEMPOTENCY_KEY_REQUIRED` — sem `Idempotency-Key`; nada é "
            + "cadastrado.")
    @PostMapping
    ResponseEntity<StaffVehicleResponse> register(@Valid @RequestBody VehicleRequest request) {
        Vehicle vehicle = registerVehicle.register(new RegisterVehicleCommand(request.toDetails()));
        return ResponseEntity.created(URI.create("/vehicles/" + vehicle.id())).body(StaffVehicleResponse.of(vehicle));
    }

    @Operation(summary = "Editar veículo", description = "UC-02. Manda o veículo inteiro com a `version` lida; só grava "
            + "se o estoque ainda está nela e o veículo segue à venda ou reservado. Não muda status nem compra: o "
            + "preço novo de um reservado não muda a compra em curso (RN-02). Devolve a `version` nova e grava "
            + "`VEHICLE_UPDATED` com antes e depois.")
    @ApiResponse(responseCode = "200", description = "Editado.")
    @ApiResponse(responseCode = "400", description = VALIDATION + " Sem `version`: `version` `REQUIRED`.")
    @ApiResponse(responseCode = "404", description = "`NOT_FOUND` — não há veículo com esse id.")
    @ApiResponse(responseCode = "409", description = "`VERSION_CONFLICT` — outra pessoa editou depois da leitura; "
            + "`VEHICLE_NOT_EDITABLE` — vendido ou entregue; ou " + DUPLICATE)
    @PutMapping("/{id}")
    StaffVehicleResponse edit(@Parameter(description = "Id do veículo") @PathVariable UUID id,
            @Validated({Default.class, VehicleRequest.Edit.class}) @RequestBody VehicleRequest request) {
        return StaffVehicleResponse.of(
                editVehicle.edit(new EditVehicleCommand(id, request.toDetails(), request.version())));
    }

    @Operation(summary = "Vitrine", description = "UC-03. Só veículos à venda, do mais barato ao mais caro (RN-03), com "
            + "o `id` como desempate. Marca e modelo casam com o nome inteiro, sem diferença de maiúsculas; os anos "
            + "são do modelo; as faixas incluem as pontas. O item não traz placa, chassi nem RENAVAM.")
    @ApiResponse(responseCode = "200", description = "A página pedida; além do fim, `items` vazio.")
    @ApiResponse(responseCode = "400", description = PAGE)
    @GetMapping
    PageResponse<PublicVehicleResponse> listForSale(@Valid @ParameterObject ShowcaseParams params) {
        return PageResponse.of(listVehiclesForSale.list(params.toFilter(), params.toPaging()),
                PublicVehicleResponse::of);
    }

    @Operation(summary = "Vendidos", description = "UC-05. Vendidos e entregues, do menor para o maior preço de venda "
            + "(RN-04), com o `id` como desempate, no formato da equipe.")
    @ApiResponse(responseCode = "200", description = "A página pedida; além do fim, `items` vazio.")
    @ApiResponse(responseCode = "400", description = PAGE)
    @GetMapping("/sold")
    PageResponse<StaffVehicleResponse> listSold(@Valid @ParameterObject PageParams params) {
        return PageResponse.of(listSoldVehicles.list(params.toPaging()), StaffVehicleResponse::of);
    }

    @Operation(summary = "Detalhe do veículo", description = "UC-04. Sem token ou como cliente: só o veículo à venda, "
            + "no formato da vitrine. A equipe (`SELLER`, `ADMIN`) vê qualquer status, com placa, chassi e RENAVAM.")
    @ApiResponse(responseCode = "200", description = "O veículo, no formato de quem pede.", content = @Content(
            schema = @Schema(oneOf = {PublicVehicleResponse.class, StaffVehicleResponse.class})))
    @ApiResponse(responseCode = "404", description = "`NOT_FOUND` — não existe ou, para o público, não está à venda.")
    @GetMapping("/{id}")
    Object view(@Parameter(description = "Id do veículo") @PathVariable UUID id) {
        boolean staff = isStaff(ActorContextHolder.get());
        Vehicle vehicle = viewVehicle.view(id, staff);
        return staff ? StaffVehicleResponse.of(vehicle) : PublicVehicleResponse.of(vehicle);
    }

    private static boolean isStaff(ActorContext actor) {
        return actor.type() == ActorType.USER && (actor.role() == Role.SELLER || actor.role() == Role.ADMIN);
    }
}
