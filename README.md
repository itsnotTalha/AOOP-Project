# AuthVault

AuthVault is a full-stack university capstone project for managing trusted digital identities and secure verification workflows. The repository contains a React frontend and a Spring Boot backend that work together around authenticated user access, JWT-based sessions, and the foundation for a broader vault, wallet, and marketplace platform.

## Start Here On A New Branch

If you are picking up a new branch, start by reading this README first. It explains what the project does, what is already implemented, and where the unfinished parts are. A good next step is to run both apps locally, then inspect the authentication flow before adding new features.

Suggested workflow:

1. Read the project overview and implementation status below.
2. Check the frontend and backend folders to understand the feature boundaries.
3. Run the app locally and verify the current login and registration flow.
4. Build new work on a fresh branch so changes stay isolated from the main line.

## Project At A Glance

- Frontend: React 19, Vite, Tailwind CSS, React Router, React Hook Form, Axios
- Backend: Spring Boot 3.3.2, Spring Security, Spring Data JPA, Validation, JWT
- Database: SQLite via Hibernate community dialects
- Language: Java 21 on the backend, modern JavaScript on the frontend

## What The Project Is Building

The codebase is organized around a secure digital asset and identity platform. The current domain model already includes users, wallets, documents, digital assets, blockchain ledger records, ownership history, marketplace listings, notifications, and verification history. The application is structured so those features can grow into a unified platform for identity verification, secure storage, and asset management.

## What Is Already Implemented

### Frontend

- Public landing page, authentication layout, and dashboard layout scaffolding
- Login and registration pages with form validation and server error handling
- Auth context and token persistence for client-side session handling
- Protected dashboard route that redirects unauthenticated users away from private pages
- Shared UI primitives such as form fields and submit buttons

### Backend

- Authentication API endpoints for register and login at `/api/auth/register` and `/api/auth/login`
- JWT token generation and validation
- Spring Security configuration with stateless auth and CORS enabled for the Vite frontend
- User registration flow that creates a new user and an associated wallet record
- Custom exception types and a global exception handler foundation
- Initial entity, repository, DTO, and service structure for the wider AuthVault domain

## What Is Still In Progress

- The landing page and dashboard are still mostly placeholders
- The product experience beyond authentication is not yet complete
- Several domain areas are scaffolded in the backend but not fully exposed through the UI yet

## Current Status

The authentication flow is the most complete part of the project right now. The frontend can create accounts and sign in, and the backend returns JWT-based auth responses. The rest of the platform is set up structurally, but the user-facing features still need to be built out.

## Folder Structure

```text
client/   # React frontend
server/   # Spring Boot backend
```

## Running The Project Locally

### Prerequisites

- Install a full JDK 21, not just a Java runtime, so `javac` is available for Maven.
- Confirm `java -version` and `javac -version` both report Java 21 before running the backend.

### Backend

1. Open the `server/` directory.
2. Configure `application.properties` or `application.yml` with the database and JWT settings.
3. Run the app with Maven:

```bash
mvn spring-boot:run
```

If Maven reports `release version 21 not supported`, update `JAVA_HOME` and `PATH` to point to a JDK 21 installation.

### Frontend

1. Open the `client/` directory.
2. Install dependencies:

```bash
npm install
```

3. Start the development server:

```bash
npm run dev
```

## API Overview

- `POST /api/auth/register` creates a new user account and wallet
- `POST /api/auth/login` authenticates an existing user and returns a JWT auth payload

## Notes For Contributors

- The backend is already structured for additional modules such as assets, documents, wallets, marketplace, and blockchain history.
- The UI currently shows the app shell and auth flow, while product-specific dashboard features are still pending implementation.
- When starting work on a branch, prefer to keep changes focused on one feature area so the current auth foundation stays easy to understand.
