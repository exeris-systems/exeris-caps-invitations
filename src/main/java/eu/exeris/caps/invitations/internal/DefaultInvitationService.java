package eu.exeris.caps.invitations.internal;

import eu.exeris.caps.invitations.InvalidInvitationException;
import eu.exeris.caps.invitations.InvitationService;
import eu.exeris.caps.invitations.internal.repository.InvitationRepository;
import eu.exeris.caps.invitations.model.InviteToken;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Default implementation of {@link InvitationService}.
 *
 * @since 0.1.0
 */
public final class DefaultInvitationService implements InvitationService {

    private final InvitationRepository repository;
    private final SecureRandom secureRandom = new SecureRandom();

    public DefaultInvitationService(InvitationRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
    }

    @Override
    public CreatedInvitation createInvitation(UUID targetTenantId, String email, String role, Duration ttl) {
        Objects.requireNonNull(targetTenantId, "targetTenantId must not be null");
        Objects.requireNonNull(email, "email must not be null");
        Objects.requireNonNull(role, "role must not be null");
        Objects.requireNonNull(ttl, "ttl must not be null");

        byte[] entropy = new byte[32];
        secureRandom.nextBytes(entropy);
        String rawToken = "inv_" + Base64.getUrlEncoder().withoutPadding().encodeToString(entropy);

        String tokenHash = hashToken(rawToken);
        Instant now = Instant.now();
        Instant expiresAt = now.plus(ttl);
        UUID id = UUID.randomUUID();

        InviteToken record = new InviteToken(
                id,
                targetTenantId,
                email.trim().toLowerCase(),
                role.trim(),
                tokenHash,
                expiresAt,
                null
        );
        repository.save(record);

        return new CreatedInvitation(rawToken, record);
    }

    @Override
    public Optional<InviteToken> findValidInvitation(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        String tokenHash = hashToken(rawToken);
        return repository.findByTokenHash(tokenHash)
                .filter(record -> record.isValid(Instant.now()));
    }

    @Override
    public InviteToken acceptInvitation(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new InvalidInvitationException("Invitation token must not be null or blank");
        }
        String tokenHash = hashToken(rawToken);
        Instant now = Instant.now();
        return repository.consumeByTokenHash(tokenHash, now)
                .orElseThrow(() -> new InvalidInvitationException("Invitation token is invalid, expired, or already consumed"));
    }

    private static String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
