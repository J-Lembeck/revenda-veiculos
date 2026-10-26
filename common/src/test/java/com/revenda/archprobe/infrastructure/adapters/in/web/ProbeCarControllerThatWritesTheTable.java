package com.revenda.archprobe.infrastructure.adapters.in.web;

import com.revenda.archprobe.infrastructure.adapters.out.persistence.JdbcProbeCarAdapter;
import org.springframework.jdbc.core.simple.JdbcClient;

public record ProbeCarControllerThatWritesTheTable(JdbcProbeCarAdapter cars, JdbcClient jdbc) {
}
