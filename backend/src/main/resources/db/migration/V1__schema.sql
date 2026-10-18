-- AgencyDesk schema.
-- Rule 1: every tenant-owned table has agency_id NOT NULL.
-- Rule 2: every reference to another tenant-owned row is a composite FK on (id, agency_id),
--         so the database itself rejects a row that points into a different agency.
-- Rule 3: anything a client might see carries a visibility flag ('internal' | 'client').

-- ---------- Tenants and identity ----------

CREATE TABLE agencies (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name       TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- A person. Global: no agency_id. One row per email, ever.
CREATE TABLE users (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email         TEXT NOT NULL,
    name          TEXT NOT NULL,
    password_hash TEXT NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT users_email_lowercase CHECK (email = lower(email))
);
CREATE UNIQUE INDEX users_email_uq ON users (email);

CREATE TABLE clients (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    agency_id  BIGINT NOT NULL REFERENCES agencies (id),
    name       TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (id, agency_id)
);

-- A person's role inside ONE agency. Same user can have one membership per agency.
CREATE TABLE memberships (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id    BIGINT NOT NULL REFERENCES users (id),
    agency_id  BIGINT NOT NULL REFERENCES agencies (id),
    role       TEXT   NOT NULL CHECK (role IN ('agency_admin', 'agency_member', 'client_user')),
    client_id  BIGINT,
    active     BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (user_id, agency_id),
    UNIQUE (id, agency_id),
    -- client_user must belong to exactly one client of THIS agency; staff never do.
    CONSTRAINT membership_client_matches_role
        CHECK ((role = 'client_user') = (client_id IS NOT NULL)),
    FOREIGN KEY (client_id, agency_id) REFERENCES clients (id, agency_id)
);

-- ---------- Work ----------

CREATE TABLE projects (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    agency_id  BIGINT NOT NULL REFERENCES agencies (id),
    client_id  BIGINT NOT NULL,
    name       TEXT   NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (id, agency_id),
    UNIQUE (id, agency_id, client_id),
    FOREIGN KEY (client_id, agency_id) REFERENCES clients (id, agency_id)
);

-- Which agency_members work on which project (agency_admin sees all projects anyway).
CREATE TABLE project_members (
    project_id    BIGINT NOT NULL,
    membership_id BIGINT NOT NULL,
    agency_id     BIGINT NOT NULL,
    added_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (project_id, membership_id),
    FOREIGN KEY (project_id, agency_id)    REFERENCES projects (id, agency_id),
    FOREIGN KEY (membership_id, agency_id) REFERENCES memberships (id, agency_id)
);

CREATE TABLE tasks (
    id                     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    agency_id              BIGINT NOT NULL,
    project_id             BIGINT NOT NULL,
    title                  TEXT   NOT NULL,
    description            TEXT,
    status                 TEXT   NOT NULL DEFAULT 'todo'
                           CHECK (status IN ('todo', 'in_progress', 'review', 'done')),
    priority               TEXT   NOT NULL DEFAULT 'medium'
                           CHECK (priority IN ('low', 'medium', 'high', 'urgent')),
    assignee_membership_id BIGINT,
    due_date               DATE,
    visibility             TEXT   NOT NULL DEFAULT 'internal'
                           CHECK (visibility IN ('internal', 'client')),
    -- set when the assignee is removed from the project (history kept, owner cleared)
    needs_reassignment     BOOLEAN NOT NULL DEFAULT FALSE,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (id, agency_id),
    FOREIGN KEY (project_id, agency_id)             REFERENCES projects (id, agency_id),
    FOREIGN KEY (assignee_membership_id, agency_id) REFERENCES memberships (id, agency_id)
);
CREATE INDEX tasks_project_idx ON tasks (agency_id, project_id);

CREATE TABLE comments (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    agency_id            BIGINT NOT NULL,
    task_id              BIGINT NOT NULL,
    author_membership_id BIGINT NOT NULL,
    body                 TEXT   NOT NULL,
    visibility           TEXT   NOT NULL DEFAULT 'internal'
                         CHECK (visibility IN ('internal', 'client')),
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    FOREIGN KEY (task_id, agency_id)              REFERENCES tasks (id, agency_id),
    FOREIGN KEY (author_membership_id, agency_id) REFERENCES memberships (id, agency_id)
);
CREATE INDEX comments_task_idx ON comments (agency_id, task_id);

CREATE TABLE files (
    id                     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    agency_id              BIGINT NOT NULL,
    task_id                BIGINT NOT NULL,
    uploader_membership_id BIGINT NOT NULL,
    filename               TEXT   NOT NULL,
    content_type           TEXT,
    size_bytes             BIGINT NOT NULL CHECK (size_bytes >= 0),
    storage_path           TEXT   NOT NULL,
    visibility             TEXT   NOT NULL DEFAULT 'internal'
                           CHECK (visibility IN ('internal', 'client')),
    approval_status        TEXT   NOT NULL DEFAULT 'pending'
                           CHECK (approval_status IN ('pending', 'approved', 'needs_changes')),
    reviewed_by_membership_id BIGINT,
    reviewed_at            TIMESTAMPTZ,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    FOREIGN KEY (task_id, agency_id)                   REFERENCES tasks (id, agency_id),
    FOREIGN KEY (uploader_membership_id, agency_id)    REFERENCES memberships (id, agency_id),
    FOREIGN KEY (reviewed_by_membership_id, agency_id) REFERENCES memberships (id, agency_id)
);
CREATE INDEX files_task_idx ON files (agency_id, task_id);

CREATE TABLE time_entries (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    agency_id     BIGINT NOT NULL,
    task_id       BIGINT NOT NULL,
    membership_id BIGINT NOT NULL,
    minutes       INT    NOT NULL CHECK (minutes > 0 AND minutes <= 24 * 60),
    note          TEXT,
    entry_date    DATE   NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    FOREIGN KEY (task_id, agency_id)       REFERENCES tasks (id, agency_id),
    FOREIGN KEY (membership_id, agency_id) REFERENCES memberships (id, agency_id)
);
CREATE INDEX time_entries_task_idx ON time_entries (agency_id, task_id);

-- ---------- Invites and sessions ----------

CREATE TABLE invites (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    agency_id        BIGINT NOT NULL REFERENCES agencies (id),
    email            TEXT   NOT NULL CHECK (email = lower(email)),
    role             TEXT   NOT NULL CHECK (role IN ('agency_admin', 'agency_member', 'client_user')),
    client_id        BIGINT,
    token            TEXT   NOT NULL UNIQUE,
    status           TEXT   NOT NULL DEFAULT 'pending'
                     CHECK (status IN ('pending', 'accepted', 'revoked')),
    invited_by_membership_id BIGINT NOT NULL,
    accepted_user_id BIGINT REFERENCES users (id),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    accepted_at      TIMESTAMPTZ,
    CONSTRAINT invite_client_matches_role CHECK ((role = 'client_user') = (client_id IS NOT NULL)),
    FOREIGN KEY (client_id, agency_id)                REFERENCES clients (id, agency_id),
    FOREIGN KEY (invited_by_membership_id, agency_id) REFERENCES memberships (id, agency_id)
);
-- Invite race #1: at most ONE pending invite per (agency, email). Resend reuses it.
CREATE UNIQUE INDEX invites_one_pending_uq ON invites (agency_id, email) WHERE status = 'pending';

CREATE TABLE sessions (
    token         TEXT PRIMARY KEY,
    user_id       BIGINT NOT NULL REFERENCES users (id),
    -- NULL until the person picks which agency/membership to act in
    membership_id BIGINT REFERENCES memberships (id),
    expires_at    TIMESTAMPTZ NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
