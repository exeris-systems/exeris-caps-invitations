package eu.exeris.caps.invitations;

import eu.exeris.caps.invitations.internal.DefaultInvitationService;
import eu.exeris.caps.invitations.internal.repository.InMemoryInvitationRepository;
import eu.exeris.caps.invitations.internal.repository.InvitationRepository;
import eu.exeris.caps.invitations.internal.repository.PostgresInvitationRepository;
import eu.exeris.kernel.spi.persistence.TransactionalExecutor;
import eu.exeris.sdk.annotation.capability.CapabilityLifecycle;
import eu.exeris.sdk.annotation.capability.CapabilityModule;
import eu.exeris.sdk.annotation.capability.Provides;
import eu.exeris.sdk.annotation.capability.Requires;
import eu.exeris.sdk.composition.lifecycle.CapabilityLifecycleHooks;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Capability declaration and lifecycle owner for {@code exeris-caps-invitations}.
 *
 * <p>Provides Layer 5 Domain Primitive:
 * <ul>
 *   <li>{@link InvitationService} (tenant invitation creation, verification, and single-use acceptance)</li>
 * </ul>
 *
 * @since 0.1.0
 */
@CapabilityModule
@CapabilityLifecycle
@Provides(service = InvitationService.class, version = "0.1.0")
@Requires(service = TransactionalExecutor.class, optional = true)
public final class InvitationsModule implements CapabilityLifecycleHooks {

    private final InvitationService invitationService;
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final AtomicBoolean ready = new AtomicBoolean(false);

    /**
     * Public no-arg constructor for reflective instantiation by the composition conductor.
     */
    public InvitationsModule() {
        this(new InMemoryInvitationRepository());
    }

    /**
     * Constructor wiring with kernel persistence SPI.
     */
    public InvitationsModule(TransactionalExecutor tx) {
        this(tx != null ? new PostgresInvitationRepository(tx) : new InMemoryInvitationRepository());
    }

    /**
     * Full constructor with explicit repository.
     */
    public InvitationsModule(InvitationRepository repository) {
        Objects.requireNonNull(repository, "repository must not be null");
        this.invitationService = new DefaultInvitationService(repository);
    }

    public InvitationService invitationService() {
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
