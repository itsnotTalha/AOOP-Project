#!/usr/bin/env bash

set -euo pipefail
source "$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)/common.sh"

require_command docker
require_command peer
require_peer_cli_config
require_fabric_25
require_network_material

required_services=(
  orderer.vaultchain.local
  peer0.creators.vaultchain.local
  peer0.authenticators.vaultchain.local
  couchdb.creators.vaultchain.local
  couchdb.authenticators.vaultchain.local
)
running_services="$(compose ps --status running --services)"
for service in "${required_services[@]}"; do
  grep -Fxq "$service" <<<"$running_services" || die "required service is not running: $service"
done

set_creators_peer
peer lifecycle chaincode querycommitted \
  --channelID "$CHANNEL_NAME" \
  --name "$CHAINCODE_NAME" >/dev/null

ASSET_ID="11111111-1111-1111-1111-111111111111"
DUPLICATE_ASSET_ID="22222222-2222-2222-2222-222222222222"
CREATOR_ID_HASH="creator-test-hash"
SHA256="aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
EVIDENCE_HASH="bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"

query_chaincode() {
  peer chaincode query \
    --channelID "$CHANNEL_NAME" \
    --name "$CHAINCODE_NAME" \
    --ctor "$1"
}

orderer_arguments
both_peer_arguments
invoke_chaincode() {
  peer chaincode invoke \
    "${ORDERER_ARGS[@]}" \
    "${BOTH_PEER_ARGS[@]}" \
    --channelID "$CHANNEL_NAME" \
    --name "$CHAINCODE_NAME" \
    --waitForEvent \
    --ctor "$1"
}

exists="$(query_chaincode "{\"function\":\"OriginalExists\",\"Args\":[\"$ASSET_ID\"]}")"
if [[ "$exists" == "false" ]]; then
  invoke_chaincode "{\"function\":\"RegisterOriginal\",\"Args\":[\"$ASSET_ID\",\"$CREATOR_ID_HASH\",\"$SHA256\",\"IMAGE\",\"VERIFIED\",\"$EVIDENCE_HASH\"]}"
elif [[ "$exists" != "true" ]]; then
  die "unexpected OriginalExists response: $exists"
fi

by_id="$(query_chaincode "{\"function\":\"GetOriginal\",\"Args\":[\"$ASSET_ID\"]}")"
grep -Fq "\"assetId\":\"$ASSET_ID\"" <<<"$by_id" || die "GetOriginal did not return the seed asset"
grep -Fq "\"verificationStatus\":\"VERIFIED\"" <<<"$by_id" || die "GetOriginal did not return VERIFIED"

by_sha="$(query_chaincode "{\"function\":\"FindBySha256\",\"Args\":[\"$SHA256\"]}")"
grep -Fq "\"assetId\":\"$ASSET_ID\"" <<<"$by_sha" || die "FindBySha256 did not return the seed asset"

DUPLICATE_ERROR_FILE="$GENERATED_DIR/duplicate-sha-error.log"
if invoke_chaincode "{\"function\":\"RegisterOriginal\",\"Args\":[\"$DUPLICATE_ASSET_ID\",\"$CREATOR_ID_HASH\",\"$SHA256\",\"DOCUMENT\",\"VERIFIED\",\"$EVIDENCE_HASH\"]}" >"$DUPLICATE_ERROR_FILE" 2>&1; then
  die "duplicate SHA-256 registration unexpectedly succeeded"
fi
grep -Fq "already registered" "$DUPLICATE_ERROR_FILE" || die "duplicate SHA-256 failed without the expected registry error"
rm -f -- "$DUPLICATE_ERROR_FILE"

history="$(query_chaincode "{\"function\":\"GetAssetHistory\",\"Args\":[\"$ASSET_ID\"]}")"
grep -Fq "\"assetId\":\"$ASSET_ID\"" <<<"$history" || die "GetAssetHistory did not include the seed asset"

printf 'PASSED: register, ID lookup, SHA-256 lookup, duplicate rejection, and history query.\n'
