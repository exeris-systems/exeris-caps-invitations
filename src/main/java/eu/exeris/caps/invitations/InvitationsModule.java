package eu.exeris.caps.invitations;

import eu.exeris.caps.invitations.api.InvitationService;
import eu.exeris.caps.invitations.api.model.InvitationPolicy;
import eu.exeris.caps.invitations.internal.DefaultInvitationService;
import eu.exeris.caps.invitations.internal.repository.InvitationRepository;
import eu.exeris.caps.invitations.internal.repository.PostgresInvitationRepository;
import eu.exeris.caps.invitations.spi.InvitationAdministrators;
import eu.exeris.kernel.spi.persistence.TransactionalExecutor;
import eu.exeris.sdk.annotation.capability.CapabilityLifecycle;
import eu.exeris.sdk.annotation.capability.CapabilityModule;
import eu.exeris.sdk.annotation.capability.Provides;
import eu.exeris.sdk.annotation.capability.Requires;
import eu.exeris.sdk.composition.lifecycle.CapabilityLifecycleHooks;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Capability declaration and lifecycle owner for {@code exeris-caps-invitations}.
 *
 * <p>Provides Layer 5 Domain Primitive:
 * <ul>
 *   <li>{@link InvitationService} (issue, list, revoke and consume single-use hashed invitation tokens)</li>
 * </ul>
 *
 * @since 0.1.0
 */
@CapabilityModule
@CapabilityLifecycle
@Provides(service = InvitationService.class, version = "0.1.0")
@Requires(service = TransactionalExecutor.class, optional = true)
public final class InvitationsModule implements CapabilityLifecycleHooks {

    /**
     * The schema migrations of this capability, in the order they apply, as classpath resource paths. The
     * capability does not apply them itself; the composing SKU does, before the first service call.
     */
    public static final List<String> MIGRATIONS = List.of(
            "db/migration/exeris-caps-invitations/V1__invitations.sql");

    private final InvitationService invitationService;
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final AtomicBoolean ready = new AtomicBoolean(false);

    /**
     * Lifecycle-only instance for reflective instantiation by the composition conductor. It carries no
     * service: {@link #invitationService()} throws {@link IllegalStateException}. There is no in-memory
     * fallback, so an unwired instance cannot silently lose invitations.
     */
    public InvitationsModule() {
        this.invitationService = null;
    }

    /**
     * Wired instance over PostgreSQL through the kernel Persistence SPI, with the default policy.
     *
     * @param tx             kernel transactional executor; required
     * @param administrators decides who may issue, list and revoke; required
     */
    public InvitationsModule(TransactionalExecutor tx, InvitationAdministrators administrators) {
        this(tx, administrators, InvitationPolicy.DEFAULT);
    }

    /**
     * Wired instance over PostgreSQL through the kernel Persistence SPI.
     *
     * @param tx             kernel transactional executor; required
     * @param administrators decides who may issue, list and revoke; required
     * @param policy         lifetime bound; required
     */
    public InvitationsModule(TransactionalExecutor tx, InvitationAdministrators administrators,
                             InvitationPolicy policy) {
        this(new PostgresInvitationRepository(Objects.requireNonNull(tx, "tx must not be null")),
                administrators, policy, Clock.systemUTC());
    }

    /** Wiring over an internal repository; package-private because its parameter types are internal. */
    InvitationsModule(InvitationRepository repository, InvitationAdministrators administrators,
                      InvitationPolicy policy, Clock clock) {
        this.invitationService = new DefaultInvitationService(repository, administrators, policy, clock);
    }

    public InvitationService invitationService() {
        if (invitationService == null) {
            throw new IllegalStateException("InvitationsModule was instantiated without its dependencies; "
                    + "the composing SKU supplies them through the wiring constructor");
        }
        return invitationService;
    }

    @Override
    public void initialize() throws Exception {
        initialized.set(true);
    }

    @Override
    public void ready() throws Exception {
        ready.set(true);
    }

    @Override
    public void drain(Duration remaining) throws Exception {
        ready.set(false);
    }

    @Override
    public void terminate() {
        ready.set(false);
        initialized.set(false);
    }

    public boolean isReady() {
        return ready.get();
    }

    public boolean isInitialized() {
        return initialized.get();
    }
}
