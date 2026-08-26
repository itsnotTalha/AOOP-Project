#!/usr/bin/env bash

set -euo pipefail
source "$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)/common.sh"

require_command peer
require_peer_cli_config
require_fabric_25
require_network_material
require_file "$CHANNEL_ARTIFACTS_DIR/$CHANNEL_NAME.tx"
orderer_arguments

CHANNEL_BLOCK="$CHANNEL_ARTIFACTS_DIR/$CHANNEL_NAME.block"
if [[ ! -f "$CHANNEL_BLOCK" ]]; then
  set_creators_peer
  for attempt in {1..20}; do
    if peer channel create \
      "${ORDERER_ARGS[@]}" \
      --channelID "$CHANNEL_NAME" \
      --file "$CHANNEL_ARTIFACTS_DIR/$CHANNEL_NAME.tx" \
      --outputBlock "$CHANNEL_BLOCK"; then
      break
    fi
    if [[ "$attempt" -eq 20 ]]; then
      die "channel creation failed after 20 attempts"
    fi
    sleep 2
  done
fi

join_peer_if_needed() {
  local organization="$1"
  if peer channel list 2>/dev/null | grep -Fxq "$CHANNEL_NAME"; then
    printf '%s peer is already joined to %s.\n' "$organization" "$CHANNEL_NAME"
    return
  fi
  peer channel join --blockpath "$CHANNEL_BLOCK"
}

set_creators_peer
join_peer_if_needed "CreatorsOrg"

set_authenticators_peer
join_peer_if_needed "AuthenticatorsOrg"

printf 'Both organization peers joined %s.\n' "$CHANNEL_NAME"
