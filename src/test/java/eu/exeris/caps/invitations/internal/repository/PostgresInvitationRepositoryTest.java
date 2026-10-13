package eu.exeris.caps.invitations.internal.repository;

import eu.exeris.caps.invitations.model.InviteToken;
import eu.exeris.kernel.spi.persistence.PersistenceConnection;
import eu.exeris.kernel.spi.persistence.PersistenceStatement;
import eu.exeris.kernel.spi.persistence.QueryResult;
import eu.exeris.kernel.spi.persistence.RowCursor;
import eu.exeris.kernel.spi.persistence.TransactionIsolation;
import eu.exeris.kernel.spi.persistence.TransactionalExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PostgresInvitationRepository Unit Tests")
class PostgresInvitationRepositoryTest {

    private CapturingTransactionalExecutor tx;
    private PostgresInvitationRepository repository;

    @BeforeEach
    void setUp() {
        tx = new CapturingTransactionalExecutor();
        repository = new PostgresInvitationRepository(tx);
    }

    @Test
    @DisplayName("save executes managed INSERT with correct bindings")
    void saveExecutesInsert() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Instant exp = Instant.now().plusSeconds(3600);
        InviteToken token = new InviteToken(id, tenantId, "user@exeris.eu", "ADMIN", "hash123", exp, null);

        repository.save(token);

        assertThat(tx.executedStatements).hasSize(1);
        CapturingStatement stmt = tx.executedStatements.get(0);
        assertThat(stmt.sql).contains("INSERT INTO invitations");
        assertThat(stmt.boundParameters.get(0)).isEqualTo(id);
        assertThat(stmt.boundParameters.get(1)).isEqualTo(tenantId);
        assertThat(stmt.boundParameters.get(2)).isEqualTo("user@exeris.eu");
        assertThat(stmt.boundParameters.get(3)).isEqualTo("ADMIN");
        assertThat(stmt.boundParameters.get(4)).isEqualTo("hash123");
        assertThat(stmt.boundParameters.get(5)).isEqualTo(exp);
        assertThat(stmt.boundParameters.get(6)).isNull();
    }

    @Test
    @DisplayName("findByTokenHash returns mapped record")
    void findByTokenHashSuccess() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Instant exp = Instant.now().plusSeconds(3600);
        Object[] row = new Object[]{id, tenantId, "user@exeris.eu", "ADMIN", "hash123", exp, null};

        tx.queryResultSupplier = () -> new StubQueryResult(java.util.Collections.singletonList(row));

        Optional<InviteToken> found = repository.findByTokenHash("hash123");
        assertThat(found).isPresent();
        assertThat(found.get().id()).isEqualTo(id);
        assertThat(found.get().email()).isEqualTo("user@exeris.eu");
    }

    @Test
    @DisplayName("consumeByTokenHash executes UPDATE and returns consumed token")
    void consumeByTokenHashSuccess() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(3600);
        Object[] row = new Object[]{id, tenantId, "user@exeris.eu", "ADMIN", "hash123", exp, now};

        tx.rowsAffectedResult = 1;
        tx.queryResultSupplier = () -> new StubQueryResult(java.util.Collections.singletonList(row));

        Optional<InviteToken> consumed = repository.consumeByTokenHash("hash123", now);
        assertThat(consumed).isPresent();
        assertThat(consumed.get().isConsumed()).isTrue();
    }

    private static final class CapturingTransactionalExecutor implements TransactionalExecutor {
        final List<CapturingStatement> executedStatements = new ArrayList<>();
        java.util.function.Supplier<QueryResult> queryResultSupplier;
        long rowsAffectedResult = 1;

        @Override public void execute(TransactionalWork work) { work.run(new CapturingConnection(this)); }
        @Override public void executeManaged(TransactionalWork work) { work.run(new CapturingConnection(this)); }
        @Override public void executeManaged(TransactionIsolation isolation, boolean readOnly, TransactionalWork work) { work.run(new CapturingConnection(this)); }

        @Override
        public <T> T query(Function<PersistenceConnection, T> query) {
            return query.apply(new CapturingConnection(this));
        }
    }

    private static final class CapturingConnection implements PersistenceConnection {
        private final CapturingTransactionalExecutor executor;

        CapturingConnection(CapturingTransactionalExecutor executor) {
            this.executor = executor;
        }

        @Override
        public PersistenceStatement prepare(String sql) {
            CapturingStatement stmt = new CapturingStatement(sql, executor);
            executor.executedStatements.add(stmt);
            return stmt;
        }

        @Override public QueryResult executeQuery(String sql) { return null; }
        @Override public long executeUpdate(String sql) { return 0; }
        @Override public void beginTransaction() {}
        @Override public void beginTransaction(TransactionIsolation isolation, boolean readOnly) {}
        @Override public void commit() {}
        @Override public void rollback() {}
        @Override public boolean inTransaction() { return true; }
        @Override public boolean isOpen() { return true; }
        @Override public void close() {}
    }

    private static final class CapturingStatement implements PersistenceStatement {
        final String sql;
        final CapturingTransactionalExecutor executor;
        final Map<Integer, Object> boundParameters = new HashMap<>();

        CapturingStatement(String sql, CapturingTransactionalExecutor executor) {
            this.sql = sql;
            this.executor = executor;
        }

        @Override public PersistenceStatement bindInt(int index, int value) { boundParameters.put(index, value); return this; }
        @Override public PersistenceStatement bindLong(int index, long value) { boundParameters.put(index, value); return this; }
        @Override public PersistenceStatement bindShort(int index, short value) { boundParameters.put(index, value); return this; }
        @Override public PersistenceStatement bindFloat(int index, float value) { boundParameters.put(index, value); return this; }
        @Override public PersistenceStatement bindDouble(int index, double value) { boundParameters.put(index, value); return this; }
        @Override public PersistenceStatement bindBoolean(int index, boolean value) { boundParameters.put(index, value); return this; }
        @Override public PersistenceStatement bindString(int index, String value) { boundParameters.put(index, value); return this; }
        @Override public PersistenceStatement bindUuid(int index, UUID value) { boundParameters.put(index, value); return this; }
        @Override public PersistenceStatement bindBytes(int index, byte[] value) { boundParameters.put(index, value); return this; }
        @Override public PersistenceStatement bindInstant(int index, Instant value) { boundParameters.put(index, value); return this; }
        @Override public PersistenceStatement bindNull(int index) { boundParameters.put(index, null); return this; }

        @Override
        public QueryResult executeQuery() {
            if (executor.queryResultSupplier != null) {
                return executor.queryResultSupplier.get();
            }
            return new StubQueryResult(List.of());
        }

        @Override
        public long executeUpdate() {
            return executor.rowsAffectedResult;
        }

        @Override
        public void close() {}
    }

    private static final class StubQueryResult implements QueryResult {
        private final List<Object[]> rows;
        private int cursor = -1;

        StubQueryResult(List<Object[]> rows) {
            this.rows = rows;
        }

        @Override
        public boolean next() {
            cursor++;
            return cursor < rows.size();
        }

        @Override
        public RowCursor row() {
            return new StubRowCursor(rows.get(cursor));
        }

        @Override public long rowsAffected() { return rows.size(); }
        @Override public int columnCount() { return rows.isEmpty() ? 0 : rows.get(0).length; }
        @Override public String commandTag() { return "SELECT"; }
        @Override public void close() {}
    }

    private static final class StubRowCursor implements RowCursor {
        private final Object[] values;

        StubRowCursor(Object[] values) {
            this.values = values;
        }

        @Override public int getInt(int i) { return (Integer) values[i]; }
        @Override public long getLong(int i) { return (Long) values[i]; }
        @Override public short getShort(int i) { return (Short) values[i]; }
        @Override public float getFloat(int i) { return (Float) values[i]; }
        @Override public double getDouble(int i) { return (Double) values[i]; }
        @Override public boolean getBoolean(int i) { return (Boolean) values[i]; }
        @Override public boolean isNull(int i) { return values[i] == null; }
        @Override public java.lang.foreign.MemorySegment getSegment(int i) { return null; }
        @Override public int getLength(int i) { return 0; }
        @Override public String getString(int i) { return (String) values[i]; }
        @Override public byte[] getBytes(int i) { return (byte[]) values[i]; }
        @Override public UUID getUuid(int i) { return (UUID) values[i]; }
        @Override public Instant getInstant(int i) { return (Instant) values[i]; }
        @Override public int columnCount() { return values.length; }
        @Override public boolean isValid() { return true; }
    }
}
