-- Demo data. Every account's password is: password123
-- pgcrypto's crypt(..., gen_salt('bf')) produces BCrypt hashes that Spring's BCryptPasswordEncoder verifies.
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- Two agencies (tenants)
INSERT INTO agencies (id, name) OVERRIDING SYSTEM VALUE VALUES
    (1, 'PixelCo'),
    (2, 'BrandHive');

-- PixelCo has two clients (so we can also prove client-to-client isolation inside one agency)
INSERT INTO clients (id, agency_id, name) OVERRIDING SYSTEM VALUE VALUES
    (1, 1, 'Acme Corp'),
    (2, 1, 'Globex'),
    (3, 2, 'Zeta Foods');

INSERT INTO users (id, email, name, password_hash) OVERRIDING SYSTEM VALUE VALUES
    (1, 'admin@pixelco.test',   'Priya (PixelCo admin)',   crypt('password123', gen_salt('bf'))),
    (2, 'maya@pixelco.test',    'Maya (PixelCo member)',   crypt('password123', gen_salt('bf'))),
    (3, 'dev@pixelco.test',     'Dev (PixelCo member)',    crypt('password123', gen_salt('bf'))),
    (4, 'admin@brandhive.test', 'Ben (BrandHive admin)',   crypt('password123', gen_salt('bf'))),
    -- One person, two agencies: client contact for Acme (PixelCo) AND for Zeta (BrandHive)
    (5, 'riya@client.test',     'Riya (client of both)',   crypt('password123', gen_salt('bf'))),
    (6, 'omar@globex.test',     'Omar (Globex client)',    crypt('password123', gen_salt('bf')));

INSERT INTO memberships (id, user_id, agency_id, role, client_id) OVERRIDING SYSTEM VALUE VALUES
    (1, 1, 1, 'agency_admin',  NULL),
    (2, 2, 1, 'agency_member', NULL),
    (3, 3, 1, 'agency_member', NULL),
    (4, 4, 2, 'agency_admin',  NULL),
    (5, 5, 1, 'client_user',   1),   -- Riya @ PixelCo, client Acme
    (6, 5, 2, 'client_user',   3),   -- Riya @ BrandHive, client Zeta
    (7, 6, 1, 'client_user',   2);   -- Omar @ PixelCo, client Globex

INSERT INTO projects (id, agency_id, client_id, name) OVERRIDING SYSTEM VALUE VALUES
    (1, 1, 1, 'Acme Website Redesign'),
    (2, 1, 2, 'Globex Mobile App'),
    (3, 2, 3, 'Zeta Rebrand');

-- Maya and Dev work on the Acme project; only Dev works on Globex
INSERT INTO project_members (project_id, membership_id, agency_id) VALUES
    (1, 2, 1),
    (1, 3, 1),
    (2, 3, 1);

INSERT INTO tasks (id, agency_id, project_id, title, description, status, priority,
                   assignee_membership_id, due_date, visibility) OVERRIDING SYSTEM VALUE VALUES
    (1, 1, 1, 'Homepage mockup',         'First draft of the new homepage',     'review',      'high',   2, '2026-10-25', 'client'),
    (2, 1, 1, 'Internal budget review',  'Margin is thin, discuss pricing',     'todo',        'medium', 2, '2026-10-22', 'internal'),
    (3, 1, 1, 'Set up staging server',   'DevOps chore, client does not care',  'in_progress', 'low',    3, NULL,         'internal'),
    (4, 1, 1, 'Content migration',       'Move blog posts to the new CMS',      'todo',        'medium', 3, '2026-11-01', 'client'),
    (5, 1, 2, 'Login screen',            'Globex app login',                    'in_progress', 'high',   3, '2026-10-28', 'client'),
    (6, 1, 2, 'Globex security audit',   'Internal pen-test notes',             'todo',        'urgent', 3, NULL,         'internal'),
    (7, 2, 3, 'New logo concepts',       'Three directions for Zeta',           'review',      'high',   NULL, '2026-10-30', 'client'),
    (8, 2, 3, 'Supplier quote',          'Internal cost of printing',           'todo',        'low',    NULL, NULL,       'internal');

INSERT INTO comments (agency_id, task_id, author_membership_id, body, visibility) VALUES
    (1, 1, 2, 'Draft uploaded, please review.',               'client'),
    (1, 1, 5, 'Looks great, can we try a darker header?',     'client'),
    (1, 1, 1, 'Client is picky, keep revisions to two.',      'internal'),
    (1, 2, 1, 'We are 20% over budget on this project.',      'internal'),
    (2, 7, 4, 'Logo concepts attached.',                      'client');

INSERT INTO files (agency_id, task_id, uploader_membership_id, filename, content_type, size_bytes,
                   storage_path, visibility, approval_status) VALUES
    (1, 1, 2, 'homepage-v1.txt',      'text/plain', 21, 'seed/homepage-v1.txt',      'client',   'pending'),
    (1, 2, 1, 'budget-internal.txt',  'text/plain', 22, 'seed/budget-internal.txt',  'internal', 'pending'),
    (2, 7, 4, 'zeta-logos.txt',       'text/plain', 16, 'seed/zeta-logos.txt',       'client',   'pending');

INSERT INTO time_entries (agency_id, task_id, membership_id, minutes, note, entry_date) VALUES
    (1, 1, 2, 180, 'Homepage layout',     '2026-10-14'),
    (1, 1, 2,  90, 'Revisions',           '2026-10-15'),
    (1, 3, 3, 120, 'Staging setup',       '2026-10-15'),
    (1, 5, 3, 240, 'Login screen UI',     '2026-10-16');

-- Explicit ids were used above; move every identity past them.
ALTER TABLE agencies    ALTER COLUMN id RESTART WITH 100;
ALTER TABLE clients     ALTER COLUMN id RESTART WITH 100;
ALTER TABLE users       ALTER COLUMN id RESTART WITH 100;
ALTER TABLE memberships ALTER COLUMN id RESTART WITH 100;
ALTER TABLE projects    ALTER COLUMN id RESTART WITH 100;
ALTER TABLE tasks       ALTER COLUMN id RESTART WITH 100;
