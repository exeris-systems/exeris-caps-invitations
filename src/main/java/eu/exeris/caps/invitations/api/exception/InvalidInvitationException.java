package eu.exeris.caps.invitations.api.exception;

/**
 * Thrown when an invitation cannot be consumed or issued as asked: the token is unknown, expired,
 * revoked or already consumed, it belongs to another tenant or another address, or an argument is
 * out of range.
 *
 * <p>For a token that cannot be consumed the message is the same whatever the reason, so a caller
 * learns nothing about the invitation it does not hold.
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
