# AgencyDesk design notes

This is how I designed the app and why. It is a Spring Boot + PostgreSQL backend and a small React frontend.

## 1. Keeping agencies separate

Many agencies share one database, so the main risk is one agency seeing another agency's data.
I handled it in three ways:

- Every table that belongs to an agency has an `agency_id` column that cannot be null.
- References between tables use composite foreign keys, for example `tasks (project_id, agency_id)` points to
  `projects (id, agency_id)`. Because `agency_id` is part of the key, the database itself refuses a task that
  points at a project of another agency. This holds even if I write a buggy query.
- The `agency_id` used in every query comes from the logged-in session on the server. The client never sends it.

If someone guesses an ID from another agency, the query finds nothing and the API returns 404. I chose 404 over 403
because 403 tells the person that the ID exists.

I tried this in psql: inserting a project with a client from one agency and the id of another agency fails with a
foreign key error.

## 2. Keeping internal work away from clients

All the permission checks are in one class, `AccessService`. It checks three things in order:

1. Tenant: the row's `agency_id` must match the session's agency.
2. Role: an admin sees the whole agency, a member sees only projects they were added to, and a client user sees only
   the projects of their own client.
3. Visibility: tasks, comments and files are either `internal` or `client`. A client only gets an item that is marked
   `client` and whose task is also `client`.

Lists, search, filters, comments, files and the dashboard all use the same filter, so I did not have to remember
the rule in each place. The tests cover each of these. To check that the tests really work, I removed the
visibility condition once and 4 tests failed, then I put it back.

Example: a comment marked "client" on an internal task. The client does not see it, because the task itself is
hidden. Also, when a comment is saved on an internal task it is stored as internal.

## 3. Users and memberships

`users` is the person: one row per email, with the password. `memberships` is what that person is inside one agency
(admin, member or client user, and which client). One user can have one membership per agency.

I did it this way because the same person can be a client of two agencies, for example Riya in the demo data.
She logs in once, then picks which agency to work in. The session stores the chosen membership, so the agency and the
role come from the server.

## 4. Edge cases

- Same email in two agencies: one user and two memberships, with the workspace picker after login.
- Invite sent twice: a unique index allows only one pending invite per agency and email, so resending gives back
  the same invite.
- Invite accepted twice at the same time: accepting is one `UPDATE ... WHERE token = ? AND status = 'pending'`.
  The database lets only one of the two requests change the row, and the other one gets a 409. The user is found or
  created by the unique email, so there are no duplicate accounts, and an existing password is not changed.
  There is a test that sends 8 accept requests together and checks that exactly one succeeds.
- Removing a member from a project: their tasks become unassigned and are marked `needs_reassignment`, so the work
  does not get lost. Their logged hours and comments stay, because they are history and may be needed for billing.
  Removing someone stops future access, it does not erase the past.
- A client opening an internal task, comment or file: they get 404, as described in section 2.

## 5. Choices and what is missing

- I used plain SQL with `JdbcClient` instead of JPA, so every `agency_id` filter is written out and easy to review.
- Sessions are random tokens saved in the database, not JWTs. It is easy to log someone out or switch off an account.
- Files are saved on the local disk under `agency-<id>/<random name>`. A real product would use something like S3.
- Not done: PostgreSQL row-level security (it would be a second safety net below the application), sending real
  emails (the invite link is printed in the backend log), automations and intake forms.

## 6. Gantt view

I did not build it. My plan would be to add a start date to tasks and a small table for task dependencies, with
`agency_id` and composite foreign keys like the other tables. The Gantt endpoint would use the same task filter, so a
client only sees client-visible tasks. A dependency on a hidden task should not be shown, or the chart would leak
internal work. Circular dependencies would be rejected when saving.

## 7. Use of AI

I used Claude (Anthropic) while building this. I made the design decisions: separate users and memberships,
`agency_id` on every table, what happens when a member is removed, and whether a client can see a comment on an
internal task. Claude wrote most of the code from those decisions, including the React screens, and helped run the
tests. I went through the schema, the access class and the edge cases so I can explain them.
