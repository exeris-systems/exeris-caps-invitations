package eu.exeris.caps.invitations.internal.repository;

import eu.exeris.caps.invitations.model.InviteToken;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe in-memory implementation of {@link InvitationRepository}.
 *
 * @since 0.1.0
 */
public final class InMemoryInvitationRepository implements InvitationRepository {

    private final Map<String, InviteToken> store = new ConcurrentHashMap<>();

    @Override
    public synchronized void save(InviteToken inviteToken) {
        Objects.requireNonNull(inviteToken, "inviteToken must not be null");
        store.put(inviteToken.tokenHash(), inviteToken);
    }

    @Override
    public Optional<InviteToken> findByTokenHash(String tokenHash) {
        if (tokenHash == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(store.get(tokenHash));
    }

    @Override
    public synchronized Optional<InviteToken> consumeByTokenHash(String tokenHash, Instant consumedAt) {
        if (tokenHash == null) {
            return Optional.empty();
        }
        InviteToken existing = store.get(tokenHash);
        if (existing == null || existing.isConsumed() || existing.isExpired(consumedAt)) {
            return Optional.empty();
        }
        InviteToken consumed = new InviteToken(
                existing.id(),
                existing.targetTenantId(),
                existing.email(),
                existing.role(),
                existing.tokenHash(),
                existing.expiresAt(),
                consumedAt
        );
        store.put(tokenHash, consumed);
        return Optional.of(consumed);
    }
}
