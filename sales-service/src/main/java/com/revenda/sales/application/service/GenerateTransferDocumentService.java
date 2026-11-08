package com.revenda.sales.application.service;

import com.revenda.sales.domain.model.Purchase;
import com.revenda.sales.domain.model.TransferBuyer;
import com.revenda.sales.domain.model.TransferDocument;
import com.revenda.sales.domain.model.TransferVehicle;
import com.revenda.sales.domain.ports.in.GenerateTransferDocumentUseCase;
import com.revenda.sales.domain.ports.out.LoadPurchasePort;
import com.revenda.sales.domain.ports.out.LoadTransferBuyerPort;
import com.revenda.sales.domain.ports.out.LoadTransferVehiclePort;
import com.revenda.sales.domain.ports.out.LockPurchasePort;
import com.revenda.sales.domain.ports.out.RenderTransferDocumentPort;
import com.revenda.sales.domain.ports.out.SavePurchasePort;
import com.revenda.sales.domain.ports.out.SaveTransferDocumentPort;
import java.time.Clock;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Passo 7 da saga, depois do pivô: só repete até dar certo. Lê o comprador e o veículo e guarda o PDF sem transação
 * aberta, sempre na mesma chave; depois trava a compra e registra o documento. Se o registro não chega a gravar, a
 * repetição grava o PDF por cima e registra — nunca sobram dois documentos nem duas linhas na linha do tempo.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GenerateTransferDocumentService implements GenerateTransferDocumentUseCase {

    private final LoadPurchasePort purchases;
    private final LockPurchasePort locks;
    private final SavePurchasePort savedPurchases;
    private final LoadTransferBuyerPort buyers;
    private final LoadTransferVehiclePort vehicles;
    private final RenderTransferDocumentPort renderer;
    private final SaveTransferDocumentPort documents;
    private final SagaOutbox outbox;
    private final TransactionTemplate transaction;
    private final Clock clock;

    @Override
    public void generate(UUID purchaseId) {
        Purchase purchase = purchases.load(purchaseId)
                .orElseThrow(() -> new IllegalStateException("Documento pedido para a compra " + purchaseId
                        + ", que não existe"));
        if (purchase.documentKey() != null) {
            log.info("Compra {} já tem documento de transferência; o comando repetido não emite outro", purchaseId);
            return;
        }
        TransferBuyer buyer = buyers.loadBuyer(purchase.customerId(), purchaseId);
        TransferVehicle vehicle = vehicles.load(purchase.vehicleId());
        String key = TransferDocument.keyOf(purchase);
        documents.save(key, renderer.render(new TransferDocument(purchase, buyer, vehicle, clock.instant())));
        transaction.executeWithoutResult(status -> locks.lock(purchaseId).orElseThrow()
                .recordDocument(key, clock.instant())
                .ifPresent(change -> {
                    savedPurchases.update(change);
                    outbox.publishAfter(change);
                }));
    }
}
