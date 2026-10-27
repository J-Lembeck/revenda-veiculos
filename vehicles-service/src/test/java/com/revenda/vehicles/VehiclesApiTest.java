package com.revenda.vehicles;

import com.revenda.common.test.ApiDriver;
import com.revenda.common.test.PostgresTestDatabase;
import com.revenda.common.test.SyntheticData;
import com.revenda.common.test.TestTokenConfiguration;
import com.revenda.common.test.TestTokens;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.JsonNode;

/** O serviço de pé, com o banco de teste e tokens de teste, chamado pelo HTTP. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import(TestTokenConfiguration.class)
@TestPropertySource(properties = "revenda.security.oidc.issuer-uri=" + TestTokens.ISSUER)
abstract class VehiclesApiTest {

    protected static final String SELLER = "5d1e7a40-vendedora";
    protected static final String ADMIN = "9a3c2f81-administrador";
    protected static final String CUSTOMER = "2b6f4d93-cliente";

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    protected JdbcClient jdbc;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.register(registry, "vehicles");
    }

    protected ApiDriver api() {
        return new ApiDriver(rest);
    }

    /** Um veículo válido e inédito, como o corpo do {@code POST /vehicles}; o que vier em {@code changes} troca. */
    protected static Map<String, Object> vehicleBody(Map<String, Object> changes) {
        Map<String, Object> body = new HashMap<>();
        body.put("brand", "Chevrolet");
        body.put("model", "Onix");
        body.put("trimName", "1.0 Turbo LT");
        body.put("manufactureYear", 2023);
        body.put("modelYear", 2024);
        body.put("color", "Prata");
        body.put("mileageKm", 18_500);
        body.put("plate", SyntheticData.plate());
        body.put("vin", SyntheticData.vin());
        body.put("renavam", SyntheticData.renavam());
        body.put("priceCents", 8_990_000L);
        body.putAll(changes);
        return body;
    }

    protected static Map<String, Object> vehicleBody() {
        return vehicleBody(Map.of());
    }

    /** Cadastra pelo {@code POST} um veículo do {@link #vehicleBody(Map)} com {@code changes}. */
    protected Registered register(Map<String, Object> changes) {
        Map<String, Object> body = vehicleBody(changes);
        String id = api().asSeller(SELLER).post("/vehicles", body).getBody().get("id").asString();
        return new Registered(UUID.fromString(id), body);
    }

    protected Registered register() {
        return register(Map.of());
    }

    /** Põe o veículo como reservado por uma compra, como faria a reserva interna. */
    protected void markReserved(UUID id) {
        jdbc.sql("UPDATE vehicle SET status = 'RESERVED', purchase_id = :purchase WHERE id = :id")
                .param("purchase", UUID.randomUUID()).param("id", id).update();
    }

    /** Põe o veículo como vendido pelo preço dado, ou já entregue, como fariam os comandos da compra. */
    protected void markSold(UUID id, long soldPriceCents, boolean delivered) {
        jdbc.sql("""
                        UPDATE vehicle
                           SET status = :status, purchase_id = :purchase, sold_price_cents = :soldPriceCents,
                               sold_at = created_at, delivered_at = CASE WHEN :delivered THEN created_at END
                         WHERE id = :id""")
                .param("status", delivered ? "DELIVERED" : "SOLD")
                .param("purchase", UUID.randomUUID())
                .param("soldPriceCents", soldPriceCents)
                .param("delivered", delivered)
                .param("id", id)
                .update();
    }

    protected Map<String, Object> row(UUID id) {
        return jdbc.sql("SELECT * FROM vehicle WHERE id = :id").param("id", id).query().singleRow();
    }

    /** Os {@code fields} do erro como {@code "campo CÓDIGO"}. */
    protected static List<String> fieldsOf(ResponseEntity<JsonNode> response) {
        List<String> fields = new ArrayList<>();
        response.getBody().get("fields").forEach(f -> fields.add(f.get("field").asString() + " " + f.get("code").asString()));
        return fields;
    }

    /** Os ids dos {@code items} de uma página, na ordem da resposta. */
    protected static List<UUID> idsOf(ResponseEntity<JsonNode> response) {
        List<UUID> ids = new ArrayList<>();
        response.getBody().get("items").forEach(item -> ids.add(UUID.fromString(item.get("id").asString())));
        return ids;
    }

    /** Espera as outras threads na largada, para os pedidos simultâneos saírem juntos. */
    protected static void await(CyclicBarrier barrier) {
        try {
            barrier.await();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Um veículo cadastrado pelo {@code POST}, com o corpo que o criou. */
    protected record Registered(UUID id, Map<String, Object> body) {

        String path() {
            return "/vehicles/" + id;
        }

        /** O corpo do {@code PUT}: o veículo como foi cadastrado, na versão 0, com o que vier em {@code changes}. */
        Map<String, Object> edit(Map<String, Object> changes) {
            Map<String, Object> edit = new HashMap<>(body);
            edit.put("version", 0);
            edit.putAll(changes);
            return edit;
        }
    }
}
