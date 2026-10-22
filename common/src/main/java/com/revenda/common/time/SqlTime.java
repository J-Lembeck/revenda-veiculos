package com.revenda.common.time;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public final class SqlTime {

    private SqlTime() {
    }

    public static OffsetDateTime utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    public static OffsetDateTime utcOrNull(Instant instant) {
        return instant != null ? utc(instant) : null;
    }

    public static Instant instantOrNull(ResultSet row, String column) throws SQLException {
        OffsetDateTime value = row.getObject(column, OffsetDateTime.class);
        return value != null ? value.toInstant() : null;
    }
}
