# AOOP Project — VaultChain / AuthVault

VaultChain is a decentralized and cryptographic digital asset management platform featuring a Spring Boot backend and a React + Vite frontend.

## Quick Start

You can use the startup helper scripts at the repository root:

### 1. Start the Backend (Spring Boot)

```bash
./start-backend.sh
```
Or directly:
```bash
cd AuthVault/server
mvn spring-boot:run
```
The backend API server will run at `http://localhost:3000/api`.

### 2. Start the Frontend (React + Vite)

In a separate terminal:
```bash
./start-frontend.sh
```
Or directly:
```bash
cd AuthVault/client
npm install
npm run dev
```
Open the frontend application in your browser at `http://localhost:5173`.

## Testing

- **Backend tests**:
  ```bash
  cd AuthVault/server
  mvn test
  ```
- **Frontend tests & build**:
  ```bash
  cd AuthVault/client
  npm test
  npm run build
  ```

## Features

- **Digital Asset Management**: Cryptographic SHA-256 fingerprinting, perceptual hashing (BlockHash), and tamper detection.
- **Document Management & OCR**: PDF & image uploads, OCR text extraction with handwritten HTR engine support, integrity verification, and metadata registry.
- **Enterprise & Organization Workspaces**: Switch between personal and organization workspaces, team contributor management, revenue splits, treasury deposit/withdrawal, and company vault isolation.
- **Marketplace & Negotiations**: Asset listing, anonymous posting, buyer preview requests with seller authorization, price negotiations, and cryptographic security receipts.
- **Vault Protection**: Password-protected vault storage with scoped unlocking.
- **Blockchain Ledger**: Immutable audit trail of asset registration and verification events.
- **Account Recovery**: Security questions and one-time recovery codes.
