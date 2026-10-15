package eu.exeris.caps.invitations.api.model;

import java.util.Objects;

/**
 * A newly issued invitation with its plaintext token. The token exists only here: the capability
 * stores its hash, so it cannot be shown again.
 *
 * @param rawToken   plaintext opaque token, at least 256 bits of entropy
 * @param invitation the stored invitation
 * @since 0.1.0
 */
public record IssuedInvitation(String rawToken, Invitation invitation) {

    public IssuedInvitation {
        Objects.requireNonNull(rawToken, "rawToken must not be null");
        Objects.requireNonNull(invitation, "invitation must not be null");
    }

    @Override
    public String toString() {
        return "IssuedInvitation[invitation=" + invitation + "]";
    }
}
