#!/usr/bin/env bash

set -euo pipefail
source "$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)/common.sh"

"$SCRIPT_DIR/network-down.sh"

[[ "$GENERATED_DIR" == "$BLOCKCHAIN_DIR/generated" ]] || die "refusing to reset unexpected path: $GENERATED_DIR"
rm -rf -- "$GENERATED_DIR"
mkdir -p "$GENERATED_DIR"
printf 'Removed generated crypto material, channel artifacts, and chaincode packages.\n'
