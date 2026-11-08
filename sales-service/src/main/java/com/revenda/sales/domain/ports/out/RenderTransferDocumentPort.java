package com.revenda.sales.domain.ports.out;

import com.revenda.sales.domain.model.TransferDocument;

/** O documento de transferência como PDF. */
public interface RenderTransferDocumentPort {

    byte[] render(TransferDocument document);
}
