---
title: "Roadmap of the invitations capability"
type: roadmap
visibility: public
owning-repo: exeris-caps-invitations
status: active
last-verified: 2026-10-15
---

# Roadmap of `exeris-caps-invitations`

This file records what the proof of concept does not cover. Read it together with the code: an item
is removed by the pull request that closes it. An item under "Before 0.1.0" has a tracking issue in
the `0.1.0` milestone, so the milestone shows what blocks the first release. Items under "Later" do
not block it.

Milestones are versions, not dates.

## Before 0.1.0

### Authorise the role being granted

[Tracking issue #12](https://github.com/exeris-systems/exeris-caps-invitations/issues/12).
`InvitationAdministrators.mayAdminister(tenantId, subjectId)` is the only check `issue` makes, so any
subject who may administer a tenant's invitations can issue one carrying any role of 1 to 64
characters, including a role above their own. The role is checked for length only, and the
composition has no hook to refuse a grant. Adding the role to the SPI is breaking, which costs
nothing before the first release. Code: `spi/InvitationAdministrators.java`,
`internal/DefaultInvitationService.java` (`issue`, `authorize`).

## Later

### Take the transactional executor in `initialize()`

The module declares `@Requires(service = TransactionalExecutor.class, optional = true)`, and the
composing SKU builds the wired instance by hand with `new InvitationsModule(tx, administrators[, policy])`.
The manifest records the requirement as `satisfied: false` with no provider. The requirement stays
optional because a `@Requires` on a kernel SPI type is reported as a capability-graph error
(`exeris-tooling#409`, open). The kernel `development/0.13.0` branch publishes
`KernelProviders.TRANSACTIONAL_EXECUTOR`, so the module can read it in `initialize()` once the
tooling accepts the requirement. Until then the cost is that the SKU owns the wiring and the no-arg
instance hands out no service. Code: `InvitationsModule.java`, `cap-manifest.json`. Waits on:
`exeris-tooling#409`.

### Acceptance and membership are two transactions

`consume` is one conditional write that commits on its own. Creating the tenant membership is left
to the caller, so a failure after `consume` returns leaves the invitation used up with no
membership, and the invitee cannot retry with the same token. Closing this needs either a consume
that joins the caller's transaction or a documented reissue path; that is a design question for the
Identity SKU and the persistence SPI, not a change this repository can make alone. Code:
`internal/repository/PostgresInvitationRepository.java` (`consume`).

### Repeated invitations for one address

Nothing limits a tenant to one pending invitation per address. Each `issue` for the same tenant and
e-mail creates a new valid token, and older ones stay usable until they expire or are revoked. The
cost is a larger set of live tokens for one invitee and no signal to the issuer. Whether a new
invitation should supersede, refuse or coexist with a pending one is undecided. Code:
`DefaultInvitationService.issue`, `V1__invitations.sql` (only `token_hash` is unique).

### Address normalisation is lower-casing only

E-mail is trimmed and lower-cased with `Locale.ROOT`, and checked for one `@`, no whitespace and a
length of 320. Internationalised and provider-specific forms are compared as written, so an
acceptor whose verified address differs in form from the invited one is refused. This is fail-closed
and not a defect, but it is a usability limit that needs a rule on what counts as the same address.
Code: `DefaultInvitationService.normalizeEmail`, `lower`.

### Expired and consumed rows are never removed

The table keeps every invitation. Expired, revoked and consumed rows stay indefinitely, and `list`
returns them with the pending ones. The cost grows with issuance volume. A retention rule, and the
scheduling to apply it, belong to the composition or to a later contract. Code:
`V1__invitations.sql`, `PostgresInvitationRepository.list`.

### Paging has no total and no stable cursor

`list` takes `limit` (1 to 100) and `offset` and returns a plain list. A caller cannot learn the
total, and an offset page shifts when invitations are issued between calls. Code:
`InvitationService.list`, `PostgresInvitationRepository.list`.

### No audit event

ADR-099 lists `audit-trail` as an optional neighbour of this capability. The module declares no
requirement on it and emits nothing when an invitation is issued, revoked or consumed, so the
composition cannot record who invited whom without wrapping the service. Waits on a decision about
the audit contract between capabilities. Code: `InvitationsModule.java`.

### The declared contract version has no test

Nothing in the test sources asserts the `@Provides` version of `InvitationService`. A change of the
declared version is caught only by the committed-manifest diff. A test that reads the manifest or
the annotation would pin it. Code: `InvitationsModuleTest.java`.

### Review rule S5 names only the kernel SPI

`docs/repo-review-rules.md` S5 lists "the kernel SPI" as the runtime classpath, while S1 allows the
public packages of `exeris-kernel-core` as well. A compile-scope `exeris-kernel-core` dependency
that S1 permits would still read as dependency creep under S5. The wording needs to name Core.
Code: `docs/repo-review-rules.md`.
