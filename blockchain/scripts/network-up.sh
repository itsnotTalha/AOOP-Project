#!/usr/bin/env bash

set -euo pipefail
source "$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)/common.sh"

require_command docker
require_command cryptogen
require_command configtxgen
require_command peer
require_peer_cli_config
require_fabric_25
[[ "${FABRIC_IMAGE_TAG:-2.5.12}" =~ ^2\.5\.[0-9]+$ ]] || die "FABRIC_IMAGE_TAG must select a 2.5.x release"
docker compose version >/dev/null 2>&1 || die "Docker Compose v2 is required"

mkdir -p "$ORGANIZATIONS_DIR" "$CHANNEL_ARTIFACTS_DIR"

if [[ ! -f "$ORGANIZATIONS_DIR/peerOrganizations/creators.vaultchain.local/users/Admin@creators.vaultchain.local/msp/config.yaml" ]]; then
  if find "$ORGANIZATIONS_DIR" -mindepth 1 -print -quit | grep -q .; then
    die "generated crypto material is incomplete; run scripts/reset-network.sh"
  fi
  cryptogen generate --config="$NETWORK_DIR/crypto-config.yaml" --output="$ORGANIZATIONS_DIR"
fi

if [[ ! -f "$CHANNEL_ARTIFACTS_DIR/genesis.block" ]]; then
  (
    cd "$NETWORK_DIR"
    FABRIC_CFG_PATH="$NETWORK_DIR" configtxgen \
      -profile VaultChainOrdererGenesis \
      -channelID vaultchain-system-channel \
      -outputBlock "$CHANNEL_ARTIFACTS_DIR/genesis.block"
  )
fi

if [[ ! -f "$CHANNEL_ARTIFACTS_DIR/$CHANNEL_NAME.tx" ]]; then
  (
    cd "$NETWORK_DIR"
    FABRIC_CFG_PATH="$NETWORK_DIR" configtxgen \
      -profile VaultChainChannel \
      -channelID "$CHANNEL_NAME" \
      -outputCreateChannelTx "$CHANNEL_ARTIFACTS_DIR/$CHANNEL_NAME.tx"
  )
fi

compose config --quiet
compose up --detach

for attempt in {1..30}; do
  set_creators_peer
  if peer node status >/dev/null 2>&1; then
    break
  fi
  if [[ "$attempt" -eq 30 ]]; then
    compose ps
    die "CreatorsOrg peer did not become ready"
  fi
  sleep 2
done

"$SCRIPT_DIR/create-channel.sh"
printf 'VaultChain Fabric development network is running on channel %s.\n' "$CHANNEL_NAME"
