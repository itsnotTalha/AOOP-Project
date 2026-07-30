# AuthVault

AuthVault is a full-stack university capstone project for managing trusted digital identities and secure verification workflows. The repository contains a React frontend and a Spring Boot backend that work together around authenticated user access, JWT-based sessions, and the foundation for a broader vault, wallet, and marketplace platform.

## Project At A Glance

- Frontend: React 19, Vite, Tailwind CSS, React Router, React Hook Form, Axios
- Backend: Spring Boot 3.3.2, Spring Security, Spring Data JPA, Validation, JWT
- Database: SQLite via Hibernate community dialects
- Language: Java 21 on the backend, modern JavaScript on the frontend

## What AuthVault Is Building

The codebase is organized around a secure digital asset and identity platform. The current domain model already includes users, wallets, documents, digital assets, blockchain ledger records, ownership history, marketplace listings, notifications, and verification history. The application is structured so those features can grow into a unified platform for identity verification, secure storage, and asset management.

## Implemented So Far

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

## Current Status

The authentication flow is the most complete part of the project right now. The frontend can create accounts and sign in, and the backend returns JWT-based auth responses. The landing page and dashboard are still placeholders, so the product experience beyond auth is still under construction.

## Folder Structure

```text
client/   # React frontend
server/   # Spring Boot backend
```

## Running The Project Locally

### Backend

1. Open the `server/` directory.
2. Configure `application.properties` or `application.yml` with the database and JWT settings.
3. Run the app with Maven:

```bash
mvn spring-boot:run
```

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

## Notes

- The backend is already structured for additional modules such as assets, documents, wallets, marketplace, and blockchain history.
- The UI currently shows the app shell and auth flow, while product-specific dashboard features are still pending implementation.
