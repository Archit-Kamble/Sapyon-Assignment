# AgencyDesk

A multi-tenant project management app for agencies and their clients.
Backend: Spring Boot (Java 21) with PostgreSQL. Frontend: React with Vite.
The design decisions are in [DESIGN.md](DESIGN.md).

## What you need
Java 21, Node 18 or newer, and PostgreSQL 16 running on your machine.

## Setup

1. Create the database (once):
```bash
sudo -u postgres psql -c "CREATE ROLE agencydesk LOGIN PASSWORD 'agencydesk' CREATEDB;"
sudo -u postgres psql -c "CREATE DATABASE agencydesk OWNER agencydesk;"
sudo -u postgres psql -c "CREATE DATABASE agencydesk_test OWNER agencydesk;"
```
The last one is only for the tests. The tables and demo data are created automatically when the backend starts
(Flyway migrations).

2. Start the backend on port 8081:
```bash
cd backend
./mvnw spring-boot:run
```
The defaults can be changed with the environment variables `DB_URL`, `DB_USER`, `DB_PASSWORD`, `PORT` and
`UPLOAD_DIR`.

3. Start the frontend:
```bash
cd frontend
npm install
npm run dev
```
Open http://localhost:5173. Vite forwards `/api` requests to the backend.

4. Run the tests (optional):
```bash
cd backend
./mvnw test
```
There are 25 integration tests. They call the real API over HTTP and use the `agencydesk_test` database, which is
reset before every test.

## Demo accounts
The password for all of them is `password123`.

| Email | Agency | Role | What they can see |
| --- | --- | --- | --- |
| admin@pixelco.test | PixelCo | admin | everything in PixelCo |
| maya@pixelco.test | PixelCo | member | only the "Acme Website Redesign" project |
| dev@pixelco.test | PixelCo | member | the Acme and Globex projects |
| admin@brandhive.test | BrandHive | admin | everything in BrandHive |
| riya@client.test | PixelCo and BrandHive | client | after login she picks a workspace, then sees only client-visible items of her own client |
| omar@globex.test | PixelCo | client (Globex) | only the Globex project, client-visible tasks |

## Things to try
1. Log in as riya@client.test. You get a workspace picker because she is a client in two agencies. In PixelCo she
   sees 2 of the 4 Acme tasks, and the dashboard counts only those.
2. As Riya, search for "budget". Nothing comes back, because that task is internal.
3. As the PixelCo admin, remove Maya from the Acme project. Her tasks are marked "needs owner" and her logged hours stay.
4. As admin, open Invites and invite the same email twice. You get the same invite back. Accepting one invite link
   twice works only the first time.

Invite emails are not sent. The invite link is shown on the Invites page and printed in the backend log.

## Folders
- `backend/src/main/resources/db/migration`: the schema (V1) and the demo data (V2)
- `backend/src/main/java/com/agencydesk`: the code. `access` has `AccessService`, which decides who can see what.
  The other folders are `auth`, `project`, `task`, `comment`, `file`, `time`, `dashboard`, `invite` and `admin`.
- `backend/src/test`: the tests
- `frontend/src`: the React app
