#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
BLOCKCHAIN_DIR="$(cd -- "$SCRIPT_DIR/.." && pwd)"
NETWORK_DIR="$BLOCKCHAIN_DIR/network"
GENERATED_DIR="$BLOCKCHAIN_DIR/generated"
ORGANIZATIONS_DIR="$GENERATED_DIR/organizations"
CHANNEL_ARTIFACTS_DIR="$GENERATED_DIR/channel-artifacts"
CHAINCODE_DIR="$BLOCKCHAIN_DIR/chaincode/registry"
COMPOSE_FILE="$NETWORK_DIR/compose.yaml"

CHANNEL_NAME="${CHANNEL_NAME:-vaultchain-channel}"
CHAINCODE_NAME="${CHAINCODE_NAME:-registered-original-registry}"
ORDERER_CA="$ORGANIZATIONS_DIR/ordererOrganizations/vaultchain.local/orderers/orderer.vaultchain.local/tls/ca.crt"
CREATORS_PEER_CA="$ORGANIZATIONS_DIR/peerOrganizations/creators.vaultchain.local/peers/peer0.creators.vaultchain.local/tls/ca.crt"
AUTHENTICATORS_PEER_CA="$ORGANIZATIONS_DIR/peerOrganizations/authenticators.vaultchain.local/peers/peer0.authenticators.vaultchain.local/tls/ca.crt"

die() {
  printf 'ERROR: %s\n' "$*" >&2
  exit 1
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || die "required command not found: $1"
}

require_file() {
  [[ -r "$1" ]] || die "required readable file not found: $1"
}

require_directory() {
  [[ -d "$1" ]] || die "required directory not found: $1"
}

require_peer_cli_config() {
  [[ -n "${FABRIC_CFG_PATH:-}" ]] || die "FABRIC_CFG_PATH must point to the Fabric 2.5 CLI config directory containing core.yaml"
  require_file "$FABRIC_CFG_PATH/core.yaml"
}

require_fabric_25() {
  local version_output
  version_output="$(peer version 2>&1)" || die "unable to read the Fabric peer CLI version"
  grep -Eq 'Version:[[:space:]]+v?2\.5\.' <<<"$version_output" || die "Hyperledger Fabric peer CLI 2.5.x is required"
}

compose() {
  docker compose -f "$COMPOSE_FILE" "$@"
}

set_creators_peer() {
  export CORE_PEER_LOCALMSPID="CreatorsOrgMSP"
  export CORE_PEER_TLS_ENABLED=true
  export CORE_PEER_ADDRESS="localhost:7051"
  export CORE_PEER_TLS_ROOTCERT_FILE="$CREATORS_PEER_CA"
  export CORE_PEER_MSPCONFIGPATH="$ORGANIZATIONS_DIR/peerOrganizations/creators.vaultchain.local/users/Admin@creators.vaultchain.local/msp"
}

set_authenticators_peer() {
  export CORE_PEER_LOCALMSPID="AuthenticatorsOrgMSP"
  export CORE_PEER_TLS_ENABLED=true
  export CORE_PEER_ADDRESS="localhost:9051"
  export CORE_PEER_TLS_ROOTCERT_FILE="$AUTHENTICATORS_PEER_CA"
  export CORE_PEER_MSPCONFIGPATH="$ORGANIZATIONS_DIR/peerOrganizations/authenticators.vaultchain.local/users/Admin@authenticators.vaultchain.local/msp"
}

require_network_material() {
  require_directory "$ORGANIZATIONS_DIR"
  require_file "$ORDERER_CA"
  require_file "$CREATORS_PEER_CA"
  require_file "$AUTHENTICATORS_PEER_CA"
}

orderer_arguments() {
  ORDERER_ARGS=(
    --orderer localhost:7050
    --tls
    --cafile "$ORDERER_CA"
    --ordererTLSHostnameOverride orderer.vaultchain.local
  )
}

both_peer_arguments() {
  BOTH_PEER_ARGS=(
    --peerAddresses localhost:7051
    --tlsRootCertFiles "$CREATORS_PEER_CA"
    --peerAddresses localhost:9051
    --tlsRootCertFiles "$AUTHENTICATORS_PEER_CA"
  )
}
