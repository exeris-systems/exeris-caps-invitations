package eu.exeris.caps.invitations.internal.repository;

import eu.exeris.caps.invitations.api.model.Invitation;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Stored form of an invitation: the public view plus the token hash. */
public record InvitationRecord(
        UUID id,
        UUID tenantId,
        String email,
        String role,
        UUID issuedBy,
        String tokenHash,
        Instant createdAt,
        Instant expiresAt,
        Instant consumedAt,
        Instant revokedAt
) {

    public InvitationRecord {
        Objects.requireNonNull(tokenHash, "tokenHash must not be null");
        // validates the remaining fields
        view(id, tenantId, email, role, issuedBy, createdAt, expiresAt, consumedAt, revokedAt);
    }

    public Invitation toInvitation() {
        return view(id, tenantId, email, role, issuedBy, createdAt, expiresAt, consumedAt, revokedAt);
    }

    private static Invitation view(UUID id, UUID tenantId, String email, String role, UUID issuedBy,
                                   Instant createdAt, Instant expiresAt, Instant consumedAt, Instant revokedAt) {
        return new Invitation(id, tenantId, email, role, issuedBy, createdAt, expiresAt, consumedAt, revokedAt);
    }
}
