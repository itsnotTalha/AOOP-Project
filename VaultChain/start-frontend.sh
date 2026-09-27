#!/usr/bin/env bash
set -e

if [ -d "VaultChain/client" ]; then
    cd VaultChain/client
elif [ -d "client" ]; then
    cd client
else
    echo "Error: Cannot locate client directory."
    exit 1
fi

echo "Starting VaultChain React Frontend on http://localhost:5173..."
if [ ! -d "node_modules" ]; then
    echo "Installing frontend dependencies..."
    npm install
fi

npm run dev
