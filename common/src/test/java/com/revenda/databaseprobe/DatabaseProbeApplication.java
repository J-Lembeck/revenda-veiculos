package com.revenda.databaseprobe;

import com.revenda.common.audit.AuditAction;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
class DatabaseProbeApplication {

    enum ProbeAuditAction implements AuditAction {
        VEHICLE_CREATED
    }
}
