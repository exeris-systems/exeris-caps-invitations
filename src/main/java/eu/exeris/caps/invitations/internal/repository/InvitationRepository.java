package eu.exeris.caps.invitations.internal.repository;

import eu.exeris.caps.invitations.model.InviteToken;

import java.time.Instant;
import java.util.Optional;

/**
 * Storage repository contract for invitations.
 *
 * @since 0.1.0
 */
public interface InvitationRepository {

    /**
     * Persists a new invitation record.
     *
     * @param inviteToken invitation to save; non-null
     */
    void save(InviteToken inviteToken);

    /**
     * Finds an invitation record by its SHA-256 token hash.
     *
     * @param tokenHash 256-bit hash hex string
     * @return optional containing the record, or empty
     */
    Optional<InviteToken> findByTokenHash(String tokenHash);

    /**
     * Atomically consumes an invitation by its token hash if not already consumed and not expired.
     *
     * @param tokenHash  256-bit hash hex string
     * @param consumedAt timestamp of consumption
     * @return optional containing the consumed record, or empty if already consumed/expired/not found
     */
    Optional<InviteToken> consumeByTokenHash(String tokenHash, Instant consumedAt);
}
