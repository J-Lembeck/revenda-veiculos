package com.revenda.vehicles.domain.ports.out;

import com.revenda.common.paging.Page;
import com.revenda.common.paging.Paging;
import com.revenda.vehicles.domain.model.ShowcaseFilter;
import com.revenda.vehicles.domain.model.Vehicle;
import java.util.Optional;
import java.util.UUID;

public interface LoadVehiclePort {

    Optional<Vehicle> findById(UUID id);

    /** Os veículos à venda que passam no filtro, por preço crescente e id como desempate. */
    Page<Vehicle> findForSale(ShowcaseFilter filter, Paging paging);

    /** Os veículos vendidos ou entregues, por preço de venda crescente e id como desempate. */
    Page<Vehicle> findSold(Paging paging);
}
