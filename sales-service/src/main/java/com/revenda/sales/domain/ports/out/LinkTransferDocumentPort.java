package com.revenda.sales.domain.ports.out;

import com.revenda.sales.domain.model.DocumentLink;

/** Um endereço temporário, assinado, para baixar o documento guardado em {@code key}; assinar não chama ninguém. */
public interface LinkTransferDocumentPort {

    DocumentLink link(String key);
}
