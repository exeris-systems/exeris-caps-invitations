package eu.exeris.caps.invitations;

import eu.exeris.caps.invitations.internal.repository.InMemoryInvitationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("InvitationsModule Lifecycle and Wiring Tests")
class InvitationsModuleTest {

    @Test
    @DisplayName("module wires default service and passes lifecycle transitions")
    void lifecycleAndWiring() throws Exception {
        InvitationsModule module = new InvitationsModule();

        assertThat(module.invitationService()).isNotNull();
        assertThat(module.isInitialized()).isFalse();
        assertThat(module.isReady()).isFalse();

        module.initialize();
        assertThat(module.isInitialized()).isTrue();

        module.ready();
        assertThat(module.isReady()).isTrue();

        module.drain(Duration.ofSeconds(1));
        assertThat(module.isReady()).isFalse();

        module.terminate();
        assertThat(module.isInitialized()).isFalse();
    }

    @Test
    @DisplayName("module with explicit repository wires correctly")
    void moduleWithExplicitRepository() {
        InMemoryInvitationRepository repo = new InMemoryInvitationRepository();
        InvitationsModule module = new InvitationsModule(repo);
        assertThat(module.invitationService()).isNotNull();
    }
}
