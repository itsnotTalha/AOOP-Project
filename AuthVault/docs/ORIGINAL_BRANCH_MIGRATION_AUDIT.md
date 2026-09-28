# Migration audit against the requested source branch

Audited 2026-09-26. Source: `itsnotTalha/SE-Lab-Project`, branch
`feature/document-module`, commit `0839b084bbb1c3338e43f3c4ccdf7ddd33430737`.
Target: `itsnotTalha/AOOP-Project`, branch `fixing`, `VaultChain/` directory,
commit `e9f6ae7755d886f9d01eaa608a7faa8279205131`. The URL segment
`fixing/VaultChain` means branch `fixing` and folder `VaultChain`; there is no
remote `fixing/VaultChain` branch. The working tree contains additional
uncommitted authentication work and differs from that remote commit.

## Architecture and current coverage

The source backend is Express/JavaScript, with route, controller, service and
repository layers, SQLite, JWT bearer authentication, BCrypt passwords,
filesystem upload storage, image fingerprinting, OCR, Vault access sessions,
wallets, marketplace and verification. The source declares 50 explicit routes
across health (1), auth (6), assets (8), Vaults (11), documents (10), global
verification (3), wallet (3), marketplace (7) and dashboard (1). Its schema
declares 20 tables.

The local Spring Boot 3.5.16/Java 21 app now maps health, six auth, ten document, three wallet and one dashboard routes (21/50). It uses Spring Data JPA entities/repositories for all 23 local tables. Legacy SQLite schema initialization still uses SQL before Hibernate starts. The remaining 29 explicit source routes are absent. Document OCR and exact API parity remain open. `mvn clean install` passes 36 tests on 2026-09-26; no frontend cutover or full feature parity is established.

## Required migration checklist

- [x] Clone both linked repository states to disposable audit directories and
  identify the actual branch/path mapping.
- [x] Compare route inventory, schema and local Spring implementation.
- [x] Run the current Spring `mvn clean install` baseline successfully.
- [ ] Rebase the existing migration contract and 53-test checklist on the
  requested **source branch**. They describe a different local Node variant
  with admin APIs and without the source branch's document verification APIs.
- [x] Add JPA entities and Spring Data repositories for all 23 local schema tables, including all 20 source tables. Replace runtime auth and Vault grant JDBC repositories. Keep SQL only for ordered legacy schema migration and dashboard aggregate queries through JPA.
- [ ] Validate every JPA association and cascade against the source branch, especially composite Vault keys and ownership transfers.
- [ ] Port and test all eight asset routes: uploads, fingerprints, metadata,
  owner scoped reads, protected content and ownership history.
- [ ] Port and test all eleven Vault routes: ownership, membership, password
  security, grants, rate limiting, lock expiry and token scoped revocation.
- [ ] Port and test all three global verification routes: matching, reports,
  ranking, privacy and temporary file cleanup.
- [x] Map and smoke-test all ten document routes: upload, list/search, detail, content, preview, OCR read/retry, verification, report history and delete. Persist reports in `document_verifications` through JPA.
- [ ] Finish document parity: Tesseract/TrOCR runtime, OCR confidence, source-equivalent duplicate comparisons, error boundaries and differential fixture testing.
- [x] Port and integration-test wallet (3) and dashboard summary (1) using JPA.
- [ ] Port marketplace (7), including atomic sale/ownership/balance transitions and concurrent purchase behavior.
- [ ] Reconcile authentication and schema with the requested source branch;
  its behavior differs from the local Node reference used for the existing
  authentication implementation and tests.
- [ ] Add DTO and validation coverage for every route, safe JSON errors, and
  owner/role authorization. Run source tests, Java tests and differential HTTP
  tests against disposable databases and file directories.
- [ ] Start the packaged app, verify database connection and every endpoint,
  then switch runtime configuration only after parity is demonstrated.

## Important source divergence

The current target tree's `VaultChain/server` is **not** an exact copy of the
linked original branch. It contains admin routes/settings and an older document
API, while the linked source has document preview, document verification,
verification report history and a `document_verifications` table. The target Spring schema now includes that table and has 23 tables total; its document endpoints are mapped but their OCR parity remains incomplete. Implementing against the old 64-endpoint contract
alone would miss requested source behavior. Preserve these differences until
the user chooses whether the intended product is strict source parity or a
superset that also retains target-only admin features.

## Validation limit

The successful Maven build validates only the existing health/auth/database
scaffold. It does not establish document, asset, Vault, commerce or dashboard
behavior. The local environment has `pdfinfo`, `pdftotext` and `pdftoppm`, but
no `tesseract` executable; OCR parity needs a bundled Java OCR engine or a
provisioned Tesseract runtime and fixture-based comparison.
