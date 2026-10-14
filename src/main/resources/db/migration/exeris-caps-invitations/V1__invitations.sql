-- exeris-caps-invitations, schema version 1.
--
-- Data scope: tenant (ADR-059 TENANT). An invitation belongs to the tenant it invites into, and the
-- table is isolated by row-level security keyed on the session key the kernel publishes (ADR-012):
-- `exeris.tenant_id`. A session with no tenant sees no row and may write none; the policy turns the
-- empty key into NULL before the uuid cast so that an absent tenant yields an empty result, not an error.
--
-- The invitation token is stored as a SHA-256 hash only (ADR-099 obligation 9). A pending invitation
-- is one with neither consumed_at nor revoked_at set and an expires_at in the future.

CREATE TABLE IF NOT EXISTS invitations (
    id          UUID         PRIMARY KEY,
    tenant_id   UUID         NOT NULL,
    email       VARCHAR(320) NOT NULL,
    role        VARCHAR(64)  NOT NULL,
    issued_by   UUID         NOT NULL,
    token_hash  CHAR(64)     NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL,
    expires_at  TIMESTAMPTZ  NOT NULL,
    consumed_at TIMESTAMPTZ,
    revoked_at  TIMESTAMPTZ,
    CONSTRAINT uq_invitations_token_hash UNIQUE (token_hash),
    CONSTRAINT chk_invitations_expiry CHECK (expires_at > created_at)
);

CREATE INDEX IF NOT EXISTS idx_invitations_tenant_created
    ON invitations (tenant_id, created_at DESC, id);

ALTER TABLE invitations ENABLE ROW LEVEL SECURITY;
ALTER TABLE invitations FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS tenant_isolation ON invitations;
CREATE POLICY tenant_isolation ON invitations
    USING (tenant_id = NULLIF(current_setting('exeris.tenant_id', true), '')::uuid)
    WITH CHECK (tenant_id = NULLIF(current_setting('exeris.tenant_id', true), '')::uuid);
