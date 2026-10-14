package eu.exeris.caps.invitations.api;

import eu.exeris.caps.invitations.api.exception.InvalidInvitationException;
import eu.exeris.caps.invitations.api.exception.InvitationAccessDeniedException;
import eu.exeris.caps.invitations.api.model.Invitation;
import eu.exeris.caps.invitations.api.model.IssuedInvitation;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Layer 5 Domain Primitive contract for tenant member invitations (ADR-099): issue, list, revoke and
 * consume single-use hashed invitation tokens bound to a tenant, a role and an invitee address.
 *
 * <p>Issuing, listing and revoking are administrative: the acting subject is checked against the
 * composition's {@code InvitationAdministrators}. Consuming is not, because the acceptor is not an
 * administrator; it is bound instead to the tenant and the verified address the invitation names.
 * Every operation is scoped to one tenant, and the caller's persistence session must carry the same
 * tenant for row-level security (ADR-012).
 *
 * @since 0.1.0
 */
public interface InvitationService {

    /**
     * Issues an invitation.
     *
     * @param tenantId tenant to invite into
     * @param actorId  authenticated subject issuing it
     * @param email    invitee address
     * @param role     role granted on acceptance
     * @param ttl      lifetime; positive and not above the configured maximum
     * @return the stored invitation with its plaintext token, shown once
     * @throws InvitationAccessDeniedException if the actor may not administer the tenant
     * @throws InvalidInvitationException      if an argument is out of range
     * @throws NullPointerException            if an argument is null
     */
    IssuedInvitation issue(UUID tenantId, UUID actorId, String email, String role, Duration ttl);

    /**
     * Lists the tenant's invitations, newest first.
     *
     * @param tenantId tenant to list
     * @param actorId  authenticated subject listing
     * @param limit    page size, 1 to 100
     * @param offset   rows to skip, not negative
     * @return the page of invitations, empty when there are none
     * @throws InvitationAccessDeniedException if the actor may not administer the tenant
     * @throws InvalidInvitationException      if {@code limit} is outside 1 to 100 or {@code offset} is
     *                                         negative
     * @throws NullPointerException            if {@code tenantId} or {@code actorId} is null
     */
    List<Invitation> list(UUID tenantId, UUID actorId, int limit, int offset);

    /**
     * Revokes a pending invitation of the tenant.
     *
     * @param tenantId     tenant the invitation belongs to
     * @param actorId      authenticated subject revoking
     * @param invitationId invitation to revoke
     * @return {@code true} when a pending invitation was revoked; {@code false} when there is none
     *         (unknown, other tenant, consumed, revoked, expired)
     * @throws InvitationAccessDeniedException if the actor may not administer the tenant
     * @throws NullPointerException            if {@code tenantId}, {@code actorId} or
     *                                         {@code invitationId} is null
     */
    boolean revoke(UUID tenantId, UUID actorId, UUID invitationId);

    /**
     * Finds the pending invitation the token opens for this tenant and address, without consuming it.
     *
     * @param rawToken      plaintext token; a null, blank or over-long (more than 128 characters) token
     *                      opens nothing
     * @param tenantId      tenant the acceptor is joining
     * @param acceptorEmail the acceptor's verified address
     * @return the pending invitation, or empty when none matches the token, tenant and address
     * @throws NullPointerException if {@code tenantId} is null
     */
    Optional<Invitation> findPending(String rawToken, UUID tenantId, String acceptorEmail);

    /**
     * Consumes an invitation. The token, tenant and address are checked in one conditional write, so a
     * caller that holds the token but is not the invitee fails without consuming it.
     *
     * @param rawToken      plaintext token
     * @param tenantId      tenant the acceptor is joining
     * @param acceptorEmail the acceptor's verified address
     * @return the consumed invitation
     * @throws InvalidInvitationException if no pending invitation matches all three
     * @throws NullPointerException       if {@code tenantId} is null
     */
    Invitation consume(String rawToken, UUID tenantId, String acceptorEmail);
}
