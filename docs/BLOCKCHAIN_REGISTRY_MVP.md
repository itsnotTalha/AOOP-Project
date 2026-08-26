# Blockchain Registered Original Registry MVP

## Status

### Implemented

- A repository-local Hyperledger Fabric 2.5 Docker Compose development network
- `CreatorsOrg` and `AuthenticatorsOrg`, each with one CouchDB-backed peer
- One local Raft orderer and the `vaultchain-channel` application channel
- Go contract-API chaincode for immutable verified-original registration
- Exact deterministic SHA-256 lookup and uniqueness enforcement
- Fabric ledger history queries
- Two-organization chaincode endorsement policy
- Unit tests and an optional running-network smoke test
- Spring-managed Fabric Gateway connection using the CreatorsOrg identity
- Owner-authenticated registration, original lookup, exact SHA-256 lookup,
  and provenance/history endpoints
- Deterministic pseudonymous creator and Version 1 evidence hashes
- An opt-in live Spring/Gateway integration test
- Authenticated Verify Originality exact-SHA lookup and Fabric-confirmed
  cross-owner pHash fallback

### Off-chain by design

- Image and document bytes
- pHash values and similarity indexes
- UFD scores, TruFor outputs, maps, masks, and model files
- Forensic evidence records (only their SHA-256 anchor is stored)
- OCR text, filesystem paths, JWTs, and personal identity documents

The Spring application remains the owner of assets, authentication, exact
integrity checks, perceptual analysis, and AI/manipulation evidence. Fabric is
only the canonical source of registered-original provenance.

### Pending

- Authenticator workflow and production client-identity authorization
- Four-organization topology (`CreatorsOrg`, `AuthenticatorsOrg`,
  `GalleriesOrg`, and `BuyersOrg`)
- Three-orderer production Raft topology and production certificate authorities
- Frontend Verify Originality workflow and an explicitly designed anonymous
  verification policy, if required
- Ownership transfer and marketplace capabilities

## Spring integration boundary

The Fabric Gateway is disabled by default. When enabled, Spring validates one
explicit TLS CA file, CreatorsOrg signing certificate, and private key at
startup, opens one reusable gRPC channel, and closes it during shutdown. SDK
objects never enter API DTOs or controllers. Failed Fabric operations are
translated into stable `BLOCKCHAIN_*` application errors.

Registration is permitted only for an authenticated owner's existing
`VERIFIED` `IMAGE` or `DOCUMENT`. It sends the public asset UUID, pseudonymous
creator hash, exact SHA-256, type, status, and evidence hash. It never sends
file bytes, pHash, model output, paths, JWTs, or personal user attributes.

Version 1 evidence uses exactly this UTF-8 canonical representation, joined by
line-feed characters without a trailing line-feed:

```text
AUTHVAULT_REGISTRATION_EVIDENCE_V1
assetId=<public asset UUID>
assetType=<IMAGE or DOCUMENT>
sha256=<64 lowercase hexadecimal characters>
verificationStatus=VERIFIED
lastVerifiedAt=<ISO_LOCAL_DATE_TIME>
```

The ledger receives its lowercase SHA-256. Creator identity is likewise
domain-separated as `SHA-256(UTF-8("AUTHVAULT_CREATOR_ID_V1:" + userUuid))`;
the stable internal UUID itself is not exposed.

## Registry contract

`OriginalAsset` stores `assetId`, `creatorIdHash`, `currentOwnerIdHash`,
`sha256`, `assetType`, `verificationStatus`, `evidenceHash`, and
`registeredAt`. Initial ownership always equals the pseudonymous creator
reference. Only `IMAGE` and `DOCUMENT` assets with status `VERIFIED` are
accepted. Both hashes must be exactly 64 lowercase hexadecimal characters.

The contract exposes only:

- `RegisterOriginal`
- `GetOriginal`
- `FindBySha256`
- `OriginalExists`
- `GetAssetHistory`

Registration reads and writes an `originalSha256` composite key in the same
Fabric transaction as the `originalAsset` composite key. This avoids a world
state scan and lets Fabric MVCC invalidate concurrent attempts to claim the
same SHA-256. `registeredAt` is derived from the Fabric transaction timestamp;
chaincode does not use wall-clock time.

## Development security and topology boundary

The deployed definition requires:

```text
AND('CreatorsOrgMSP.peer','AuthenticatorsOrgMSP.peer')
```

Thus every valid registry write must be endorsed by both local organization
peers. The current `cryptogen` identities, single Raft orderer, local CouchDB
credentials, and Docker socket access are development-only. They do not claim
the fault tolerance, certificate lifecycle, secret management, access control,
or four-organization governance required for production.
