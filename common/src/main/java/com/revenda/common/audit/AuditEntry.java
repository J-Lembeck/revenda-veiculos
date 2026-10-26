package com.revenda.common.audit;

import com.revenda.common.privacy.PiiMasker;
import java.util.Objects;
import java.util.UUID;

/**
 * Uma linha de auditoria. Os retratos {@code before}, {@code after} e {@code extra} são records explícitos,
 * só com o que não é dado pessoal; o resumo e o motivo passam pelo {@link PiiMasker}.
 */
public record AuditEntry(
        AuditAction action,
        String entityType,
        UUID entityId,
        String summary,
        String reason,
        Record before,
        Record after,
        Record extra) {

    public static final int ACTION_LIMIT = 60;
    public static final int ENTITY_TYPE_LIMIT = 40;
    public static final int TEXT_LIMIT = 300;

    public AuditEntry {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(entityType, "entityType");
        Objects.requireNonNull(summary, "summary");
        require(action.name().length() <= ACTION_LIMIT, "ação com mais de " + ACTION_LIMIT + " caracteres");
        require(!entityType.isBlank() && entityType.length() <= ENTITY_TYPE_LIMIT,
                "tipo de entidade vazio ou com mais de " + ENTITY_TYPE_LIMIT + " caracteres");
        summary = PiiMasker.mask(summary.strip());
        require(!summary.isEmpty() && summary.length() <= TEXT_LIMIT,
                "resumo vazio ou com mais de " + TEXT_LIMIT + " caracteres");
        if (reason != null) {
            reason = PiiMasker.mask(reason.strip());
            require(!reason.isEmpty() && reason.length() <= TEXT_LIMIT,
                    "motivo vazio ou com mais de " + TEXT_LIMIT + " caracteres");
        }
    }

    public static AuditEntry of(AuditAction action, String entityType, UUID entityId, String summary) {
        return new AuditEntry(action, entityType, entityId, summary, null, null, null, null);
    }

    public AuditEntry withReason(String why) {
        return new AuditEntry(action, entityType, entityId, summary, why, before, after, extra);
    }

    public AuditEntry withChange(Record previous, Record current) {
        return new AuditEntry(action, entityType, entityId, summary, reason, previous, current, extra);
    }

    public AuditEntry withExtra(Record details) {
        return new AuditEntry(action, entityType, entityId, summary, reason, before, after, details);
    }

    private static void require(boolean condition, String problem) {
        if (!condition) {
            throw new IllegalArgumentException("Entrada de auditoria inválida: " + problem);
        }
    }
}
