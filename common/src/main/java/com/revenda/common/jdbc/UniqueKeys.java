package com.revenda.common.jdbc;

import com.revenda.common.error.BusinessException;
import com.revenda.common.error.ErrorCode;
import java.util.Map;
import java.util.function.Supplier;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;
import org.springframework.dao.DuplicateKeyException;

/**
 * Traduz a violação de um índice único conhecido no erro de negócio dele, pelo nome do índice que o Postgres
 * devolve; a violação de outro índice segue como veio.
 */
public final class UniqueKeys {

    private UniqueKeys() {
    }

    public static <T> T translating(Map<String, ? extends ErrorCode> errorsByKey, Supplier<T> statement) {
        try {
            return statement.get();
        } catch (DuplicateKeyException e) {
            ErrorCode duplicate = errorsByKey.get(violatedKey(e));
            if (duplicate == null) {
                throw e;
            }
            throw BusinessException.of(duplicate);
        }
    }

    static String violatedKey(DuplicateKeyException e) {
        if (e.getMostSpecificCause() instanceof PSQLException psql) {
            ServerErrorMessage server = psql.getServerErrorMessage();
            return server == null ? null : server.getConstraint();
        }
        return null;
    }
}
