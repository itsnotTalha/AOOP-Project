# VaultChain Spring Boot scaffold

Java 21, Maven, Spring Boot 3.5.16, Spring JDBC and Xerial SQLite JDBC 3.53.4.0.
This phase implements only `GET /api/health` (including `/api/health/`), common
JSON errors, CORS, and database initialization. The Node backend remains the
reference implementation. No React code or runtime scripts have been switched.

Requirements: [migration contract](../docs/SPRING_BOOT_MIGRATION_CONTRACT.md)
and [parity checklist](../docs/SPRING_BOOT_PARITY_CHECKLIST.md).

## Run

Install JDK 21 and Maven 3.6.3 or later. From the workspace root:

```bash
cd VaultChain/spring-server
java -version
mvn -version
mvn spring-boot:run
```

The default is port 3000 and a **new, isolated** `data/vaultchain.sqlite` under
the process working directory. Startup creates missing parent directories and
initializes the database before publishing the DataSource and serving requests.
Initialization failure aborts startup. There is no automatic fallback to the
legacy database and no import of `server/.env`.

To run alongside Node, use a separate port and an explicitly chosen scratch DB:

```bash
PORT=3001 DATABASE_PATH=/tmp/vaultchain-java-dev/vaultchain.sqlite mvn spring-boot:run
```

From another terminal:

```bash
curl -i http://localhost:3001/api/health
curl -i http://localhost:3001/api/not-found
```

Expected JSON bodies:

```json
{"success":true,"message":"VaultChain API running"}
```

```json
{"success":false,"message":"Route not found"}
```

The first returns 200; the second returns 404. All other business routes are
unimplemented and return 404. Unsupported health methods also return the
legacy 404, rather than Spring's default 405. Browser requests accepting HTML
still receive JSON for missing routes. CORS accepts local Vite origins (and,
like Express `cors()`, other origins), Authorization/Content-Type headers,
and returns 204 to valid preflights without credential cookies.

## Build and test

From `VaultChain/spring-server`:

```bash
mvn test
mvn clean verify
java -jar target/spring-server-0.1.0-SNAPSHOT.jar
```

`mvn clean verify` runs every test and builds the executable jar. Maven's first
run requires network access to resolve the pinned dependencies and plugins.
To keep the Maven cache in a writable scratch directory:

```bash
mvn -Dmaven.repo.local=/tmp/vaultchain-spring-m2 clean verify
```

Tests use JUnit `@TempDir` file-backed SQLite databases, synthetic legacy rows,
and an ephemeral local HTTP port. The application integration test overrides
`vaultchain.database-path` through `@DynamicPropertySource`, taking precedence
over an inherited `DATABASE_PATH`. Configuration tests supply explicit temporary
paths or a mock environment. No test opens either legacy SQLite file or writes
to `server/`, `client/`, or the normal `data/` directory.

Coverage includes startup/JDBC templates, health and trailing slash, 404/JSON
errors, CORS, default/override configuration, all 22 tables and 32 explicit
fresh-schema indexes, repeated startup with data/settings unchanged, independent
connection foreign keys, constraints/cascades, historical schema/row migrations,
report rebuild failure rollback, and commission backfill without wallet changes.
These tests establish scaffold behavior, not full backend parity.

## Environment variables

| Name | Default | Behavior |
| --- | --- | --- |
| `PORT` | `3000` | HTTP listen port; use 3001 while Node uses 3000. |
| `DATABASE_PATH` | `./data/vaultchain.sqlite` | SQLite filesystem path, absolute or relative to process working directory; parent directories created. |

The database default intentionally differs from Node's
`server/src/database/vaultchain.sqlite` to make the migration scaffold safe to
start without touching the executable reference's data. Select a **copy** with
`DATABASE_PATH` when validating existing data. Spring property
`vaultchain.database-path` is the internal binding; Boot's standard
`--vaultchain.database-path=/path/to/copy.sqlite` override also works.
`.env` files are not automatically loaded; export variables in the shell.
JWT, upload, pHash and Vault variables from the contract are reserved for their
later migration phases and are not used by this scaffold.

## Safely try an existing database

Do not point this scaffold at the real database. Startup includes legacy
backfills and, when needed, the verification table rebuild. First create a
consistent disposable SQLite backup. For example, from `spring-server/`,
after identifying which file the legacy backend actually uses:

```bash
export DATABASE_PATH="$(mktemp -d /tmp/vaultchain-migration-XXXXXX)/copy.sqlite"
python3 - ../server/src/database/vaultchain.sqlite "$DATABASE_PATH" <<'PY'
import pathlib, sqlite3, sys
source = pathlib.Path(sys.argv[1]).resolve()
with sqlite3.connect(source.as_uri() + '?mode=ro', uri=True) as original:
    with sqlite3.connect(sys.argv[2]) as disposable:
        original.backup(disposable)
PY
PORT=3001 mvn spring-boot:run
```

Replace the source argument if the legacy `DATABASE_PATH` selects another file.
The read-only SQLite backup API handles a consistent snapshot, including a live
database's WAL. The example does not copy uploads; this phase does not read or
serve them. Inspect copied data locally without publishing credentials or files.

## Schema initialization and compatibility

`src/main/resources/database/schema.sql` is an exact copy of the legacy DDL.
`repository/SchemaInitializer` ports `server/src/database/init.js` in order:

1. Base schema and indexes (`CREATE IF NOT EXISTS`).
2. Verification `user_id` addition/backfill and nullable-asset table rebuild.
3. Vault password and auto-lock columns.
4. Marketplace/listing/history columns, hex reference backfills, duplicate
   active-listing cancellation, and unique/partial indexes.
5. Nullable legacy document columns and OCR status defaults.
6. User status/role normalization, default settings, and insert-or-ignore
   historical marketplace ledger backfill using the current commission setting.

Each startup repeats these idempotent operations; no new migration-version
table is introduced. Rows, ids, stored credentials/fingerprints/JSON and paths
are preserved except for the exact legacy normalization/backfills. Existing
settings are not overwritten. Legacy constraints are not silently replaced
with the stronger fresh-schema constraints. Legacy invalid-role CHECK constraints
or duplicate nonnull public references can still fail startup as they do in Node.

The dedicated initialization connection temporarily disables foreign keys
**outside** the verification rebuild transaction. Failure rolls back that
transaction and always re-enables foreign keys before closing the connection.
Earlier migration steps are not wrapped in a global transaction, matching Node.
This adds explicit failure cleanup rather than leaving a failed rebuild open.

The Node rebuild drops two base verification indexes; only its user index is
recreated immediately. This port deliberately preserves that behavior: the next
startup restores the base indexes. Tests cover both first and subsequent starts;
data is stable on every repeat. Historical orphaned reports are retained rather
than discarded during the FK-disabled rebuild. Use `PRAGMA foreign_key_check`
on the copy to assess existing inconsistencies before future cutover.

Foreign keys are configured through `SQLiteConfig.enforceForeignKeys(true)` on
the `SQLiteDataSource`, so **every physical connection**, including Spring JDBC
connections opened after initialization, enforces them. This scaffold uses an
unpooled DataSource; `JdbcTemplate` and `NamedParameterJdbcTemplate` are supplied
by Boot. There is no JPA/Hibernate dependency, schema auto-update or background
database reset. Later transaction work must explicitly preserve SQLite writer
serialization and atomic purchase boundaries.

## Package layout

```text
com.vaultchain
  config/       Database construction/initialization and CORS
  controller/   Health endpoint
  dto/          Exact health/error response records
  exception/    Shared exception advice and servlet error fallback
  repository/   Ordered SQLite schema initializer
  security/     Reserved for the authentication phase
  service/      Reserved for migrated business logic
  storage/      Reserved for asset/document file handling
  util/         Reserved for shared algorithms with parity tests
```

Reserved packages contain only package documentation. Authentication/Spring
Security is not activated in this health-only phase; there is no generated login
page, default account, permissive placeholder business API, or JWT implementation.

Reference documentation: [Spring Boot 3.5 system requirements](https://docs.spring.io/spring-boot/3.5/system-requirements.html),
[SQLite per-connection foreign keys](https://www.sqlite.org/foreignkeys.html),
[Xerial SQLite JDBC](https://github.com/xerial/sqlite-jdbc).
