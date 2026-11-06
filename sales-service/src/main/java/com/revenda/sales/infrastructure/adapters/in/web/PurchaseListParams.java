package com.revenda.sales.infrastructure.adapters.in.web;

import com.revenda.common.paging.web.Paged;
import com.revenda.sales.domain.model.PurchaseStatus;
import io.swagger.v3.oas.annotations.Parameter;

/** A consulta da listagem de compras: o status, se vier, e a página. */
record PurchaseListParams(
        @Parameter(description = "Só as compras neste status", example = "AWAITING_PAYMENT") PurchaseStatus status,
        @Parameter(description = "Página, de 0 em diante (padrão 0)", example = "0") @Paged.PageNumber Integer page,
        @Parameter(description = "Itens por página, de 1 a 100 (padrão 20)", example = "20")
        @Paged.PageSize Integer size) implements Paged {
}
