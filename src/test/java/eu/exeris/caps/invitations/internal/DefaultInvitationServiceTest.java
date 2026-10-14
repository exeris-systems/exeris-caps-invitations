package eu.exeris.caps.invitations.internal;

import eu.exeris.caps.invitations.api.exception.InvalidInvitationException;
import eu.exeris.caps.invitations.api.exception.InvitationAccessDeniedException;
import eu.exeris.caps.invitations.api.model.Invitation;
import eu.exeris.caps.invitations.api.model.InvitationPolicy;
import eu.exeris.caps.invitations.api.model.IssuedInvitation;
import eu.exeris.caps.invitations.internal.repository.InMemoryInvitationRepository;
import eu.exeris.caps.invitations.spi.InvitationAdministrators;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("DefaultInvitationService")
class DefaultInvitationServiceTest {

    private static final class MutableClock extends Clock {
        Instant now = Instant.parse("2026-10-14T10:00:00Z");
        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    private final UUID tenant = UUID.randomUUID();
    private final UUID admin = UUID.randomUUID();
    private final UUID stranger = UUID.randomUUID();
    private MutableClock clock;
    private DefaultInvitationService service;

    @BeforeEach
    void setUp() {
        clock = new MutableClock();
        InvitationAdministrators admins = (t, s) -> t.equals(tenant) && s.equals(admin);
        service = new DefaultInvitationService(new InMemoryInvitationRepository(), admins,
                new InvitationPolicy(Duration.ofDays(7)), clock);
    }

    private IssuedInvitation issue() {
        return service.issue(tenant, admin, "Ann@Exeris.EU", "TENANT_MEMBER", Duration.ofHours(1));
    }

    @Test
    @DisplayName("the token carries at least 256 bits and is unique per invitation")
    void tokenStrength() {
        IssuedInvitation a = issue();
        IssuedInvitation b = issue();
        assertThat(a.rawToken()).startsWith("inv_").isNotEqualTo(b.rawToken());
        assertThat(Base64.getUrlDecoder().decode(a.rawToken().substring(4))).hasSize(32);
        assertThat(a.invitation().email()).isEqualTo("ann@exeris.eu");
    }

    @Test
    @DisplayName("e-mail normalisation does not depend on the default locale")
    void localeIndependent() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertThat(service.issue(tenant, admin, "INFO@EXERIS.EU", "R", Duration.ofHours(1)).invitation().email())
                    .isEqualTo("info@exeris.eu");
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    @DisplayName("ttl must be positive and within the configured maximum")
    void ttlBounds() {
        assertThatThrownBy(() -> service.issue(tenant, admin, "a@b.eu", "R", Duration.ZERO))
                .isInstanceOf(InvalidInvitationException.class);
        assertThatThrownBy(() -> service.issue(tenant, admin, "a@b.eu", "R", Duration.ofSeconds(-1)))
                .isInstanceOf(InvalidInvitationException.class);
        assertThatThrownBy(() -> service.issue(tenant, admin, "a@b.eu", "R", Duration.ofDays(7).plusNanos(1)))
                .isInstanceOf(InvalidInvitationException.class);
        assertThat(service.issue(tenant, admin, "a@b.eu", "R", Duration.ofDays(7))).isNotNull();
    }

    @Test
    @DisplayName("the policy rejects a non-positive maximum")
    void policyRejectsNonPositive() {
        assertThatThrownBy(() -> new InvitationPolicy(Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InvitationPolicy(Duration.ofSeconds(-5))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("malformed e-mail and role are rejected")
    void argumentValidation() {
        for (String bad : List.of("", "no-at", "a@@b", "a b@c.eu", "@c.eu", "a@")) {
            assertThatThrownBy(() -> service.issue(tenant, admin, bad, "R", Duration.ofHours(1)))
                    .as(bad).isInstanceOf(InvalidInvitationException.class);
        }
        assertThatThrownBy(() -> service.issue(tenant, admin, "a@b.eu", " ", Duration.ofHours(1)))
                .isInstanceOf(InvalidInvitationException.class);
    }

    @Test
    @DisplayName("consume succeeds once for the right tenant and address, and the address is case-insensitive")
    void consumeOnce() {
        IssuedInvitation issued = issue();
        Invitation consumed = service.consume(issued.rawToken(), tenant, " ANN@exeris.eu ");
        assertThat(consumed.isConsumed()).isTrue();
        assertThat(consumed.id()).isEqualTo(issued.invitation().id());
        assertThatThrownBy(() -> service.consume(issued.rawToken(), tenant, "ann@exeris.eu"))
                .isInstanceOf(InvalidInvitationException.class);
    }

    @Test
    @DisplayName("a wrong acceptor or tenant fails without burning the invitation")
    void mismatchDoesNotConsume() {
        IssuedInvitation issued = issue();
        assertThatThrownBy(() -> service.consume(issued.rawToken(), tenant, "mallory@exeris.eu"))
                .isInstanceOf(InvalidInvitationException.class);
        assertThatThrownBy(() -> service.consume(issued.rawToken(), UUID.randomUUID(), "ann@exeris.eu"))
                .isInstanceOf(InvalidInvitationException.class);
        assertThat(service.findPending(issued.rawToken(), tenant, "ann@exeris.eu")).isPresent();
        assertThat(service.consume(issued.rawToken(), tenant, "ann@exeris.eu").isConsumed()).isTrue();
    }

    @Test
    @DisplayName("every failure to consume reads the same")
    void uniformMessage() {
        IssuedInvitation issued = issue();
        String unknown = catchMessage(() -> service.consume("inv_nope", tenant, "ann@exeris.eu"));
        String wrongMail = catchMessage(() -> service.consume(issued.rawToken(), tenant, "x@exeris.eu"));
        assertThat(unknown).isEqualTo(wrongMail);
    }

    private static String catchMessage(Runnable r) {
        try {
            r.run();
        } catch (InvalidInvitationException e) {
            return e.getMessage();
        }
        throw new AssertionError("expected InvalidInvitationException");
    }

    @Test
    @DisplayName("null, blank and oversized tokens are refused")
    void badTokens() {
        for (String t : new String[]{null, "", "  ", "x".repeat(500)}) {
            assertThatThrownBy(() -> service.consume(t, tenant, "ann@exeris.eu"))
                    .isInstanceOf(InvalidInvitationException.class);
            assertThat(service.findPending(t, tenant, "ann@exeris.eu")).isEmpty();
        }
    }

    @Test
    @DisplayName("an expired invitation can be neither found nor consumed")
    void expiry() {
        IssuedInvitation issued = issue();
        clock.now = clock.now.plus(Duration.ofHours(1));
        assertThat(service.findPending(issued.rawToken(), tenant, "ann@exeris.eu")).isEmpty();
        assertThatThrownBy(() -> service.consume(issued.rawToken(), tenant, "ann@exeris.eu"))
                .isInstanceOf(InvalidInvitationException.class);
    }

    @Test
    @DisplayName("a revoked invitation cannot be consumed, and revoking twice reports false")
    void revoke() {
        IssuedInvitation issued = issue();
        assertThat(service.revoke(tenant, admin, issued.invitation().id())).isTrue();
        assertThat(service.revoke(tenant, admin, issued.invitation().id())).isFalse();
        assertThatThrownBy(() -> service.consume(issued.rawToken(), tenant, "ann@exeris.eu"))
                .isInstanceOf(InvalidInvitationException.class);
    }

    @Test
    @DisplayName("a consumed invitation cannot be revoked")
    void revokeConsumed() {
        IssuedInvitation issued = issue();
        service.consume(issued.rawToken(), tenant, "ann@exeris.eu");
        assertThat(service.revoke(tenant, admin, issued.invitation().id())).isFalse();
    }

    @Test
    @DisplayName("revoke is scoped to the tenant")
    void revokeOtherTenant() {
        UUID other = UUID.randomUUID();
        InvitationAdministrators both = (t, s) -> s.equals(admin);
        DefaultInvitationService multi = new DefaultInvitationService(new InMemoryInvitationRepository(), both,
                InvitationPolicy.DEFAULT, clock);
        IssuedInvitation issued = multi.issue(tenant, admin, "a@b.eu", "R", Duration.ofHours(1));
        assertThat(multi.revoke(other, admin, issued.invitation().id())).isFalse();
        assertThat(multi.list(other, admin, 10, 0)).isEmpty();
    }

    @Test
    @DisplayName("administrative operations refuse a subject the administrators do not authorise")
    void authorisation() {
        IssuedInvitation issued = issue();
        assertThatThrownBy(() -> service.issue(tenant, stranger, "a@b.eu", "R", Duration.ofHours(1)))
                .isInstanceOf(InvitationAccessDeniedException.class);
        assertThatThrownBy(() -> service.list(tenant, stranger, 10, 0))
                .isInstanceOf(InvitationAccessDeniedException.class);
        assertThatThrownBy(() -> service.revoke(tenant, stranger, issued.invitation().id()))
                .isInstanceOf(InvitationAccessDeniedException.class);
    }

    @Test
    @DisplayName("list pages newest first and validates its paging arguments")
    void listPaging() {
        for (int i = 0; i < 5; i++) {
            clock.now = clock.now.plusSeconds(1);
            service.issue(tenant, admin, "u" + i + "@exeris.eu", "R", Duration.ofHours(1));
        }
        List<Invitation> page1 = service.list(tenant, admin, 2, 0);
        List<Invitation> page3 = service.list(tenant, admin, 2, 4);
        assertThat(page1).extracting(Invitation::email).containsExactly("u4@exeris.eu", "u3@exeris.eu");
        assertThat(page3).extracting(Invitation::email).containsExactly("u0@exeris.eu");
        assertThatThrownBy(() -> service.list(tenant, admin, 0, 0)).isInstanceOf(InvalidInvitationException.class);
        assertThatThrownBy(() -> service.list(tenant, admin, 101, 0)).isInstanceOf(InvalidInvitationException.class);
        assertThatThrownBy(() -> service.list(tenant, admin, 10, -1)).isInstanceOf(InvalidInvitationException.class);
    }

    @Test
    @DisplayName("the public view never exposes the token or its hash")
    void viewHidesToken() {
        IssuedInvitation issued = issue();
        assertThat(issued.invitation().toString()).doesNotContain(issued.rawToken());
        assertThat(issued.toString()).doesNotContain(issued.rawToken());
        assertThat(Set.of(Invitation.class.getRecordComponents()).stream().map(c -> c.getName()))
                .noneMatch(n -> n.toLowerCase(Locale.ROOT).contains("hash") || n.toLowerCase(Locale.ROOT).contains("token"));
    }

    @Test
    @DisplayName("concurrent consumes: exactly one wins")
    void concurrentConsume() throws Exception {
        IssuedInvitation issued = issue();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Callable<Boolean>> tasks = java.util.stream.IntStream.range(0, 16)
                    .<Callable<Boolean>>mapToObj(i -> () -> {
                        try {
                            service.consume(issued.rawToken(), tenant, "ann@exeris.eu");
                            return true;
                        } catch (InvalidInvitationException e) {
                            return false;
                        }
                    }).toList();
            long wins = 0;
            for (Future<Boolean> f : pool.invokeAll(tasks)) {
                if (f.get()) {
                    wins++;
                }
            }
            assertThat(wins).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }
}
