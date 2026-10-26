package com.revenda.common.crypto;

import java.util.UUID;

/** Onde o campo cifrado mora: entra na cifra, e a mesma cifra não abre em outra tabela, coluna ou linha. */
public record FieldRef(String table, String column, UUID rowId) {

    public FieldRef {
        if (table == null || table.isBlank() || column == null || column.isBlank() || rowId == null) {
            throw new IllegalArgumentException("Campo cifrado precisa de tabela, coluna e linha");
        }
    }
}
