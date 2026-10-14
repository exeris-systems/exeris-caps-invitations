package eu.exeris.caps.invitations.api.model;

import java.time.Duration;
import java.util.Objects;

/**
 * Bounds on the lifetime of an invitation.
 *
 * @param maxTtl longest lifetime {@code issue} accepts; positive
 * @since 0.1.0
 */
public record InvitationPolicy(Duration maxTtl) {

    /** Seven days is the default ceiling: long enough for a human to act, short enough to be safe. */
    public static final InvitationPolicy DEFAULT = new InvitationPolicy(Duration.ofDays(7));

    public InvitationPolicy {
        Objects.requireNonNull(maxTtl, "maxTtl must not be null");
        if (maxTtl.isZero() || maxTtl.isNegative()) {
            throw new IllegalArgumentException("maxTtl must be positive");
        }
    }
}
