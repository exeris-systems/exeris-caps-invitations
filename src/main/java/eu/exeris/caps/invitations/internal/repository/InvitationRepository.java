package eu.exeris.caps.invitations.internal.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Storage contract for invitations. Every operation is scoped to a tenant, and the two that change a
 * pending invitation do so in one conditional write.
 */
public interface InvitationRepository {

    void insert(InvitationRecord record);

    /** Pending invitation matching hash, tenant and address at {@code now}, without consuming it. */
    Optional<InvitationRecord> findPending(String tokenHash, UUID tenantId, String email, Instant now);

    /**
     * Consumes the invitation if, and only if, hash, tenant and address all match and it is neither
     * consumed, revoked nor expired at {@code now}. A mismatch leaves the invitation untouched.
     */
    Optional<InvitationRecord> consume(String tokenHash, UUID tenantId, String email, Instant now);

    /** Revokes a pending invitation of the tenant; {@code false} when none matched. */
    boolean revoke(UUID tenantId, UUID invitationId, Instant now);

    /** Invitations of the tenant, newest first. */
    List<InvitationRecord> list(UUID tenantId, int limit, int offset);
}
