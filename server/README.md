# AuthVault Backend

Backend skeleton for the AuthVault university capstone project.

## Requirements

- Java 21
- Maven

## Run

```bash
mvn spring-boot:run
```

## Registered-original Fabric integration

The backend uses Hyperledger Fabric Gateway to submit and evaluate the
repository's `registered-original-registry` chaincode. It is disabled by
default, so the application starts without Fabric or generated crypto
material. When enabled, startup validates and reads one explicit TLS CA file,
one CreatorsOrg signing certificate, and one explicit private-key file. It
does not scan a key directory or log key/certificate contents.

Start and deploy the repository-local Fabric network from the repository root:

```bash
cd blockchain
./scripts/network-up.sh
./scripts/deploy-registry.sh
```

In another terminal, start Spring from `server/` with the exact generated
development identity paths:

```bash
cd server

export AUTHVAULT_BLOCKCHAIN_ENABLED=true
export AUTHVAULT_FABRIC_MSP_ID=CreatorsOrgMSP
export AUTHVAULT_FABRIC_CHANNEL=vaultchain-channel
export AUTHVAULT_FABRIC_CHAINCODE=registered-original-registry
export AUTHVAULT_FABRIC_PEER_ENDPOINT=localhost:7051
export AUTHVAULT_FABRIC_PEER_HOST_OVERRIDE=peer0.creators.vaultchain.local
export AUTHVAULT_FABRIC_TLS_CERT_PATH=../blockchain/generated/organizations/peerOrganizations/creators.vaultchain.local/peers/peer0.creators.vaultchain.local/tls/ca.crt
export AUTHVAULT_FABRIC_CERT_PATH=../blockchain/generated/organizations/peerOrganizations/creators.vaultchain.local/users/Admin@creators.vaultchain.local/msp/signcerts/Admin@creators.vaultchain.local-cert.pem
export AUTHVAULT_FABRIC_PRIVATE_KEY_PATH=../blockchain/generated/organizations/peerOrganizations/creators.vaultchain.local/users/Admin@creators.vaultchain.local/msp/keystore/priv_sk

mvn spring-boot:run
```

Spring does not import shell-style `.env` files. `server/.env.example` is the
copyable variable inventory; export those values through the shell or your
IDE/run configuration. Never commit the generated files under
`blockchain/generated/` or a real `.env`.

The authenticated API exposes:

- `POST /api/v1/assets/{assetId}/blockchain-registration`
- `GET /api/v1/assets/{assetId}/blockchain-registration`
- `GET /api/v1/assets/{assetId}/blockchain-history`
- `GET /api/v1/blockchain/originals/by-sha256/{sha256}`
- `POST /api/v1/originality/verify` (`multipart/form-data`, field `image`)

Registration is owner-scoped and accepts only existing `VERIFIED` `IMAGE` or
`DOCUMENT` assets with a valid lowercase SHA-256. The Fabric ledger remains
the source of truth; Spring does not store a synthetic registration flag.

### Registration evidence V1

`evidenceHash` is the SHA-256 of these UTF-8 lines, in this exact order, with a
single line-feed separator and no trailing line-feed:

```text
AUTHVAULT_REGISTRATION_EVIDENCE_V1
assetId=<public asset UUID>
assetType=<IMAGE or DOCUMENT>
sha256=<64 lowercase hexadecimal characters>
verificationStatus=VERIFIED
lastVerifiedAt=<ISO_LOCAL_DATE_TIME>
```

`creatorIdHash` is the lowercase SHA-256 of the UTF-8 value
`AUTHVAULT_CREATOR_ID_V1:<stable internal user UUID>`. The raw user UUID and
personal identity fields are not sent to Fabric or returned by this API.

### Optional live integration test

With the network running and the variables above still exported:

```bash
export AUTHVAULT_FABRIC_INTEGRATION_TEST_ENABLED=true
mvn -Dtest=FabricOriginalRegistryLiveIntegrationTest test
```

The test registers a new UUID-derived record, then exercises `GetOriginal`,
`FindBySha256`, and `GetAssetHistory`. It skips unless the opt-in variable is
exactly enabled and never resets or deletes ledger data.
