package eu.exeris.caps.invitations.internal.repository;

import eu.exeris.caps.invitations.model.InviteToken;
import eu.exeris.kernel.spi.persistence.PersistenceStatement;
import eu.exeris.kernel.spi.persistence.QueryResult;
import eu.exeris.kernel.spi.persistence.RowCursor;
import eu.exeris.kernel.spi.persistence.TransactionalExecutor;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * PostgreSQL implementation of {@link InvitationRepository} using {@link TransactionalExecutor}.
 *
 * @since 0.1.0
 */
public final class PostgresInvitationRepository implements InvitationRepository {

    private final TransactionalExecutor tx;

    public PostgresInvitationRepository(TransactionalExecutor tx) {
        this.tx = Objects.requireNonNull(tx, "tx must not be null");
    }

    @Override
    public void save(InviteToken inviteToken) {
        Objects.requireNonNull(inviteToken, "inviteToken must not be null");
        tx.executeManaged(conn -> {
            try (PersistenceStatement stmt = conn.prepare(
                    "INSERT INTO invitations (id, target_tenant_id, email, role, token_hash, expires_at, consumed_at) " +
                    "VALUES ($1, $2, $3, $4, $5, $6, $7)")) {
                stmt.bindUuid(0, inviteToken.id())
                    .bindUuid(1, inviteToken.targetTenantId())
                    .bindString(2, inviteToken.email())
                    .bindString(3, inviteToken.role())
                    .bindString(4, inviteToken.tokenHash())
                    .bindInstant(5, inviteToken.expiresAt());

                if (inviteToken.consumedAt() != null) {
                    stmt.bindInstant(6, inviteToken.consumedAt());
                } else {
                    stmt.bindNull(6);
                }

                stmt.executeUpdate();
            }
        });
    }

    @Override
    public Optional<InviteToken> findByTokenHash(String tokenHash) {
        if (tokenHash == null) {
            return Optional.empty();
        }
        return tx.query(conn -> {
            try (PersistenceStatement stmt = conn.prepare(
                    "SELECT id, target_tenant_id, email, role, token_hash, expires_at, consumed_at " +
                    "FROM invitations WHERE token_hash = $1")) {
                stmt.bindString(0, tokenHash);
                try (QueryResult rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapRow(rs.row()));
                    }
                    return Optional.empty();
                }
            }
        });
    }

    @Override
    public Optional<InviteToken> consumeByTokenHash(String tokenHash, Instant consumedAt) {
        if (tokenHash == null) {
            return Optional.empty();
        }
        AtomicReference<Optional<InviteToken>> result = new AtomicReference<>(Optional.empty());
        tx.executeManaged(conn -> {
            try (PersistenceStatement stmt = conn.prepare(
                    "UPDATE invitations SET consumed_at = $1 " +
                    "WHERE token_hash = $2 AND consumed_at IS NULL AND expires_at > $1")) {
                stmt.bindInstant(0, consumedAt)
                    .bindString(1, tokenHash);
                long updated = stmt.executeUpdate();
                if (updated > 0) {
                    try (PersistenceStatement q = conn.prepare(
                            "SELECT id, target_tenant_id, email, role, token_hash, expires_at, consumed_at " +
                            "FROM invitations WHERE token_hash = $1")) {
                        q.bindString(0, tokenHash);
                        try (QueryResult rs = q.executeQuery()) {
                            if (rs.next()) {
                                result.set(Optional.of(mapRow(rs.row())));
                            }
                        }
                    }
                }
            }
        });
        return result.get();
    }

    private static InviteToken mapRow(RowCursor row) {
        UUID id = row.getUuid(0);
        UUID tenantId = row.getUuid(1);
        String email = row.getString(2);
        String role = row.getString(3);
        String hash = row.getString(4);
        Instant expiresAt = row.getInstant(5);
        Instant consumedAt = row.isNull(6) ? null : row.getInstant(6);

        return new InviteToken(id, tenantId, email, role, hash, expiresAt, consumedAt);
    }
}
