# Spring Boot parity checklist

Audit dates: 2026-09-20–2026-09-21. Companion: [migration contract](SPRING_BOOT_MIGRATION_CONTRACT.md).

This maps **all 53 named tests in eight `server/tests/*.test.js` files**. Every Java target below is proposed and **not implemented**. Unchecked means outstanding, regardless of whether its Node reference currently passes. Existing service-level assertions about internal `error.code` must not become HTTP response fields. Java integration tests should use JUnit5/Spring Boot Test and real disposable SQLite, not an in-memory database with different SQL/constraints.

## Test-by-test replacement map

Source links point to exact existing test declarations. Preserve every assertion inside each test; the final column highlights the behaviors and numeric expectations most likely to be lost. Split large journeys into additional Java tests if useful, while retaining the full journey. Several Node files share fixtures/state between sequential tests; isolated Java tests must create equivalent state explicitly.

### adminApi.test.js — 4 tests

| Done | Exact Node test | Phase | Proposed Java replacement | Required assertions |
| --- | --- | --- | --- | --- |
| [ ] | [admin routes reject regular users and enforce specialist role boundaries](../server/tests/adminApi.test.js#L47) | 9 | `AdminAuthorizationIT#refreshRolesAndEnforceSpecialists` | Regular USER forbidden; promotion/demotion takes effect on an already-issued token; MODERATOR overview/security yes, revenue no; FINANCE revenue/transactions yes, security no. |
| [ ] | [overview and finance endpoints aggregate persisted marketplace records](../server/tests/adminApi.test.js#L61) | 9 | `AdminAnalyticsIT#aggregatePersistedSales` | Persisted sale1000/fee50 yields overview revenue50/one transaction/one verified asset, nonzero timeline bucket, revenue source100%, marketplace fee50, matching TX row. |
| [ ] | [super admin mutations persist settings and audited user access changes](../server/tests/adminApi.test.js#L70) | 9 | `AdminMutationIT#persistSettingsAndAudit` | 7.5% stores string0.075; minimum25 stores string25; user role/status update persists; settings and access actions appear in audit log. |
| [ ] | [custom date ranges include the complete selected end date](../server/tests/adminApi.test.js#L77) | 9 | `AdminAnalyticsIT#includeFullCustomEndDate` | Same date from/to includes the entire UTC end date, not just midnight. |

### assetApi.test.js — 37 tests

| Done | Exact Node test | Phase | Proposed Java replacement | Required assertions |
| --- | --- | --- | --- | --- |
| [ ] | [authentication middleware rejects missing credentials and accepts a valid JWT](../server/tests/assetApi.test.js#L168) | 3 | `AuthenticationIT#validateBearerAndFingerprint` | Missing credentials ->401 Unauthorized; valid JWT populates current id and exact-token SHA-256 fingerprint. |
| [ ] | [upload still creates hashes and metadata without exposing a filesystem path](../server/tests/assetApi.test.js#L182) | 4 | `AssetUploadIT#persistFingerprintsAndMetadata` | Upload creates asset/hash/metadata, true hasMetadata, no filesystem path in public asset. |
| [ ] | [asset list is owner-scoped, omits private paths, and returns newest first](../server/tests/assetApi.test.js#L197) | 4 | `AssetReadIT#scopeAndOrderList` | Only caller-owned assets, newest created_at/id first; omit filePath and ownerId; foreign caller gets empty list. |
| [ ] | [asset detail, hash, metadata, and content lookup enforce ownership](../server/tests/assetApi.test.js#L221) | 4 | `AssetReadIT#enforceOwnershipEveryLookup` | Owner can read detail/hash/metadata/content lookup; foreign account gets404 Asset not found for each. |
| [ ] | [ownership check finds the current user exact match and removes its temporary file](../server/tests/assetApi.test.js#L238) | 4 | `OwnershipCheckIT#exactOwnMatchAndCleanup` | Own exact match returns id/title/You,64hex SHA, null pHash; check directory empty. |
| [ ] | [cross-owner ownership match is pseudonymous and does not leak private asset data](../server/tests/assetApi.test.js#L250) | 4 | `OwnershipCheckIT#redactForeignExactMatch` | Foreign exact match null id/title, VC owner/asset references, no personal identity/path/name/stored hashes; file removed. |
| [ ] | [ownership check reports an identical perceptual fingerprint when SHA-256 differs](../server/tests/assetApi.test.js#L268) | 4 | `OwnershipCheckIT#equalVisualHashDifferentSha` | PNG tEXt variant changes SHA but pHash distance0, perceptual/identical, threshold6,256bits; cleanup. |
| [ ] | [JPEG recompression produces a strong perceptual match with a different SHA-256](../server/tests/assetApi.test.js#L282) | 4 | `FingerprintCompatibilityIT#jpegRecompressionDistance` | Reuse Node-produced JPEG75 variant bytes; perceptual strong distance EXACTLY2,256bits; cleanup. |
| [ ] | [resizing produces a strong cross-owner perceptual match without private identity data](../server/tests/assetApi.test.js#L291) | 4 | `FingerprintCompatibilityIT#resizedImageDistance` | Reuse Node nearest-neighbor72%-size PNG bytes; strong distance EXACTLY2, foreign privacy, cleanup. |
| [ ] | [small brightness modification is evaluated as a perceptual rather than SHA match](../server/tests/assetApi.test.js#L303) | 4 | `FingerprintCompatibilityIT#brightnessVariantDistance` | Node RGB min(255,round(channel*1.05+5)) fixture produces different SHA, perceptual identical distance0; cleanup. |
| [ ] | [ownership check returns generated fingerprints without registering a no-match image](../server/tests/assetApi.test.js#L312) | 4 | `OwnershipCheckIT#noMatchDoesNotRegister` | Unmatched image returns fingerprints/null asset, no new asset records, temporary directory empty. |
| [ ] | [global verification ranks an exact match first, sorts visual candidates, and caps results at five](../server/tests/assetApi.test.js#L330) | 6 | `GlobalVerificationIT#rankExactAndLimitFive` | Seed pHash distances1/3/6/7/10/12/13; exact ranks first then1/3/6/7, max5, rank1..5, possible at7; own detail only, no raw hashes/private candidates; cleanup. |
| [ ] | [verification classification follows cryptographic and configured perceptual boundaries](../server/tests/assetApi.test.js#L370) | 6 | `VerificationClassificationTest#boundaryClassification` | Exact wins even distance200;6 strong,7 and12 possible,13 no_match under defaults. |
| [ ] | [global verification ranks the strongest visual candidates first](../server/tests/assetApi.test.js#L378) | 6 | `GlobalVerificationIT#rankVisualVariants` | JPEG/resized/brighter nonexact variants yield strong_visual, sorted distance,256bits, cleanup for each. |
| [ ] | [verification classifies an unrelated image as no meaningful match](../server/tests/assetApi.test.js#L398) | 6 | `GlobalVerificationIT#unrelatedImage` | No meaningful match ->result no_match/matches[], nearestDistance>possible, cleanup. |
| [ ] | [cross-owner global matches expose only pseudonymous evidence](../server/tests/assetApi.test.js#L409) | 6 | `GlobalVerificationIT#pseudonymousForeignEvidence` | Foreign exact evidence has VC refs and no asset object/title/fileName/email/metadata/raw hashes/path. |
| [ ] | [verification history and report details are isolated by asset owner](../server/tests/assetApi.test.js#L424) | 6 | `VerificationHistoryIT#requesterIsolation` | Separate requester histories, own detail success/foreign reference404; preserve exact match. Reference test title says asset owner but storage scope is requester user_id. |
| [ ] | [verification removes its temporary file when report persistence fails](../server/tests/assetApi.test.js#L438) | 6 | `VerificationFailureIT#cleanupOnPersistenceFailure` | Inject report repository failure, propagate error and remove comparison file. |
| [ ] | [creates, lists, and retrieves owner-scoped Vaults with safe references](../server/tests/assetApi.test.js#L455) | 5 | `VaultIT#createListGetAndUnlock` | Trim name, safe VT reference, create locked/default10, BCrypt hash stored not plaintext/public; ownership isolation, wrong password401, foreign404, correct unlock expiry. |
| [ ] | [repeated wrong Vault passwords are rate limited before a later successful unlock](../server/tests/assetApi.test.js#L482) | 5 | `VaultRateLimitIT#blockAndRecover` | Set max3/block1s/window60s; first2 wrong401, third429, correct while blocked429; after expiry correct unlock succeeds and attempts clear. |
| [ ] | [updates an owned Vault and rejects cross-owner update attempts](../server/tests/assetApi.test.js#L508) | 5 | `VaultIT#updateOnlyOwnedUnlockedVault` | Change name, preserve omitted description, foreign update404. |
| [ ] | [adds multiple owned assets without copying asset records and updates real dashboard counts](../server/tests/assetApi.test.js#L518) | 5 | `VaultMembershipIT#batchAndDashboardCounts` | Add two owned assets; membership count2 without copying assets; dashboard real totalVaults2/organized2. |
| [ ] | [server-side Vault grants protect assets, require every Vault, expire, lock manually, and clear on logout](../server/tests/assetApi.test.js#L529) | 5 | `VaultAccessIT#allVaultsExpiryLockLogout` | Two protecting Vaults; locked detail redaction and content/hash/metadata423; locked verification evidence redacted; both grants required; TTL1s expiry; manual lock; token logout removes grants; new login distinct fingerprint and locked. |
| [ ] | [password change and account-authenticated reset revoke every session without leaking credentials](../server/tests/assetApi.test.js#L581) | 5 | `VaultSecurityIT#changeAndResetRevokeAllSessions` | Second login unlock; change to30min revokes both grants and old password; account-authenticated reset to5min revokes grants again; wrong password401/foreign404; no credentials leak. |
| [ ] | [rejects duplicate Vault membership and foreign asset injection](../server/tests/assetApi.test.js#L623) | 5 | `VaultMembershipIT#rejectDuplicatesAndForeignAssets` | Existing membership409 and foreign injection404; membership count unchanged. |
| [ ] | [removes only Vault membership while preserving asset, hashes, and verification reports](../server/tests/assetApi.test.js#L646) | 5 | `VaultMembershipIT#removeOnlyMembership` | Remove membership without losing asset/hash/reports; can re-add. |
| [ ] | [deletes a Vault and memberships without deleting contained assets or verification evidence](../server/tests/assetApi.test.js#L656) | 5 | `VaultMembershipIT#deleteOnlyVault` | Foreign delete404; delete removes Vault/memberships, preserves asset/hash/reports. |
| [ ] | [asset row deletion cascades Vault membership without leaving a broken relation](../server/tests/assetApi.test.js#L666) | 5 | `VaultSchemaIT#assetDeletionCascadesMembership` | Direct DB asset delete removes membership and adjusts count; no dangling relation. |
| [ ] | [duplicate detection remains active across accounts](../server/tests/assetApi.test.js#L684) | 4 | `AssetUploadIT#globalDuplicateDetection` | Same image across accounts ->409 already uploaded message; do not reveal registered asset id. |
| [ ] | [assets remain available after a fresh login](../server/tests/assetApi.test.js#L698) | 3,4 | `AssetReadIT#persistAcrossLogin` | Fresh login can still list preexisting owned assets. |
| [ ] | [manual wallet entries cannot forge marketplace purchase or sale activity](../server/tests/assetApi.test.js#L705) | 8 | `WalletIT#rejectForgedMarketEntries` | Manual purchase/sale types400; balance unchanged and no ledger entries. |
| [ ] | [marketplace creation requires ownership, unlocked Vault access, and one active listing per asset](../server/tests/assetApi.test.js#L717) | 8 | `MarketplaceListingIT#ownershipProtectionAndUniqueness` | Foreign asset404, locked asset423, one active listing409/DUPLICATE_LISTING internally; safe listing; foreign preview fields null/content423; foreign cancel404. |
| [ ] | [dashboard summary is user-scoped, complete, and returns bounded recent data newest first](../server/tests/assetApi.test.js#L758) | 9 | `DashboardIT#scopeCountsAndBoundRecentData` | Counts from actual per-user asset/Vault/listing/wallet/report rows; recent assets<=5/activity<=8, deterministic order and no foreign asset. |
| [ ] | [purchase validation prevents self-purchase and insufficient-credit partial changes](../server/tests/assetApi.test.js#L782) | 8 | `MarketplacePurchaseIT#validationHasNoSideEffects` | Own listing409/OWN_LISTING; insufficient funds400/INSUFFICIENT_BALANCE; unchanged owner/seller balance/active listing. |
| [ ] | [purchase atomically transfers ownership, balances, Vault membership, and persistent history](../server/tests/assetApi.test.js#L799) | 8 | `MarketplacePurchaseIT#atomicSaleAndHistory` | Deposit500, purchase125, fee6.25/payout118.75/buyer375; transfer same asset/file/hash/metadata; seller memberships removed, reports retained by requester; history/ledgers linked TX, private refs only, former owner404; dashboard sale/purchase; new ownership checks and global detail reflect buyer. |
| [ ] | [concurrent purchases serialize so exactly one buyer succeeds](../server/tests/assetApi.test.js#L867) | 8 | `MarketplaceConcurrencyIT#singleWinner` | Two simultaneous funded buyers ->one success/one409, one sold listing/owner/history record. |
| [ ] | [forced persistence failure rolls the complete purchase transaction back](../server/tests/assetApi.test.js#L884) | 8 | `MarketplaceRollbackIT#abortHistoryInsert` | SQLite trigger RAISE(ABORT) on ownership_history: balances, owner, active listing and membership restored, no history; drop trigger then cancel listing. |

### authAccount.test.js — 4 tests

| Done | Exact Node test | Phase | Proposed Java replacement | Required assertions |
| --- | --- | --- | --- | --- |
| [ ] | [profile update normalizes values and returns the public account](../server/tests/authAccount.test.js#L36) | 3 | `AccountIT#normalizeProfileAndPublicShape` | Trim name/lowercase email, no password hash, /me snake_case full_name updated. |
| [ ] | [profile update rejects invalid and already registered emails](../server/tests/authAccount.test.js#L49) | 3 | `AccountIT#rejectInvalidOrDuplicateEmail` | Invalid email400 Email is invalid; existing other email409 Email is already registered. |
| [ ] | [password change requires the correct current password and enforces minimum length](../server/tests/authAccount.test.js#L60) | 3 | `AccountIT#validatePasswordChange` | Wrong current401 Current password is incorrect; short new400 exact length message. |
| [ ] | [password change invalidates the old password and accepts the new password](../server/tests/authAccount.test.js#L71) | 3 | `AccountIT#replacePassword` | Old password login401; new password login succeeds for same id. This test does NOT establish JWT revocation. |

### coreWorkflow.test.js — 1 tests

| Done | Exact Node test | Phase | Proposed Java replacement | Required assertions |
| --- | --- | --- | --- | --- |
| [ ] | [complete authenticated asset, verification, Vault, marketplace, wallet, and dashboard journey](../server/tests/coreWorkflow.test.js#L74) | 3–9 | `CoreWorkflowIT#completeAuthenticatedJourney` | HTTP register/login/me/upload ->cross-owner private verification/history ->Vault lock/unlock/content ->locked buyer listing preview ->insufficient-credit no mutation ->deposit500/purchase125/5% ->wallets375 and118.75/linked transactions/ownership/dashboard ->seller loses asset and membership ->delete empty Vault without deleting sold asset ->buyer relists/cancels safely. Keep HTTP201/200/204 and body checks. |

### documentApi.test.js — 1 tests

| Done | Exact Node test | Phase | Proposed Java replacement | Required assertions |
| --- | --- | --- | --- | --- |
| [ ] | [document upload, OCR, listing, ownership, content, failure preservation, and deletion](../server/tests/documentApi.test.js#L87) | 7,9 | `DocumentWorkflowIT#ocrSearchPrivacyFailureDelete` | HTTP auth401/type400; image OCR System Architecture; digital PDF text/page1; broken PNG persists failed201 and content200; newest list omits full OCR; own filename/OCR literal search/snippet incl %_ behavior, PDF/image/status filters; foreign search/detail/content/OCR/retry/delete404; own dashboard counts/recent docs; exact content bytes; delete204 and empty storage. |

### metadataComparison.test.js — 2 tests

| Done | Exact Node test | Phase | Proposed Java replacement | Required assertions |
| --- | --- | --- | --- | --- |
| [ ] | [metadata comparison classifies same, changed, added, removed, and unavailable values](../server/tests/metadataComparison.test.js#L10) | 4,6 | `MetadataComparisonTest#classifyPresenceAndEquality` | same/changed/added/removed/unavailable truth table, preserve null semantics. |
| [ ] | [safe metadata evidence reports GPS presence without exposing coordinates](../server/tests/metadataComparison.test.js#L18) | 4,6 | `MetadataComparisonTest#gpsPrivacyAndSoftwareDiff` | GPS is Present with no coordinates; software/Orientation evidence; removed software classified removed. |

### phashComparison.test.js — 3 tests

| Done | Exact Node test | Phase | Proposed Java replacement | Required assertions |
| --- | --- | --- | --- | --- |
| [ ] | [calculates bit-level Hamming distance rather than differing hex characters](../server/tests/phashComparison.test.js#L9) | 4 | `PhashComparisonTest#bitPopcount` | 0 vs f=4,00 vs03=2,case-insensitive equal hashes=0; not hex-character distance. |
| [ ] | [rejects malformed or unequal-length perceptual hashes](../server/tests/phashComparison.test.js#L15) | 4 | `PhashComparisonTest#invalidHashes` | Empty/nonhex ->hexadecimal error; unequal lengths ->equal lengths error. |
| [ ] | [finds the nearest candidate and safely ignores invalid stored hashes](../server/tests/phashComparison.test.js#L21) | 4 | `PhashComparisonTest#skipBadCandidates` | Skip null stored hash, nearest candidate id3/distance2/hashBits16. |

### verificationMigration.test.js — 1 tests

| Done | Exact Node test | Phase | Proposed Java replacement | Required assertions |
| --- | --- | --- | --- | --- |
| [ ] | [database initialization adds report ownership to the legacy verification table](../server/tests/verificationMigration.test.js#L24) | 2 | `LegacySchemaMigrationIT#initializeOldVerificationTable` | Legacy asset_id NOT NULL table ->user_id added, asset_id nullable, user index present; four Vault tables/security columns/PK; listing fields/reference+active unique indexes; history fields. Use disposable SQLite fixture, never original. |

## What the current suite does not establish

These are additional migration gates, not fabricated existing Node tests. An existing test passing does not prove all endpoint, security, data or decoder parity.

- [ ] **HTTP matrix:** execute every explicit route in the contract against both backends; compare status, exact error message, envelope, casing, types, absent/null, collection order. Include malformed JSON, unknown route, unsupported method, bad ids, auth-before-upload, wrong part name,20MiB/20MiB+1, MIME/extension mismatches, all three content endpoints, HEAD/OPTIONS/ranges/conditional requests and CORS/Helmet behavior.
- [ ] **Health/auth:** health response; all register/login/profile/password validation boundaries; UTF-16 string lengths vs UTF-8 bytes; duplicate email races; account+wallet rollback; suspended login and stale-token suspension, deleted accounts, review status, every role matrix cell including VERIFICATION_ADMIN. Existing tests only exercise a subset.
- [ ] **JWT/BCrypt interoperability:** shared-secret Node->Java and Java->Node tokens, secret byte interpretation, HMAC algorithm acceptance, expiry/iat/jti, numeric-string duration parsing, malformed/expired token errors, stored BCrypt costs10/12 and prefix/multibyte/72-byte truncation vectors. Logout affects only exact-token Vault grants and does not invalidate JWT or other-token grants.
- [ ] **SHA canonical golden corpus:** exact source bytes, exifr outputs, normalized payload UTF-8, final SHA for all formats/metadata variants; document raw SHA separately. Add JSON number/date/array/Unicode/missing-field fixtures and case-sensitive EXIF tag behavior. Match existing stored hashes without rewriting them.
- [ ] **pHash golden corpus:** install only into a disposable reference copy; inspect image-hash5.3.2 internals and pinned decoder behavior, derive actual algorithm rather than selecting by name. Freeze64hex output for JPEG/PNG/WebP/alpha/orientation/flat images, exact existing variant distances, bit ordering and malformed stored hashes. Threshold overrides incl strong>possible, tie-breaking asset ids, malformed candidates, empty registry and exact-SHA malformed-pHash behavior.
- [ ] **Temporary cleanup:** ownership-check decoder/metadata/file errors, verification decoder/report failures, rejected multipart requests; cleanup even on failures. Scanned-PDF subprocess/recognizer failure must remove scratch directories/terminate workers. Distinguish permanent asset-upload orphan behavior from temporary-check invariant.
- [ ] **Vault coverage:** legacy passwordless setup, all name/password byte/confirmation/auto-lock validators; fifty-id batch, Number coercion, duplicate/foreign mixed batch rollback; locked update/delete; grants across tokens, simultaneous failed-attempt increments, change/reset shared rate state, exact expiry boundaries, block/window interaction, subsecond config truncation and upper caps.
- [ ] **Vault response discrepancy:** unlocked Vault containing asset also in locked Vault currently exposes dimensions/contentUrl; direct content still423. Record the intended security resolution separately from unchanged-contract tests. Test other locked asset fields, own ownership-check title, and dashboard titles.
- [ ] **Verification legacy/privacy:** actual image_comparison stored JSON, invalid JSON, nullable asset, unknown report type exclusion, missing optional fields, stored owner labels after sale, old report requester isolation, HMAC secret changes/reference collisions, redaction of foreign/locked details, filename sanitization and ignored extra assetId fields.
- [ ] **OCR:** immutable existing OCR PNG1785433463116-711316499.png; JPEG, digital PDF embedded text below/at10 characters, scanned1/10/11 pages and unknown count, numeric page ordering, trim/newline/confidence semantics, engine/language package availability, corrupt files, owner retry success/failure including retention of prior text, search filters/arrays/200-char limit, literal %_, SQLite Unicode lower behavior,160-char snippet, delete cascade/missing file. Current document journey does not test scanned PDFs or successful owner retry.
- [ ] **Marketplace/wallet:** listing update/cancel/empty PATCH/null+blank fields, minimum/cent precision/max, cancelled/sold list DTOs, foreign preview/session behavior, stale seller ownership, missing wallets, repeated purchase, TX/ML collision, manual withdrawal validation/insufficiency/rollback and fractional unrounded amounts. Test commission0/5/7.5/20%, half-cent binary rounding and invalid settings. No blanket decimal normalization.
- [ ] **Failure-injection transaction matrix:** abort each purchase write, especially marketplace_transactions and each wallet transaction after history insert; assert both wallets, asset, listing, Vault memberships and every ledger restored. Existing trigger test aborts at history only. Race two buyers using separate JDBC connections; shared connections must not enlist unrelated operations. Characterize post-COMMIT read/response failure without pretending committed writes rolled back.
- [ ] **Dashboard/admin completeness:** all admin endpoints and role boundaries, own notifications/mark all read204, logs newest250, settings map string values, audited mutation errors/self-demotion/inactive listing/reactivation conflict; financial/date data on empty/all statuses. Overview non-finance data exposure is current behavior, not covered by role tests alone.
- [ ] **Date analytics:** UTC today/7d/default30d/1y, valid/invalid/from-only/to-only/custom/reversed inputs, full end date, previous-window1hour floor, >62day month buckets,400point cap, zero-fill and English labels; current-vs-all-time population differences, null SUMs, percent rounding and security ISO-vs-SQL lexical expiry issue.
- [ ] **Schema:** fresh and each legacy stage; foreign_keys per connection; every DDL table/index/constraint/cascade/restrict, verification backfill/rebuild and dropped indexes, passwordless Vaults, listing/history backfill+duplicate cancellation, legacy nullable documents, unknown-role normalization, settings preservation, historical sale ledger backfill with current rate, repeat initialization/restart and failed initialization. Existing migration test begins with an empty legacy report table and does not prove data-bearing migrations.
- [ ] **Existing data/storage:** identify actual DATABASE_PATH; make consistent disposable SQLite+storage copies, inventory counts/ids/sequences/schema/FKs/file checksums/report JSON/password hashes/fingerprints without exposing private contents; point both servers at equivalent copies. No production mutation or hash replacement.
- [ ] **Frontend smoke:** all nine unchanged service modules, authenticated object-URL previews, localStorage token flow, /me snake_case mapping, admin success without success flag, marketplace delete200, document/Vault delete204, OCR synchronous response and exact errors.

## Reference execution and fixture requirements

`server/package.json` defines `npm test` as `node --test tests/*.test.js`. Full reference execution requires the lockfile dependencies and Poppler (`pdfinfo`, `pdftotext`, `pdftoppm`), bundled English OCR data, and fixture files in `server/src/uploads/`. No installed node_modules tree is present in the audited project, so **full Node integration baseline is not established**. No Java backend or Java tests were created in Prompt0.

Audit verification: `node --test VaultChain/server/tests/phashComparison.test.js VaultChain/server/tests/metadataComparison.test.js` exited0; these dependency-free files contain the five named helper scenarios mapped above. The runner reported two successful file-level subtests. This is not a claim that the other48 tests passed. Full baseline should run in an isolated copy with DATABASE_PATH, UPLOAD_DIRECTORY, CHECK_UPLOAD_DIRECTORY and DOCUMENT_UPLOAD_DIRECTORY all explicitly redirected; some reference files otherwise leave unused directory defaults at server/src. Do not install dependencies or initialize databases in the original merely to complete the audit.

`assetApi.test.js` selects PNG1785437099015-901021797.png if available, otherwise first PNG; unrelated fixture selection is file-order/size-dependent. `coreWorkflow.test.js` uses first PNG. `documentApi.test.js` requires the exact OCR image noted above. Freeze selected bytes and their checksums for differential tests instead of relying on directory enumeration. Keep fixtures private/local if source uploads contain private material; no external fixture publication is authorized.

Normalize only nondeterministic correspondence (random VT/ML/TX ids, tokens/jti, timestamps, uptime/latency) in cross-backend comparisons; exact HMAC refs with fixed ids/secrets and fingerprint/financial values must agree. Use independent copies so running one backend cannot change the other's baseline. SQL snapshots before/after failure are part of parity, not just HTTP responses.

## Phase exit checklist

| Phase | Required exit evidence | State |
| --- | --- | --- |
| 1 / contract (Prompt0) | All source layers+client services audited; endpoint/schema/security/risk contract and53-test map written | Documentation complete; runtime limitations stated |
| 2 / scaffold | spring-server Java21/Maven scaffold and disposable DB initialization; no original writes | [ ] |
| 3 / Auth | Auth/health/security contract and interoperability tests | [ ] |
| 4 / Assets | Storage/metadata/SHA/pHash/check/protected-content parity and immutable golden vectors | [ ] |
| 5 / Vaults | Grants/attempts/memberships/legacy security/race tests | [ ] |
| 6 / Verification | Ranking/privacy/history/legacy reports/cleanup | [ ] |
| 7 / Documents | OCR/PDF/search/privacy/storage failure parity | [ ] |
| 8 / Commerce | Wallet/listing/atomic sale/rollback/concurrency/history | [ ] |
| 9 / Admin | Dashboard/admin complete role/DTO/aggregation/date matrix | [ ] |
| 10 / Cutover | Full53 Node baseline, all replacements and added gates, differential API+DB+file results, unchanged-client smoke; only then runtime script switch | [ ] |
