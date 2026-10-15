package eu.exeris.caps.invitations.spi;

import java.util.UUID;

/**
 * Decides whether a subject may issue, list and revoke the invitations of a tenant. The composition
 * implements it over its tenant membership and role grants; this capability owns neither.
 *
 * @since 0.1.0
 */
@FunctionalInterface
public interface InvitationAdministrators {

    /**
     * Tells whether the subject may issue, list and revoke the invitations of the tenant. A
     * {@code false} answer makes those operations throw {@code InvitationAccessDeniedException}.
     *
     * @param tenantId tenant whose invitations are administered
     * @param subjectId authenticated subject acting
     * @return {@code true} when the subject may administer the tenant's invitations
     */
    boolean mayAdminister(UUID tenantId, UUID subjectId);
}
