package eu.exeris.caps.invitations.internal;

import eu.exeris.caps.invitations.api.InvitationService;
import eu.exeris.caps.invitations.api.exception.InvalidInvitationException;
import eu.exeris.caps.invitations.api.exception.InvitationAccessDeniedException;
import eu.exeris.caps.invitations.api.model.Invitation;
import eu.exeris.caps.invitations.api.model.InvitationPolicy;
import eu.exeris.caps.invitations.api.model.IssuedInvitation;
import eu.exeris.caps.invitations.internal.repository.InvitationRecord;
import eu.exeris.caps.invitations.internal.repository.InvitationRepository;
import eu.exeris.caps.invitations.spi.InvitationAdministrators;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Default implementation of {@link InvitationService}. */
public final class DefaultInvitationService implements InvitationService {

    static final int TOKEN_BYTES = 32;
    static final int MAX_PAGE = 100;
    private static final int MAX_EMAIL = 320;
    private static final int MAX_ROLE = 64;
    private static final int MAX_RAW_TOKEN = 128;
    private static final String NOT_CONSUMABLE = "Invitation token is invalid, expired, revoked or already consumed";

    private final InvitationRepository repository;
    private final InvitationAdministrators administrators;
    private final InvitationPolicy policy;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();

    public DefaultInvitationService(InvitationRepository repository, InvitationAdministrators administrators,
                                    InvitationPolicy policy, Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.administrators = Objects.requireNonNull(administrators, "administrators must not be null");
        this.policy = Objects.requireNonNull(policy, "policy must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public IssuedInvitation issue(UUID tenantId, UUID actorId, String email, String role, Duration ttl) {
        authorize(tenantId, actorId);
        Objects.requireNonNull(role, "role must not be null");
        Objects.requireNonNull(ttl, "ttl must not be null");
        if (ttl.isZero() || ttl.isNegative()) {
            throw new InvalidInvitationException("ttl must be positive");
        }
        if (ttl.compareTo(policy.maxTtl()) > 0) {
            throw new InvalidInvitationException("ttl exceeds the maximum of " + policy.maxTtl());
        }
        String normalizedEmail = normalizeEmail(email);
        String normalizedRole = role.trim();
        if (normalizedRole.isEmpty() || normalizedRole.length() > MAX_ROLE) {
            throw new InvalidInvitationException("role must be 1 to " + MAX_ROLE + " characters");
        }

        byte[] entropy = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(entropy);
        String rawToken = "inv_" + Base64.getUrlEncoder().withoutPadding().encodeToString(entropy);

        Instant now = clock.instant();
        InvitationRecord record = new InvitationRecord(UUID.randomUUID(), tenantId, normalizedEmail, normalizedRole,
                actorId, hash(rawToken), now, now.plus(ttl), null, null);
        repository.insert(record);
        return new IssuedInvitation(rawToken, record.toInvitation());
    }

    @Override
    public List<Invitation> list(UUID tenantId, UUID actorId, int limit, int offset) {
        authorize(tenantId, actorId);
        if (limit < 1 || limit > MAX_PAGE) {
            throw new InvalidInvitationException("limit must be between 1 and " + MAX_PAGE);
        }
        if (offset < 0) {
            throw new InvalidInvitationException("offset must not be negative");
        }
        return repository.list(tenantId, limit, offset).stream().map(InvitationRecord::toInvitation).toList();
    }

    @Override
    public boolean revoke(UUID tenantId, UUID actorId, UUID invitationId) {
        authorize(tenantId, actorId);
        Objects.requireNonNull(invitationId, "invitationId must not be null");
        return repository.revoke(tenantId, invitationId, clock.instant());
    }

    @Override
    public Optional<Invitation> findPending(String rawToken, UUID tenantId, String acceptorEmail) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        if (!plausible(rawToken) || acceptorEmail == null || acceptorEmail.isBlank()) {
            return Optional.empty();
        }
        return repository.findPending(hash(rawToken), tenantId, lower(acceptorEmail), clock.instant())
                .map(InvitationRecord::toInvitation);
    }

    @Override
    public Invitation consume(String rawToken, UUID tenantId, String acceptorEmail) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        if (!plausible(rawToken) || acceptorEmail == null || acceptorEmail.isBlank()) {
            throw new InvalidInvitationException(NOT_CONSUMABLE);
        }
        return repository.consume(hash(rawToken), tenantId, lower(acceptorEmail), clock.instant())
                .map(InvitationRecord::toInvitation)
                .orElseThrow(() -> new InvalidInvitationException(NOT_CONSUMABLE));
    }

    private void authorize(UUID tenantId, UUID actorId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        Objects.requireNonNull(actorId, "actorId must not be null");
        if (!administrators.mayAdminister(tenantId, actorId)) {
            throw new InvitationAccessDeniedException("Subject may not administer invitations of this tenant");
        }
    }

    private static boolean plausible(String rawToken) {
        return rawToken != null && !rawToken.isBlank() && rawToken.length() <= MAX_RAW_TOKEN;
    }

    private static String lower(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeEmail(String email) {
        Objects.requireNonNull(email, "email must not be null");
        String normalized = lower(email);
        int at = normalized.indexOf('@');
        if (normalized.length() > MAX_EMAIL || at < 1 || at != normalized.lastIndexOf('@')
                || at == normalized.length() - 1 || normalized.chars().anyMatch(Character::isWhitespace)) {
            throw new InvalidInvitationException("email is not a valid address");
        }
        return normalized;
    }

    /** SHA-256 hex of the token. The token is 256 bits of random, so no salt or slow hash is needed. */
    static String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
