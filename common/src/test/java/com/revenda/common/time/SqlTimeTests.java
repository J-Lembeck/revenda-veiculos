package com.revenda.common.time;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class SqlTimeTests {

    private static final Instant RESERVED_AT = Instant.parse("2026-10-22T14:32:00Z");

    @Test
    void writesInstantsAsUtcOffsets() {
        assertThat(SqlTime.utc(RESERVED_AT)).isEqualTo(OffsetDateTime.of(2026, 10, 22, 14, 32, 0, 0, ZoneOffset.UTC));
        assertThat(SqlTime.utcOrNull(RESERVED_AT).getOffset()).isEqualTo(ZoneOffset.UTC);
        assertThat(SqlTime.utcOrNull(null)).isNull();
    }

    @Test
    void readsTimestamptzBackAsTheSameInstant() throws SQLException {
        ResultSet row = mock(ResultSet.class);
        OffsetDateTime inSaoPaulo = OffsetDateTime.of(2026, 10, 22, 11, 32, 0, 0, ZoneOffset.ofHours(-3));
        when(row.getObject("reserved_at", OffsetDateTime.class)).thenReturn(inSaoPaulo);
        when(row.getObject("paid_at", OffsetDateTime.class)).thenReturn(null);

        assertThat(SqlTime.instantOrNull(row, "reserved_at")).isEqualTo(RESERVED_AT);
        assertThat(SqlTime.instantOrNull(row, "paid_at")).isNull();
    }
}
