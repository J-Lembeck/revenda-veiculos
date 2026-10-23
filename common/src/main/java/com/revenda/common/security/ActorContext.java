package com.revenda.common.security;

public record ActorContext(ActorType type, String id, Role role, String requestId) {

    public static ActorContext system() {
        return new ActorContext(ActorType.SYSTEM, null, null, null);
    }

    public static ActorContext anonymous(String requestId) {
        return new ActorContext(ActorType.ANONYMOUS, null, null, requestId);
    }
}
