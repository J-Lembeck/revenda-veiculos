package com.revenda.sales.domain.model;

/**
 * Quais compras listar: as de uma conta ({@code customerSubject}) ou de todas (nulo, só para a equipe), e só as de um
 * status, se vier.
 */
public record PurchaseFilter(String customerSubject, PurchaseStatus status) {

    @Override
    public String toString() {
        return "PurchaseFilter[status=" + status + "]";
    }
}
