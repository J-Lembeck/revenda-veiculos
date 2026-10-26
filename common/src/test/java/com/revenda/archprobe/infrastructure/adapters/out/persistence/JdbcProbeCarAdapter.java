package com.revenda.archprobe.infrastructure.adapters.out.persistence;

import com.revenda.archprobe.domain.model.ProbeCar;
import com.revenda.archprobe.domain.ports.out.SaveProbeCarPort;
import com.revenda.common.crypto.BlindIndexer;
import com.revenda.common.crypto.FieldCipher;
import com.revenda.common.time.SqlTime;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;

@RequiredArgsConstructor
public class JdbcProbeCarAdapter implements SaveProbeCarPort {

    private final JdbcClient jdbc;
    private final FieldCipher cipher;
    private final BlindIndexer indexer;

    @Override
    public void save(ProbeCar car) {
        jdbc.sql("INSERT INTO probe_car (id, price_cents, registered_at) VALUES (:id, :price, :at)")
                .param("id", car.id())
                .param("price", car.price().toCents())
                .param("at", SqlTime.utc(car.registeredAt()))
                .update();
    }
}
