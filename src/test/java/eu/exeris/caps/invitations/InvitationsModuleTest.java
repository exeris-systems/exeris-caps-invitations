package eu.exeris.caps.invitations;

import eu.exeris.caps.invitations.api.model.InvitationPolicy;
import eu.exeris.caps.invitations.internal.repository.InMemoryInvitationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("InvitationsModule lifecycle and wiring")
class InvitationsModuleTest {

    @Test
    @DisplayName("the no-arg instance passes lifecycle transitions and refuses to hand out a service")
    void lifecycleOnlyInstance() throws Exception {
        InvitationsModule module = new InvitationsModule();

        assertThat(module.isInitialized()).isFalse();
        module.initialize();
        assertThat(module.isInitialized()).isTrue();
        module.ready();
        assertThat(module.isReady()).isTrue();
        module.drain(Duration.ofSeconds(1));
        assertThat(module.isReady()).isFalse();
        module.terminate();
        assertThat(module.isInitialized()).isFalse();

        assertThatThrownBy(module::invitationService).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("a wired instance hands out the service")
    void wiredInstance() {
        InvitationsModule module = new InvitationsModule(new InMemoryInvitationRepository(), (t, s) -> true,
                InvitationPolicy.DEFAULT, Clock.systemUTC());
        assertThat(module.invitationService()).isNotNull();
    }

    @Test
    @DisplayName("no public constructor names a type from an internal package")
    void publicConstructorsTakeOnlyPublicTypes() {
        for (Constructor<?> c : InvitationsModule.class.getDeclaredConstructors()) {
            if (Modifier.isPublic(c.getModifiers())) {
                assertThat(Arrays.stream(c.getParameterTypes()).map(Class::getName))
                        .noneMatch(n -> n.contains(".internal."));
            }
        }
    }

    @Test
    @DisplayName("MIGRATIONS names a resource that ships")
    void migrationsResolve() {
        assertThat(InvitationsModule.MIGRATIONS).isNotEmpty().allSatisfy(path ->
                assertThat(InvitationsModuleTest.class.getClassLoader().getResource(path)).as(path).isNotNull());
    }
}
