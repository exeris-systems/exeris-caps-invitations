package eu.exeris.caps.invitations.internal.repository;

import eu.exeris.caps.invitations.InvitationsModule;
import eu.exeris.caps.invitations.api.exception.InvalidInvitationException;
import eu.exeris.caps.invitations.api.model.IssuedInvitation;
import eu.exeris.caps.invitations.api.model.InvitationPolicy;
import eu.exeris.caps.invitations.internal.DefaultInvitationService;
import eu.exeris.kernel.community.persistence.jdbc.JdbcPersistenceConnection;
import eu.exeris.kernel.spi.persistence.PersistenceConnection;
import eu.exeris.kernel.spi.persistence.TransactionIsolation;
import eu.exeris.kernel.spi.persistence.TransactionalExecutor;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs the repository and the service against a real PostgreSQL started by Testcontainers, with the schema
 * created from the capability's own migration. It needs a Docker daemon and fails when there is none: the
 * conditional {@code UPDATE ... RETURNING} and the row-level-security policy are verified only here.
 *
 * <p>The repository connects as a non-superuser, because a superuser bypasses row-level security.
 */
@Testcontainers(disabledWithoutDocker = false)
@DisplayName("PostgresInvitationRepository on a live PostgreSQL")
class PostgresInvitationRepositoryLiveIT {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private static Connection appConn;
    private static PostgresInvitationRepository repository;
    private static DefaultInvitationService service;

    private final UUID tenant = UUID.randomUUID();
    private final UUID admin = UUID.randomUUID();

    @BeforeAll
    static void setUpDatabase() throws Exception {
        try (Connection owner = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement stmt = owner.createStatement()) {
            for (String migration : InvitationsModule.MIGRATIONS) {
                try (InputStream is = PostgresInvitationRepositoryLiveIT.class.getClassLoader().getResourceAsStream(migration)) {
                    assertThat(is).as(migration).isNotNull();
                    String ddl = new String(is.readAllBytes(), StandardCharsets.UTF_8).replaceAll("(?m)^--.*$", "");
                    for (String command : ddl.split(";")) {
                        if (!command.isBlank()) {
                            stmt.execute(command.trim());
                        }
                    }
                }
            }
            stmt.execute("CREATE ROLE invitations_app LOGIN PASSWORD 'app' NOSUPERUSER NOBYPASSRLS");
            stmt.execute("GRANT SELECT, INSERT, UPDATE ON invitations TO invitations_app");
        }
        appConn = DriverManager.getConnection(POSTGRES.getJdbcUrl(), "invitations_app", "app");
        repository = new PostgresInvitationRepository(executor());
        service = new DefaultInvitationService(repository, (t, s) -> true, InvitationPolicy.DEFAULT, Clock.systemUTC());
    }

    @AfterAll
    static void tearDown() throws SQLException {
        if (appConn != null) {
            appConn.close();
        }
    }

    @BeforeEach
    void publishTenant() throws SQLException {
        asTenant(tenant);
    }

    private static void asTenant(UUID tenantId) throws SQLException {
        try (Statement stmt = appConn.createStatement()) {
            stmt.execute("SELECT set_config('exeris.tenant_id', '" + (tenantId == null ? "" : tenantId) + "', false)");
        }
    }

    private static TransactionalExecutor executor() {
        return new TransactionalExecutor() {
            @Override
            public void execute(TransactionalWork work) {
                executeManaged(work);
            }

            @Override
            public void executeManaged(TransactionalWork work) {
                try {
                    PersistenceConnection conn = new JdbcPersistenceConnection(appConn);
                    conn.beginTransaction();
                    work.run(conn);
                    conn.commit();
                } catch (Exception e) {
                    try {
                        appConn.rollback();
                    } catch (SQLException ignored) {
                        // the original failure is the one to report
                    }
                    throw new RuntimeException(e);
                }
            }

            @Override
            public void executeManaged(TransactionIsolation isolation, boolean readOnly, TransactionalWork work) {
                executeManaged(work);
            }

            @Override
            public <T> T query(Function<PersistenceConnection, T> query) {
                try {
                    return query.apply(new JdbcPersistenceConnection(appConn));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        };
    }

    private static String hash() {
        return (UUID.randomUUID().toString() + UUID.randomUUID()).replace("-", "").substring(0, 64);
    }

    private InvitationRecord record(UUID tenantId, String email, String tokenHash, Instant expiresAt) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        return new InvitationRecord(UUID.randomUUID(), tenantId, email, "TENANT_MEMBER", admin, tokenHash,
                now, expiresAt.truncatedTo(ChronoUnit.MICROS), null, null);
    }

    @Test
    @DisplayName("insert and consume round-trip, and a second consume finds nothing")
    void consumeOnce() {
        String h = hash();
        InvitationRecord r = record(tenant, "ann@exeris.eu", h, Instant.now().plusSeconds(3600));
        repository.insert(r);

        Optional<InvitationRecord> consumed = repository.consume(h, tenant, "ann@exeris.eu", Instant.now());
        assertThat(consumed).isPresent();
        assertThat(consumed.get().id()).isEqualTo(r.id());
        assertThat(consumed.get().consumedAt()).isNotNull();
        assertThat(repository.consume(h, tenant, "ann@exeris.eu", Instant.now())).isEmpty();
    }

    @Test
    @DisplayName("a wrong address or tenant does not consume; the right one then still can")
    void mismatchLeavesInvitationPending() throws SQLException {
        String h = hash();
        repository.insert(record(tenant, "ann@exeris.eu", h, Instant.now().plusSeconds(3600)));

        assertThat(repository.consume(h, tenant, "mallory@exeris.eu", Instant.now())).isEmpty();
        assertThat(repository.consume(h, UUID.randomUUID(), "ann@exeris.eu", Instant.now())).isEmpty();
        assertThat(repository.findPending(h, tenant, "ann@exeris.eu", Instant.now())).isPresent();
        assertThat(repository.consume(h, tenant, "ann@exeris.eu", Instant.now())).isPresent();
    }

    @Test
    @DisplayName("an expired invitation is not consumed")
    void expired() {
        String h = hash();
        repository.insert(record(tenant, "ann@exeris.eu", h, Instant.now().plusSeconds(1)));
        assertThat(repository.consume(h, tenant, "ann@exeris.eu", Instant.now().plusSeconds(5))).isEmpty();
        assertThat(repository.findPending(h, tenant, "ann@exeris.eu", Instant.now().plusSeconds(5))).isEmpty();
    }

    @Test
    @DisplayName("revoke blocks consumption and is not repeatable")
    void revoke() {
        String h = hash();
        InvitationRecord r = record(tenant, "ann@exeris.eu", h, Instant.now().plusSeconds(3600));
        repository.insert(r);
        assertThat(repository.revoke(tenant, r.id(), Instant.now())).isTrue();
        assertThat(repository.revoke(tenant, r.id(), Instant.now())).isFalse();
        assertThat(repository.consume(h, tenant, "ann@exeris.eu", Instant.now())).isEmpty();
    }

    @Test
    @DisplayName("list is paged, newest first, and scoped to the tenant")
    void listPaged() throws SQLException {
        UUID t = UUID.randomUUID();
        asTenant(t);
        for (int i = 0; i < 3; i++) {
            InvitationRecord base = record(t, "u" + i + "@exeris.eu", hash(), Instant.now().plusSeconds(3600));
            repository.insert(new InvitationRecord(base.id(), t, base.email(), base.role(), admin, base.tokenHash(),
                    base.createdAt().plusSeconds(i), base.expiresAt().plusSeconds(i), null, null));
        }
        assertThat(repository.list(t, 2, 0)).extracting(InvitationRecord::email)
                .containsExactly("u2@exeris.eu", "u1@exeris.eu");
        assertThat(repository.list(t, 2, 2)).extracting(InvitationRecord::email).containsExactly("u0@exeris.eu");
    }

    @Test
    @DisplayName("row-level security hides another tenant's rows and refuses a foreign write")
    void rowLevelSecurity() throws SQLException {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        String h = hash();
        asTenant(a);
        InvitationRecord r = record(a, "ann@exeris.eu", h, Instant.now().plusSeconds(3600));
        repository.insert(r);

        asTenant(b);
        assertThat(repository.list(a, 10, 0)).isEmpty();
        assertThat(repository.findPending(h, a, "ann@exeris.eu", Instant.now())).isEmpty();
        assertThat(repository.consume(h, a, "ann@exeris.eu", Instant.now())).isEmpty();
        assertThat(repository.revoke(a, r.id(), Instant.now())).isFalse();
        assertThatThrownBy(() -> repository.insert(record(a, "x@exeris.eu", hash(), Instant.now().plusSeconds(60))))
                .isInstanceOf(RuntimeException.class);

        asTenant(null);
        assertThat(repository.list(a, 10, 0)).isEmpty();

        asTenant(a);
        assertThat(repository.list(a, 10, 0)).extracting(InvitationRecord::id).containsExactly(r.id());
    }

    @Test
    @DisplayName("the service issues, lists, revokes and consumes through PostgreSQL; only the hash is stored")
    void serviceEndToEnd() throws SQLException {
        IssuedInvitation issued = service.issue(tenant, admin, "Bob@Exeris.eu", "TENANT_ADMIN", Duration.ofHours(1));
        assertThat(service.list(tenant, admin, 100, 0)).extracting(i -> i.id()).contains(issued.invitation().id());

        try (Statement stmt = appConn.createStatement();
             var rs = stmt.executeQuery("SELECT token_hash FROM invitations WHERE id = '" + issued.invitation().id() + "'")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString(1)).hasSize(64).doesNotContain(issued.rawToken());
        }
        assertThatThrownBy(() -> service.consume(issued.rawToken(), tenant, "eve@exeris.eu"))
                .isInstanceOf(InvalidInvitationException.class);
        assertThat(service.consume(issued.rawToken(), tenant, "bob@exeris.eu").role()).isEqualTo("TENANT_ADMIN");
        assertThatThrownBy(() -> service.consume(issued.rawToken(), tenant, "bob@exeris.eu"))
                .isInstanceOf(InvalidInvitationException.class);
        List<?> all = service.list(tenant, admin, 100, 0);
        assertThat(all).isNotEmpty();
    }
}
