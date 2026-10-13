package eu.exeris.caps.invitations.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Entity record representing a tenant invitation and its single-use token.
 *
 * @param id             unique invitation identifier
 * @param targetTenantId tenant organization identifier
 * @param email          invited user email address
 * @param role           assigned role within tenant (e.g. "TENANT_MEMBER", "TENANT_ADMIN")
 * @param tokenHash      SHA-256 hash of the plaintext invite token
 * @param expiresAt      timestamp after which the invitation can no longer be accepted
 * @param consumedAt     timestamp when invitation was accepted, or null if pending
 * @since 0.1.0
 */
public record InviteToken(
        UUID id,
        UUID targetTenantId,
        String email,
        String role,
        String tokenHash,
        Instant expiresAt,
        Instant consumedAt
) {

    public InviteToken {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(targetTenantId, "targetTenantId must not be null");
        Objects.requireNonNull(email, "email must not be null");
        Objects.requireNonNull(role, "role must not be null");
        Objects.requireNonNull(tokenHash, "tokenHash must not be null");
        Objects.requireNonNull(expiresAt, "expiresAt must not be null");
    }

    public boolean isConsumed() {
        return consumedAt != null;
    }

    public boolean isExpired(Instant now) {
        return !expiresAt.isAfter(now);
    }

    public boolean isValid(Instant now) {
        return !isConsumed() && !isExpired(now);
    }
}
