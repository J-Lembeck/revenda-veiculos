package com.revenda.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ActorContextHolderTests {

    @BeforeEach
    @AfterEach
    void clear() {
        ActorContextHolder.clear();
    }

    @Test
    void outsideARequestTheActorIsTheSystem() {
        assertThat(ActorContextHolder.get()).isEqualTo(ActorContext.system());
    }

    @Test
    void nestedWorkGivesTheOuterActorBack() {
        ActorContext outer = new ActorContext(ActorType.USER, "cliente-1", Role.CUSTOMER, "requisicao-1");
        ActorContextHolder.set(outer);

        ActorType inner = ActorContextHolder.callAs(ActorContext.system(), () -> ActorContextHolder.get().type());

        assertThat(inner).isEqualTo(ActorType.SYSTEM);
        assertThat(ActorContextHolder.get()).isSameAs(outer);
    }

    @Test
    void anonymousActorTurnsIntoTheSystemKeepingOnlyTheRequestId() {
        assertThat(ActorContext.anonymous("requisicao-3").asSystem())
                .isEqualTo(new ActorContext(ActorType.SYSTEM, null, null, "requisicao-3"));
    }

    @Test
    void withoutAnOuterActorNothingIsLeftBehindEvenOnFailure() {
        ActorContext service = new ActorContext(ActorType.SERVICE, "sales-service", null, "requisicao-2");

        assertThatThrownBy(() -> ActorContextHolder.callAs(service, () -> {
            throw new IllegalStateException("falhou");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(ActorContextHolder.get()).isEqualTo(ActorContext.system());
    }
}
