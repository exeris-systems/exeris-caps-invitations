package eu.exeris.caps.invitations.internal;

import eu.exeris.caps.invitations.InvalidInvitationException;
import eu.exeris.caps.invitations.InvitationService;
import eu.exeris.caps.invitations.internal.repository.InMemoryInvitationRepository;
import eu.exeris.caps.invitations.model.InviteToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("DefaultInvitationService Unit Tests")
class DefaultInvitationServiceTest {

    private InMemoryInvitationRepository repository;
    private DefaultInvitationService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryInvitationRepository();
        service = new DefaultInvitationService(repository);
    }

    @Test
    @DisplayName("createInvitation generates valid unconsumed invitation")
    void createInvitationSuccess() {
        UUID tenantId = UUID.randomUUID();
        InvitationService.CreatedInvitation created = service.createInvitation(
                tenantId,
                "Alice@Example.com",
                "TENANT_MEMBER",
                Duration.ofDays(7)
        );

        assertThat(created.rawToken()).startsWith("inv_");
        InviteToken record = created.inviteToken();
        assertThat(record.targetTenantId()).isEqualTo(tenantId);
        assertThat(record.email()).isEqualTo("alice@example.com");
        assertThat(record.role()).isEqualTo("TENANT_MEMBER");
        assertThat(record.isConsumed()).isFalse();
        assertThat(record.tokenHash()).isNotBlank();
    }

    @Test
    @DisplayName("findValidInvitation returns present record for active token")
    void findValidInvitationSuccess() {
        UUID tenantId = UUID.randomUUID();
        InvitationService.CreatedInvitation created = service.createInvitation(
                tenantId,
                "bob@example.com",
                "TENANT_ADMIN",
                Duration.ofHours(24)
        );

        Optional<InviteToken> found = service.findValidInvitation(created.rawToken());
        assertThat(found).isPresent();
        assertThat(found.get().id()).isEqualTo(created.inviteToken().id());
    }

    @Test
    @DisplayName("acceptInvitation consumes token and subsequent acceptance throws")
    void acceptInvitationSuccessAndReplayRejected() {
        UUID tenantId = UUID.randomUUID();
        InvitationService.CreatedInvitation created = service.createInvitation(
                tenantId,
                "charlie@example.com",
                "TENANT_MEMBER",
                Duration.ofDays(1)
        );

        InviteToken accepted = service.acceptInvitation(created.rawToken());
        assertThat(accepted.isConsumed()).isTrue();
        assertThat(accepted.consumedAt()).isNotNull();

        // Second acceptance must fail
        assertThatThrownBy(() -> service.acceptInvitation(created.rawToken()))
                .isInstanceOf(InvalidInvitationException.class)
                .hasMessageContaining("already consumed");

        // Finding valid invitation now returns empty
        assertThat(service.findValidInvitation(created.rawToken())).isEmpty();
    }

    @Test
    @DisplayName("acceptInvitation throws on expired invitation")
    void acceptInvitationExpiredThrows() {
        UUID tenantId = UUID.randomUUID();
        // Negative TTL = already expired
        InvitationService.CreatedInvitation created = service.createInvitation(
                tenantId,
                "dave@example.com",
                "TENANT_MEMBER",
                Duration.ofSeconds(-10)
        );

        assertThat(service.findValidInvitation(created.rawToken())).isEmpty();

        assertThatThrownBy(() -> service.acceptInvitation(created.rawToken()))
                .isInstanceOf(InvalidInvitationException.class);
    }

    @Test
    @DisplayName("acceptInvitation throws on unknown token")
    void acceptInvitationUnknownThrows() {
        assertThatThrownBy(() -> service.acceptInvitation("inv_invalidunknown"))
                .isInstanceOf(InvalidInvitationException.class);
    }
}
