package com.revenda.archprobe.infrastructure.adapters.in.web;

import org.springframework.security.access.prepost.PreAuthorize;

public class ProbeCarControllerWithPreAuthorize {

    @PreAuthorize("hasRole('SELLER')")
    public void register() {
    }
}
