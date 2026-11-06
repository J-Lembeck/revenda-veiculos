package com.revenda.sales.domain.model;

import com.revenda.common.money.Money;

/**
 * Como o estoque respondeu ao pedido de reserva: reservou pelo preço do momento (RN-09), recusou porque o veículo
 * não está à venda, ou não respondeu — e aí não se sabe se reservou.
 */
public sealed interface VehicleReservation {

    record Reserved(Money price) implements VehicleReservation {
    }

    record Unavailable() implements VehicleReservation {
    }

    record Unknown() implements VehicleReservation {
    }
}
