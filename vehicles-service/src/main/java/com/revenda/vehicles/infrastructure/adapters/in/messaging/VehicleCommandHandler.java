package com.revenda.vehicles.infrastructure.adapters.in.messaging;

import com.revenda.common.error.BusinessException;
import com.revenda.common.money.Money;
import com.revenda.vehicles.domain.model.VehiclesError;
import com.revenda.vehicles.domain.ports.in.DeliverVehicleCommand;
import com.revenda.vehicles.domain.ports.in.DeliverVehicleUseCase;
import com.revenda.vehicles.domain.ports.in.MarkVehicleSoldCommand;
import com.revenda.vehicles.domain.ports.in.MarkVehicleSoldUseCase;
import com.revenda.vehicles.domain.ports.in.ReleaseVehicleCommand;
import com.revenda.vehicles.domain.ports.in.ReleaseVehicleUseCase;
import com.revenda.vehicles.domain.ports.in.VehicleCommandOutcome;
import java.time.Instant;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectReader;

/**
 * Lê o envelope {@code { id, type, payload }} da fila e aplica o comando. Devolve se a mensagem está tratada e pode
 * sair da fila — aplicada, repetida ou já em vigor; nos outros casos ela volta depois do <i>visibility timeout</i> e,
 * passado o limite de recebimentos, vai para a DLQ.
 */
@Slf4j
@Component
class VehicleCommandHandler {

    static final String RELEASE_VEHICLE = "ReleaseVehicle";
    static final String MARK_VEHICLE_SOLD = "MarkVehicleSold";
    static final String DELIVER_VEHICLE = "DeliverVehicle";

    private final ObjectReader reader;
    private final ReleaseVehicleUseCase releaseVehicle;
    private final MarkVehicleSoldUseCase markVehicleSold;
    private final DeliverVehicleUseCase deliverVehicle;

    VehicleCommandHandler(ObjectMapper objectMapper, ReleaseVehicleUseCase releaseVehicle,
            MarkVehicleSoldUseCase markVehicleSold, DeliverVehicleUseCase deliverVehicle) {
        this.reader = objectMapper.reader().with(DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES,
                DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES);
        this.releaseVehicle = releaseVehicle;
        this.markVehicleSold = markVehicleSold;
        this.deliverVehicle = deliverVehicle;
    }

    boolean handle(String body, int receiveCount) {
        Envelope message;
        try {
            message = reader.forType(Envelope.class).readValue(body);
        } catch (RuntimeException e) {
            log.error("Mensagem ilegível na fila de comandos do estoque ({}), recebimento {}",
                    e.getClass().getSimpleName(), receiveCount);
            return false;
        }
        try {
            VehicleCommandOutcome outcome = apply(message);
            log.info("Comando {} {}: {}", message.type(), message.id(), outcome);
            return true;
        } catch (BusinessException e) {
            if (VehiclesError.VEHICLE_COMMAND_NOT_YET_APPLICABLE.name().equals(e.getCode())) {
                log.warn("Comando {} {} ainda não se aplica ao veículo; volta para a fila (recebimento {})",
                        message.type(), message.id(), receiveCount);
            } else {
                log.error("Comando {} {} não aplicado ({}), recebimento {}", message.type(), message.id(),
                        e.getCode(), receiveCount);
            }
            return false;
        } catch (RuntimeException e) {
            log.error("Comando {} {} falhou ({}); volta para a fila (recebimento {})", message.type(), message.id(),
                    e.getClass().getSimpleName(), receiveCount);
            return false;
        }
    }

    private VehicleCommandOutcome apply(Envelope message) {
        return switch (message.type()) {
            case RELEASE_VEHICLE -> {
                ReleaseVehicle payload = payload(message, ReleaseVehicle.class);
                yield releaseVehicle.release(
                        new ReleaseVehicleCommand(message.id(), payload.vehicleId(), payload.purchaseId()));
            }
            case MARK_VEHICLE_SOLD -> {
                MarkVehicleSold payload = payload(message, MarkVehicleSold.class);
                yield markVehicleSold.markSold(new MarkVehicleSoldCommand(message.id(), payload.vehicleId(),
                        payload.purchaseId(), Money.ofCents(payload.soldPriceCents()), payload.soldAt()));
            }
            case DELIVER_VEHICLE -> {
                DeliverVehicle payload = payload(message, DeliverVehicle.class);
                yield deliverVehicle.deliver(new DeliverVehicleCommand(message.id(), payload.vehicleId(),
                        payload.purchaseId(), payload.deliveredAt()));
            }
            default -> throw new IllegalArgumentException("Tipo de comando desconhecido");
        };
    }

    private <T> T payload(Envelope message, Class<T> type) {
        return reader.forType(type).readValue(message.payload());
    }

    record Envelope(UUID id, String type, JsonNode payload) {
    }

    record ReleaseVehicle(UUID vehicleId, UUID purchaseId) {
    }

    record MarkVehicleSold(UUID vehicleId, UUID purchaseId, long soldPriceCents, Instant soldAt) {
    }

    record DeliverVehicle(UUID vehicleId, UUID purchaseId, Instant deliveredAt) {
    }
}
