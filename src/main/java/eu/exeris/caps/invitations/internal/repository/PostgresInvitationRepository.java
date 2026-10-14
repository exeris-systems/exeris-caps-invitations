package eu.exeris.caps.invitations.internal.repository;

import eu.exeris.kernel.spi.persistence.PersistenceStatement;
import eu.exeris.kernel.spi.persistence.QueryResult;
import eu.exeris.kernel.spi.persistence.RowCursor;
import eu.exeris.kernel.spi.persistence.TransactionalExecutor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * PostgreSQL implementation over {@link TransactionalExecutor}. Every write goes through
 * {@code executeManaged}; {@code query} is read-only and serves only the reads. Consuming and revoking
 * are single conditional {@code UPDATE} statements, so concurrent attempts cannot both succeed.
 */
public final class PostgresInvitationRepository implements InvitationRepository {

    private static final String COLUMNS =
            "id, tenant_id, email, role, issued_by, token_hash, created_at, expires_at, consumed_at, revoked_at";

    private final TransactionalExecutor tx;

    public PostgresInvitationRepository(TransactionalExecutor tx) {
        this.tx = Objects.requireNonNull(tx, "tx must not be null");
    }

    @Override
    public void insert(InvitationRecord record) {
        Objects.requireNonNull(record, "record must not be null");
        tx.executeManaged(conn -> {
            try (PersistenceStatement stmt = conn.prepare(
                    "INSERT INTO invitations (id, tenant_id, email, role, issued_by, token_hash, created_at, expires_at) "
                            + "VALUES ($1, $2, $3, $4, $5, $6, $7, $8)")) {
                stmt.bindUuid(0, record.id())
                        .bindUuid(1, record.tenantId())
                        .bindString(2, record.email())
                        .bindString(3, record.role())
                        .bindUuid(4, record.issuedBy())
                        .bindString(5, record.tokenHash())
                        .bindInstant(6, record.createdAt())
                        .bindInstant(7, record.expiresAt());
                stmt.executeUpdate();
            }
        });
    }

    @Override
    public Optional<InvitationRecord> findPending(String tokenHash, UUID tenantId, String email, Instant now) {
        return tx.query(conn -> {
            try (PersistenceStatement stmt = conn.prepare(
                    "SELECT " + COLUMNS + " FROM invitations WHERE token_hash = $1 AND tenant_id = $2 AND email = $3 "
                            + "AND consumed_at IS NULL AND revoked_at IS NULL AND expires_at > $4")) {
                stmt.bindString(0, tokenHash).bindUuid(1, tenantId).bindString(2, email).bindInstant(3, now);
                try (QueryResult rs = stmt.executeQuery()) {
                    return rs.next() ? Optional.of(map(rs.row())) : Optional.<InvitationRecord>empty();
                }
            }
        });
    }

    @Override
    public Optional<InvitationRecord> consume(String tokenHash, UUID tenantId, String email, Instant now) {
        // the executor may retry the work, so the outcome is reset on every attempt
        AtomicReference<Optional<InvitationRecord>> outcome = new AtomicReference<>(Optional.empty());
        tx.executeManaged(conn -> {
            outcome.set(Optional.empty());
            try (PersistenceStatement stmt = conn.prepare(
                    "UPDATE invitations SET consumed_at = $1 WHERE token_hash = $2 AND tenant_id = $3 AND email = $4 "
                            + "AND consumed_at IS NULL AND revoked_at IS NULL AND expires_at > $5 RETURNING " + COLUMNS)) {
                stmt.bindInstant(0, now).bindString(1, tokenHash).bindUuid(2, tenantId).bindString(3, email)
                        .bindInstant(4, now);
                try (QueryResult rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        outcome.set(Optional.of(map(rs.row())));
                    }
                }
            }
        });
        return outcome.get();
    }

    @Override
    public boolean revoke(UUID tenantId, UUID invitationId, Instant now) {
        AtomicBoolean revoked = new AtomicBoolean();
        tx.executeManaged(conn -> {
            try (PersistenceStatement stmt = conn.prepare(
                    "UPDATE invitations SET revoked_at = $1 WHERE id = $2 AND tenant_id = $3 "
                            + "AND consumed_at IS NULL AND revoked_at IS NULL AND expires_at > $4")) {
                stmt.bindInstant(0, now).bindUuid(1, invitationId).bindUuid(2, tenantId).bindInstant(3, now);
                revoked.set(stmt.executeUpdate() > 0);
            }
        });
        return revoked.get();
    }

    @Override
    public List<InvitationRecord> list(UUID tenantId, int limit, int offset) {
        return tx.query(conn -> {
            try (PersistenceStatement stmt = conn.prepare(
                    "SELECT " + COLUMNS + " FROM invitations WHERE tenant_id = $1 "
                            + "ORDER BY created_at DESC, id LIMIT $2 OFFSET $3")) {
                stmt.bindUuid(0, tenantId).bindInt(1, limit).bindInt(2, offset);
                List<InvitationRecord> rows = new ArrayList<>();
                try (QueryResult rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        rows.add(map(rs.row()));
                    }
                }
                return rows;
            }
        });
    }

    private static InvitationRecord map(RowCursor row) {
        return new InvitationRecord(
                row.getUuid(0), row.getUuid(1), row.getString(2), row.getString(3), row.getUuid(4),
                row.getString(5).trim(), row.getInstant(6), row.getInstant(7),
                row.isNull(8) ? null : row.getInstant(8), row.isNull(9) ? null : row.getInstant(9));
    }
}
