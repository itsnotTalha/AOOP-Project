# Verify Originality MVP

## API and boundary

`POST /api/v1/originality/verify` accepts one authenticated multipart field
named `image` containing JPEG or PNG bytes. The image is a temporary
verification input. The operation does not create an asset, persist the
submitted image or masks, modify ownership/status, or submit a Fabric
transaction.

Spring applies the existing image byte-size, extension, signature, and decode
validation. It streams the upload into a server-generated managed temporary
file while calculating SHA-256 and deletes every submitted/reference temporary
file on success or failure. Client filenames never determine storage paths.

## Decision pipeline

The evidence is evaluated in this order:

1. Calculate SHA-256 from the raw submitted bytes.
2. call Fabric `FindBySha256` through `OriginalRegistryClient`;
3. return `EXACT_REGISTERED_ORIGINAL` immediately for a consistent Fabric
   image record;
4. after a successful exact miss, calculate the deterministic existing pHash;
5. retrieve and rank a bounded cross-owner set of locally stored, `VERIFIED`
   image records with valid pHashes;
6. starting with the closest pHash, call Fabric `FindBySha256` for at most the
   configured candidate count and trust only a response whose ledger SHA and
   public asset UUID match the local record;
7. stream, hash, and validate that confirmed registered original into a second
   managed temporary file;
8. call the internal image comparison service with the registered original as
   `reference` and the submitted image as `target`;
9. return a structured outcome, independent evidence sections, and reason
   codes.

The existing `GET /api/v1/assets/{assetId}/similar-images` and
`POST /api/v1/assets/{assetId}/compare-known-original` flows remain
owner-scoped. Only this originality workflow performs cross-owner discovery,
and its public result never exposes application users or storage metadata.

## Outcomes

- `EXACT_REGISTERED_ORIGINAL`: submitted raw bytes have a SHA-256 exact match
  in Fabric. pHash and comparison are not run.
- `MODIFIED_REGISTERED_ORIGINAL`: SHA-256 differs, Fabric confirms a strong
  `EXACT_VISUAL_HASH`/`NEAR_DUPLICATE` source, comparison completes with
  reliable alignment, and the existing difference stage reports a non-zero
  changed-area ratio after its configured pixel/region filtering.
- `POSSIBLE_DERIVATIVE`: Fabric confirms a visually related source but the
  pHash band or comparison alignment/change evidence is weaker or ambiguous.
- `NO_REGISTERED_SOURCE_FOUND`: Fabric exact lookup completed with no match,
  pHash fallback completed, and no plausible local candidate was confirmed by
  Fabric.
- `INCONCLUSIVE`: authoritative Fabric lookup, pHash evaluation, trusted
  source retrieval/validation, or comparison could not be completed safely.

`NO_REGISTERED_SOURCE_FOUND` means only that VaultChain did not find a
matching registered source in the evaluated registry/candidate window. It
does not prove that the image is original.

`MODIFIED_REGISTERED_ORIGINAL` means technical evidence indicates that the
submitted file differs from a Fabric-confirmed registered source. It is not a
legal determination of copying, infringement, ownership, fraud, or intent.

## Comparison and compatibility evidence

The workflow reuses `/v1/compare/images`; it does not reimplement ORB,
homography, overlap handling, changed-region detection, or mask generation.
The temporary Base64 difference mask may be returned and is not persisted.

The existing `aiEvidence.status=DEFERRED` field remains in this MVP response
for public compatibility. It does not invoke learned-model inference and is
not used for exact provenance or registered-source comparison.

## Privacy and ledger authority

Responses may contain the public registered asset UUID and Fabric's
pseudonymous `creatorIdHash`, SHA-256, evidence hash, registration timestamp,
asset type, and verification status. Responses never contain user email,
username, internal user/database IDs, stable internal user UUID, JWT, original
storage key/path, certificate/private-key material, or internal comparison-service
location.

The database does not contain a synthetic `blockchainRegistered` flag. Fabric
is checked for the submitted SHA and for every plausible candidate considered.
Fabric disabled/unavailable/invalid responses produce `INCONCLUSIVE`; they are
never translated into `NO_REGISTERED_SOURCE_FOUND`.

## Bounded search and MVP limitation

SQLite does not provide the required indexed 64-bit pHash Hamming-distance
operation in the current schema. The repository therefore retrieves at most
`authvault.verification.phash.registry-scan-limit` recent `VERIFIED` image
records with non-null pHashes (default `100`), ranks them in Java, excludes
`NO_MATCH`, and sends at most `max-candidates` (default `5`) to Fabric.

This avoids scanning or contacting Fabric for every image, but it is not a
complete large-scale similarity index: an older plausible original outside
the bounded scan window may not be found. A future production phase should use
an indexed perceptual-hash search strategy rather than increasing the bound
without operational analysis.
