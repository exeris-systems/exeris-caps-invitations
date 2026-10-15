package eu.exeris.caps.invitations.internal.repository;

import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Test double: in-memory {@link InvitationRepository} with the same conditional semantics as the SQL. */
public final class InMemoryInvitationRepository implements InvitationRepository {

    private final Map<UUID, InvitationRecord> store = new ConcurrentHashMap<>();

    @Override
    public synchronized void insert(InvitationRecord record) {
        store.put(record.id(), record);
    }

    private static boolean pending(InvitationRecord r, Instant now) {
        return r.consumedAt() == null && r.revokedAt() == null && r.expiresAt().isAfter(now);
    }

    private static boolean sameText(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    private Optional<InvitationRecord> match(String hash, UUID tenantId, String email, Instant now) {
        return store.values().stream()
                .filter(r -> sameText(r.tokenHash(), hash) && r.tenantId().equals(tenantId)
                        && sameText(r.email(), email) && pending(r, now))
                .findFirst();
    }

    @Override
    public synchronized Optional<InvitationRecord> findPending(String hash, UUID tenantId, String email, Instant now) {
        return match(hash, tenantId, email, now);
    }

    @Override
    public synchronized Optional<InvitationRecord> consume(String hash, UUID tenantId, String email, Instant now) {
        return match(hash, tenantId, email, now).map(r -> {
            InvitationRecord consumed = new InvitationRecord(r.id(), r.tenantId(), r.email(), r.role(), r.issuedBy(),
                    r.tokenHash(), r.createdAt(), r.expiresAt(), now, r.revokedAt());
            store.put(r.id(), consumed);
            return consumed;
        });
    }

    @Override
    public synchronized boolean revoke(UUID tenantId, UUID id, Instant now) {
        InvitationRecord r = store.get(id);
        if (r == null || !r.tenantId().equals(tenantId) || !pending(r, now)) {
            return false;
        }
        store.put(id, new InvitationRecord(r.id(), r.tenantId(), r.email(), r.role(), r.issuedBy(), r.tokenHash(),
                r.createdAt(), r.expiresAt(), r.consumedAt(), now));
        return true;
    }

    @Override
    public synchronized List<InvitationRecord> list(UUID tenantId, int limit, int offset) {
        return store.values().stream().filter(r -> r.tenantId().equals(tenantId))
                .sorted(Comparator.comparing(InvitationRecord::createdAt).reversed().thenComparing(InvitationRecord::id))
                .skip(offset).limit(limit).toList();
    }
}
