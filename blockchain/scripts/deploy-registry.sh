#!/usr/bin/env bash

set -euo pipefail
source "$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)/common.sh"

require_command peer
require_peer_cli_config
require_fabric_25
require_network_material
[[ -d "$CHAINCODE_DIR/vendor" ]] || die "vendored Go dependencies not found; run: cd blockchain/chaincode/registry && go mod download && go mod vendor"

CHAINCODE_VERSION="${CHAINCODE_VERSION:-1.0}"
CHAINCODE_SEQUENCE="${CHAINCODE_SEQUENCE:-1}"
CHAINCODE_LABEL="${CHAINCODE_LABEL:-registered-original-registry_${CHAINCODE_VERSION}}"
ENDORSEMENT_POLICY="AND('CreatorsOrgMSP.peer','AuthenticatorsOrgMSP.peer')"
PACKAGE_FILE="$GENERATED_DIR/$CHAINCODE_LABEL.tar.gz"

set_creators_peer
if committed_definition="$(peer lifecycle chaincode querycommitted --channelID "$CHANNEL_NAME" --name "$CHAINCODE_NAME" 2>/dev/null)" &&
  grep -Fq "Version: $CHAINCODE_VERSION, Sequence: $CHAINCODE_SEQUENCE" <<<"$committed_definition"; then
  printf '%s is already committed at version %s, sequence %s.\n' "$CHAINCODE_NAME" "$CHAINCODE_VERSION" "$CHAINCODE_SEQUENCE"
  exit 0
fi

mkdir -p "$GENERATED_DIR"
rm -f -- "$PACKAGE_FILE"
peer lifecycle chaincode package "$PACKAGE_FILE" \
  --path "$CHAINCODE_DIR" \
  --lang golang \
  --label "$CHAINCODE_LABEL"
PACKAGE_ID="$(peer lifecycle chaincode calculatepackageid "$PACKAGE_FILE")"

install_for_current_peer() {
  local organization="$1"
  if peer lifecycle chaincode queryinstalled 2>/dev/null | grep -Fq "$PACKAGE_ID"; then
    printf '%s already has package %s installed.\n' "$organization" "$PACKAGE_ID"
    return
  fi
  peer lifecycle chaincode install "$PACKAGE_FILE"
}

set_creators_peer
install_for_current_peer "CreatorsOrg"

set_authenticators_peer
install_for_current_peer "AuthenticatorsOrg"

orderer_arguments
approve_for_current_org() {
  peer lifecycle chaincode approveformyorg \
    "${ORDERER_ARGS[@]}" \
    --channelID "$CHANNEL_NAME" \
    --name "$CHAINCODE_NAME" \
    --version "$CHAINCODE_VERSION" \
    --package-id "$PACKAGE_ID" \
    --sequence "$CHAINCODE_SEQUENCE" \
    --signature-policy "$ENDORSEMENT_POLICY"
}

set_creators_peer
approve_for_current_org

set_authenticators_peer
approve_for_current_org

set_creators_peer
peer lifecycle chaincode checkcommitreadiness \
  --channelID "$CHANNEL_NAME" \
  --name "$CHAINCODE_NAME" \
  --version "$CHAINCODE_VERSION" \
  --sequence "$CHAINCODE_SEQUENCE" \
  --signature-policy "$ENDORSEMENT_POLICY" \
  --output json

both_peer_arguments
peer lifecycle chaincode commit \
  "${ORDERER_ARGS[@]}" \
  "${BOTH_PEER_ARGS[@]}" \
  --channelID "$CHANNEL_NAME" \
  --name "$CHAINCODE_NAME" \
  --version "$CHAINCODE_VERSION" \
  --sequence "$CHAINCODE_SEQUENCE" \
  --signature-policy "$ENDORSEMENT_POLICY"

peer lifecycle chaincode querycommitted \
  --channelID "$CHANNEL_NAME" \
  --name "$CHAINCODE_NAME"

printf 'Deployed %s with endorsement policy %s.\n' "$CHAINCODE_NAME" "$ENDORSEMENT_POLICY"
