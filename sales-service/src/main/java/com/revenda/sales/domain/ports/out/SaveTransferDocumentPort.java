package com.revenda.sales.domain.ports.out;

/**
 * Guarda o PDF no bucket privado dos documentos, cifrado com a chave deles. A mesma chave grava por cima: repetir não
 * deixa dois documentos da mesma compra.
 */
public interface SaveTransferDocumentPort {

    void save(String key, byte[] pdf);
}
