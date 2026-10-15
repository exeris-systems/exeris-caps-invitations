package eu.exeris.caps.invitations.api.exception;

/**
 * Thrown when the acting subject may not administer invitations of the tenant, as decided by the
 * composition's {@code InvitationAdministrators}.
 *
 * @since 0.1.0
 */
public class InvitationAccessDeniedException extends RuntimeException {

    public InvitationAccessDeniedException(String message) {
        super(message);
    }
}
