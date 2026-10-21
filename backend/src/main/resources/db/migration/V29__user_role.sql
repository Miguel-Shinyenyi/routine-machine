-- Access control, part 4: a role per account. Every existing account is an ordinary USER. The OWNER
-- role is set at startup from the OWNER_EMAIL environment variable (OwnerRoleService), never here,
-- so no address lives in the schema or its history.
ALTER TABLE users ADD COLUMN role VARCHAR(20) NOT NULL DEFAULT 'USER';
