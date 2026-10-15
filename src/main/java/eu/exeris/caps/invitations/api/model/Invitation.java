package eu.exeris.caps.invitations.api.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A tenant invitation as the service reports it. It never carries the token or its hash.
 *
 * @param id          invitation identifier
 * @param tenantId    tenant the invitee is invited into
 * @param email       invitee address, lower-cased
 * @param role        role granted on acceptance
 * @param issuedBy    subject that issued it
 * @param createdAt   issue time
 * @param expiresAt   time from which it can no longer be consumed
 * @param consumedAt  time it was consumed, or {@code null}
 * @param revokedAt   time it was revoked, or {@code null}
 * @since 0.1.0
 */
public record Invitation(
        UUID id,
        UUID tenantId,
        String email,
        String role,
        UUID issuedBy,
        Instant createdAt,
        Instant expiresAt,
        Instant consumedAt,
        Instant revokedAt
) {

    public Invitation {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(email, "email must not be null");
        Objects.requireNonNull(role, "role must not be null");
        Objects.requireNonNull(issuedBy, "issuedBy must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
    }

    public boolean isConsumed() {
        return consumedAt != null;
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isExpired(Instant now) {
        return !expiresAt.isAfter(now);
    }

    public boolean isPending(Instant now) {
        return !isConsumed() && !isRevoked() && !isExpired(now);
    }
}
