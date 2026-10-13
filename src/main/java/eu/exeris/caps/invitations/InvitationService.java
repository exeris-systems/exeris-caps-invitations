package eu.exeris.caps.invitations;

import eu.exeris.caps.invitations.model.InviteToken;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Layer 5 Domain Primitive contract for tenant member invitations.
 *
 * @since 0.1.0
 */
public interface InvitationService {

    /**
     * Value object wrapping a newly created invitation with its unhashed plaintext token.
     *
     * @param rawToken    plaintext opaque invitation token
     * @param inviteToken persisted entity record
     */
    record CreatedInvitation(String rawToken, InviteToken inviteToken) {}

    /**
     * Issues a new tenant invitation.
     *
     * @param targetTenantId target tenant identifier; non-null
     * @param email          invited user email; non-null
     * @param role           assigned role; non-null
     * @param ttl            validity duration; non-null
     * @return created invitation with raw token
     */
    CreatedInvitation createInvitation(UUID targetTenantId, String email, String role, Duration ttl);

    /**
     * Finds and validates a pending invitation by its raw plaintext token.
     *
     * @param rawToken plaintext invitation token
     * @return optional containing the valid invitation, or empty if expired/consumed/not found
     */
    Optional<InviteToken> findValidInvitation(String rawToken);

    /**
     * Atomically accepts and consumes an invitation by its raw plaintext token.
     *
     * @param rawToken plaintext invitation token
     * @return consumed invitation record
     * @throws InvalidInvitationException if token is missing, expired, or already consumed
     */
    InviteToken acceptInvitation(String rawToken);
}
