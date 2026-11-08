package com.revenda.customers.infrastructure.adapters.in.web;

import com.revenda.customers.domain.model.CustomerAccess;
import com.revenda.customers.domain.model.CustomerDetails;
import com.revenda.customers.domain.model.CustomerExport;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Schema(description = "Tudo o que o cadastro guarda sobre o titular, para acesso e portabilidade (LGPD, art. 18).")
record CustomerExportResponse(
        Instant exportedAt,
        ExportedCustomer customer,
        @Schema(description = "Quem acessou ou mudou o cadastro, do mais antigo ao mais recente")
        List<ExportedAccess> history,
        @Schema(description = "Onde está o resto do que a revenda guarda sobre o titular")
        List<String> elsewhere) {

    static final List<String> ELSEWHERE = List.of(
            "As compras ficam no serviço de vendas: GET /purchases, com a mesma conta, lista cada uma com a linha do "
                    + "tempo.",
            "O CPF sai mascarado também aqui; o inteiro está guardado cifrado e é o mesmo que você informou no "
                    + "cadastro.");

    static CustomerExportResponse of(CustomerExport export) {
        return new CustomerExportResponse(export.exportedAt(), ExportedCustomer.of(export),
                export.history().stream().map(ExportedAccess::of).toList(), ELSEWHERE);
    }

    @Override
    public String toString() {
        return "CustomerExportResponse[dados pessoais omitidos]";
    }

    @Schema(description = "O cadastro, com e-mail, telefone, nascimento e endereço em claro e o CPF mascarado.")
    record ExportedCustomer(
            UUID id,
            @Schema(example = "Maria Exemplo da Silva") String fullName,
            @Schema(description = "Só os seis dígitos do meio", example = "***.982.247-**") String cpfMasked,
            @Schema(example = "maria.exemplo@example.test") String email,
            @Schema(example = "+5511999990000") String phone,
            @Schema(example = "1990-05-17") LocalDate birthDate,
            AddressBody address,
            @Schema(example = "ACTIVE") String status,
            Instant createdAt,
            Instant updatedAt,
            @Schema(description = "Quando o titular pediu a exclusão; nulo se não pediu", nullable = true)
            Instant deletionRequestedAt) {

        static ExportedCustomer of(CustomerExport export) {
            CustomerDetails details = export.customer().details();
            return new ExportedCustomer(export.customer().id(), details.fullName(), details.cpf().masked(),
                    export.customer().email().address(), details.phone().number(), details.birthDate(),
                    AddressBody.of(details.address()), export.customer().status().name(),
                    export.customer().createdAt(), export.customer().updatedAt(), export.deletionRequestedAt());
        }

        @Override
        public String toString() {
            return "ExportedCustomer[id=" + id + "]";
        }
    }

    @Schema(description = "Um registro da auditoria sobre o cadastro, sem a identidade de quem fez.")
    record ExportedAccess(
            Instant at,
            @Schema(example = "CUSTOMER_PAYER_DATA_READ") String action,
            @Schema(description = "`OWNER` (o titular), `STAFF` (equipe da revenda), `SERVICE` ou `SYSTEM`",
                    example = "SERVICE") String actor,
            @Schema(description = "O papel, quando foi a equipe", example = "ADMIN", nullable = true) String role,
            @Schema(description = "O serviço, quando foi um", example = "sales-service", nullable = true)
            String service,
            @Schema(example = "Dados do pagador lidos para a compra 3f2b8c4e-9a1d-4c7b-8e5f-1a2b3c4d5e6f")
            String summary,
            @Schema(description = "O motivo declarado, quando a ação pede um", nullable = true) String reason,
            @Schema(nullable = true) UUID purchaseId) {

        static ExportedAccess of(CustomerAccess access) {
            return new ExportedAccess(access.at(), access.action(), access.actor().name(), access.role(),
                    access.service(), access.summary(), access.reason(), access.purchaseId());
        }
    }
}
