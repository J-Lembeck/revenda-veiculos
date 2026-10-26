package com.revenda.archprobe.infrastructure.adapters.in.job;

import java.util.logging.Logger;

public class ConsoleProbeJob {

    private static final Logger LOG = Logger.getLogger(ConsoleProbeJob.class.getName());

    public void run() {
        System.out.println("rodada da varredura");
        LOG.info("rodada da varredura");
    }
}
