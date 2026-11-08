package com.revenda.sales.domain.ports.in;

import java.util.UUID;

/**
 * Emite o documento de transferência da compra retirada, pelo comando do outbox: lê o comprador e o veículo na hora,
 * gera o PDF, guarda no bucket e registra na compra. Repetir não emite outro; falha de vizinho ou do bucket lança, e o
 * outbox tenta de novo.
 */
public interface GenerateTransferDocumentUseCase {

    void generate(UUID purchaseId);
}
