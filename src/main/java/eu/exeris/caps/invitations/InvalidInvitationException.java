package eu.exeris.caps.invitations;

/**
 * Thrown when an invitation token is invalid, expired, or already consumed.
 *
 * @since 0.1.0
 */
public class InvalidInvitationException extends RuntimeException {

    public InvalidInvitationException(String message) {
        super(message);
    }

    public InvalidInvitationException(String message, Throwable cause) {
        super(message, cause);
    }
}
