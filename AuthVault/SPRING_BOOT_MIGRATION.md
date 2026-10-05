# Spring Boot migration status

Source behavior was audited from `SE-Lab-Project/feature/document-module` at commit `0839b084`. This directory contains the Spring Boot backend and the restored React frontend in `client/`. The legacy Node server is no longer part of this checkout.

## Route coverage

The original Express server declares 50 routes. All 50 have Spring controller mappings; see [the route tracker](docs/ROUTE_MIGRATION_TRACKER.md). Route mapping does not establish exact parity for every input and image format.

| Module | Source routes | Spring mappings |
| --- | ---: | ---: |
| Health | 1 | 1 |
| Authentication | 6 | 6 |
| Documents | 10 | 10 |
| Assets | 8 | 8 |
| Vaults | 11 | 11 |
| Global verification | 3 | 3 |
| Marketplace | 7 | 7 |
| Wallet | 3 | 3 |
| Dashboard | 1 | 1 |
| **Total** | **50** | **50** |

## Backend architecture

The backend uses Spring Boot 3.5.16, Java 21, Spring Security, Spring Data JPA, and SQLite. JPA entities and repositories cover all 23 tables in the target schema. The existing SQLite schema initializer handles DDL and legacy backfills; Hibernate uses `ddl-auto=none`. Business persistence uses JPA repositories. The backend stores asset and document bytes on the filesystem and metadata, hashes, access grants, reports, ownership history, and wallet entries in SQLite.

Asset uploads calculate a metadata-aware SHA-256 and 256-bit block hash. Vault unlock grants are token scoped. Global verification stores ranked image matches. Marketplace purchase transfers balances and ownership and records wallet and ownership history in one transaction. Document upload, preview, content, OCR result, verification, report history, and deletion are mapped and persisted.

## Verification after the backend-only cleanup

- `mvn clean test` and `mvn clean install` passed 42 tests from the Spring Maven project.
- The packaged JAR started with disposable SQLite and returned the expected health JSON.
- Integration tests covered authentication, document storage, asset upload/read/check, Vault access, verification history, marketplace sale and forced rollback, wallet, dashboard, and schema migrations.
- The original Node server and the Spring JAR were run with separate disposable databases. Live HTTP comparisons matched representative auth, asset, Vault, verification, marketplace, wallet, and ownership-history flows. The synthetic PNG SHA-256 and block hash matched exactly.

## Remaining parity limits

- Printed image and scanned PDF OCR needs local Tesseract and English training data. This environment did not have that executable. Handwritten TrOCR, confidence scoring, and exact decoder behavior remain incomplete.
- The image metadata port matches the tested baseline PNG. JPEG EXIF, additional PNG metadata chunks, and WebP need more fixture comparisons.
- Marketplace purchase passed a forced history-write rollback test, but cross-process purchase races and other failure points have not been tested.
- The live source/target comparison covered representative flows, not every error branch or every response field.
- Spring redacts preview fields in an unlocked Vault when another protecting Vault is locked. This intentional security difference prevents a nested-response metadata leak.

## Run

Run Maven directly from this directory. See [README.md](README.md) for environment variables and commands. The original Node server is no longer present. The React client is restored, its client-only npm workspace builds successfully, and its default API URL points to the Spring backend on port 3000. A live Vite dev server and packaged Spring JAR both started; the React page, Spring health route, and cross-origin preflight succeeded. Browser click-through was not completed.
