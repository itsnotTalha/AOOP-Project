#!/usr/bin/env bash
set -e

if [ -d "AuthVault/server" ]; then
    cd AuthVault/server
elif [ -d "VaultChain/server" ]; then
    cd VaultChain/server
elif [ -d "server" ]; then
    cd server
else
    echo "Error: Cannot locate server directory."
    exit 1
fi

echo "Starting AuthVault Spring Boot Backend on http://localhost:3000..."
mvn spring-boot:run
