package com.revenda.common.security;

import java.util.Collection;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

@Getter
public class ActorAuthentication extends JwtAuthenticationToken {

    private final ActorType actorType;
    private final Role role;

    ActorAuthentication(Jwt jwt, Collection<? extends GrantedAuthority> authorities, String name,
            ActorType actorType, Role role) {
        super(jwt, authorities, name);
        this.actorType = actorType;
        this.role = role;
    }
}
