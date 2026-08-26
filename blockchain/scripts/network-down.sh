#!/usr/bin/env bash

set -euo pipefail
source "$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)/common.sh"

require_command docker
docker compose version >/dev/null 2>&1 || die "Docker Compose v2 is required"
compose down --volumes --remove-orphans
printf 'VaultChain Fabric development network stopped.\n'
