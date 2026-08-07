# AuthVault

AuthVault is a full-stack digital asset custody and verification platform. It is designed to let users create a secure identity, store and verify digital assets, monitor a wallet, and eventually manage ownership and marketplace activity from one protected workspace.

This repository is a university capstone project. Authentication and the dashboard foundation are functional; several advanced product areas are currently represented by database models and DTOs but still need services, API endpoints, and frontend pages.

## Project Status

The project is approximately **40% complete as an end-to-end MVP**.

| Area | Status | Notes |
| --- | --- | --- |
| Landing page | Implemented | Public product landing page and navigation |
| Registration | Implemented | Client/server validation, duplicate checks, password hashing, and wallet creation |
| Login and JWT authentication | Implemented | Stateless JWT sessions and protected frontend routes |
| User profile backend | Implemented | Read profile, update profile, and change password APIs |
| Dashboard UI | Implemented | Responsive application shell, metrics, activity, health, and quick-action views |
| Dashboard summary API | Implemented | Returns asset, verification, wallet, storage, and notification totals |
| SQLite persistence | Implemented | Hibernate-managed local database |
| Global API error handling | Implemented | Common success/error response structure and domain exceptions |
| Asset management | Scaffolded | Entity, repository, and DTOs exist; complete APIs and UI are pending |
| Document uploads | Scaffolded | Entity, repository, DTOs, and upload directories exist; workflow is pending |
| Secure vault | Scaffolded | Entity, repository, and DTOs exist; complete workflow is pending |
| Wallet transactions | Scaffolded | Entities, repositories, and response DTOs exist; transaction APIs/UI are pending |
| Verification history | Scaffolded | Persistence model exists; verification engine and UI are pending |
| Marketplace and ownership | Scaffolded | Models and DTOs exist; services, controllers, and UI are pending |
| Blockchain ledger | Scaffolded | Persistence model and response DTO exist; ledger workflow is pending |
| Notifications | Scaffolded | Persistence model exists; notification delivery and UI integration are pending |
| Automated tests | Pending | Test directories exist, but meaningful test coverage still needs to be added |

> The percentage is an engineering estimate based on the planned product areas already represented in the repository. Authentication is the most complete vertical feature.

## Technology Stack

### Frontend

- React 19
- Vite 5
- React Router 6
- Tailwind CSS 3
- Axios
- React Hook Form
- Lucide React icons

### Backend

- Java 21
- Spring Boot 3.3.2
- Spring Web
- Spring Security
- Spring Data JPA / Hibernate 6
- Jakarta Validation
- JSON Web Tokens using JJWT
- BCrypt password hashing
- SQLite
- Lombok
- Maven

## Repository Structure

```text
AOOP-Project/
├── client/                         # React frontend
│   ├── src/
│   │   ├── components/             # Shared UI components
│   │   ├── context/                # Authentication state
│   │   ├── hooks/                  # Shared React hooks
│   │   ├── layouts/                # Public, auth, and dashboard layouts
│   │   ├── pages/                  # Landing, login, register, and dashboard
│   │   ├── routes/                 # Route definitions and route protection
│   │   ├── services/               # Axios API and authentication services
│   │   ├── styles/                 # Global Tailwind styles
│   │   └── utils/                  # Token storage and constants
│   └── package.json
├── server/                         # Spring Boot backend
│   ├── database/                   # Database support files and schema
│   ├── uploads/                    # Planned uploaded-file storage
│   ├── src/main/java/com/verivault/
│   │   ├── config/                 # Web configuration
│   │   ├── controller/             # REST controllers
│   │   ├── dto/                    # API request and response objects
│   │   ├── entity/                 # JPA entities
│   │   ├── exception/              # API exceptions and global handler
│   │   ├── repository/             # Spring Data repositories
│   │   ├── security/               # JWT and Spring Security configuration
│   │   └── service/                # Business logic
│   ├── src/main/resources/         # Application configuration
│   └── pom.xml
└── README.md
```

## Prerequisites

Install the following before starting the project:

- **JDK 21** — a full JDK is required, not only a Java runtime
- **Maven 3.9+**
- **Node.js 18+** and npm
- Git

Verify your installation:

```bash
java -version
javac -version
mvn -version
node --version
npm --version
```

Both `java` and `javac` should report version 21.

## Getting Started

### 1. Clone and enter the repository

```bash
git clone <repository-url>
cd AOOP-Project
```

If you already have the project locally, open a terminal in its root directory.

### 2. Configure the backend

The backend configuration is in:

```text
server/src/main/resources/application.yml
```

The default development configuration uses:

```yaml
server:
  port: 8080

spring:
  datasource:
    url: jdbc:sqlite:authvault.db

jwt:
  secret: CHANGE_ME_TO_A_LONG_RANDOM_SECRET
  expiration: 86400000
```

Before deploying or sharing a public environment, replace the default JWT secret with a long, random secret. The included value is suitable only as a local placeholder.

### 3. Start the backend

Open a terminal from the repository root:

```bash
cd server
mvn spring-boot:run
```

The first run may take a few minutes while Maven downloads dependencies. A successful startup makes the API available at:

```text
http://localhost:8080
```

SQLite requires no separate database server. Hibernate creates or updates `server/authvault.db` automatically.

### 4. Start the frontend

Open a second terminal from the repository root:

```bash
cd client
npm install
npm run dev
```

Vite normally starts the application at:

```text
http://localhost:5173
```

Open that address in a browser, create an account, and sign in. Keep both the backend and frontend terminals running.

## Common Development Commands

### Frontend

Run the development server:

```bash
cd client
npm run dev
```

Create a production build:

```bash
npm run build
```

Preview the production build:

```bash
npm run preview
```

### Backend

Run the application:

```bash
cd server
mvn spring-boot:run
```

Compile and run tests:

```bash
mvn test
```

Build an executable JAR:

```bash
mvn clean package
```

Run the packaged application:

```bash
java -jar target/authvault-0.0.1-SNAPSHOT.jar
```

## API Overview

The frontend uses `http://localhost:8080/api` as its API base URL.

The complete implementation contract for Digital Asset Authentication, Document Verification, Secure Vault, Marketplace, Fractional Ownership, wallet support, and dashboard integration is available in [docs/API_SPECIFICATION.md](docs/API_SPECIFICATION.md). It includes endpoint ownership, schemas, security rules, database changes, dependencies, and the definition of done for each team. A detailed first-feature walkthrough is available in the [Image Authentication Implementation Guide](docs/IMAGE_AUTHENTICATION_IMPLEMENTATION_GUIDE.md).

### Public endpoints

| Method | Endpoint | Purpose |
| --- | --- | --- |
| `POST` | `/api/auth/register` | Create an account and associated wallet |
| `POST` | `/api/auth/login` | Authenticate by email or username and receive a JWT |

### Authenticated endpoints

These routes require an `Authorization: Bearer <token>` header.

| Method | Endpoint | Purpose |
| --- | --- | --- |
| `GET` | `/api/dashboard/summary` | Read dashboard totals for the current user |
| `GET` | `/api/users/me` | Read the authenticated user profile |
| `PUT` | `/api/users/me` | Update the authenticated user profile |
| `PUT` | `/api/users/me/password` | Change the authenticated user's password |

### Example registration request

```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "fullName": "Alex Morgan",
    "username": "alexmorgan",
    "email": "alex@example.com",
    "password": "password123",
    "confirmPassword": "password123",
    "phone": "+8801700000000"
  }'
```

### Example login request

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "emailOrUsername": "alex@example.com",
    "password": "password123"
  }'
```

## Authentication Flow

1. A user registers through the React form.
2. The backend validates the request, hashes the password with BCrypt, stores the user, and creates a wallet.
3. The user signs in with an email address or username.
4. The backend returns a JWT and user details.
5. The frontend stores the session token in browser storage.
6. The Axios request interceptor attaches the token to protected API calls.
7. Spring Security validates the JWT before allowing protected requests.
8. A `401 Unauthorized` response clears the local session and returns the user to authentication.

## Data Model Foundation

The backend currently contains JPA entities and repositories for:

- Users
- Digital assets
- Documents
- Secure vaults
- VeriWallet accounts
- Wallet transactions
- Verification history
- Blockchain ledger entries
- Ownership history
- Fractional ownership
- Marketplace listings
- Notifications

Having an entity and repository means the persistence layer has been started; it does not necessarily mean that a complete API or frontend workflow exists for that module.

## Current Limitations

- Dashboard summary values are live, but the recent-activity examples and some visual trend labels are currently presentation data.
- Dashboard navigation items other than Overview do not yet lead to completed feature pages.
- Quick-action buttons are visual placeholders until the asset, verification, wallet, and vault workflows are implemented.
- Uploaded-file handling is not complete.
- Marketplace, ownership transfer, fractional ownership, and ledger operations are not exposed as complete REST APIs.
- Notification delivery and real-time updates are not implemented.
- There is no meaningful automated test suite yet.
- The API base URL and CORS origin are configured for local development at ports `8080` and `5173`.
- The local JWT secret must be replaced before production use.

## Troubleshooting

### `release version 21 not supported`

Maven is using an older JDK. Install JDK 21 and update `JAVA_HOME` and `PATH`, then confirm:

```bash
java -version
javac -version
mvn -version
```

### Port 8080 is already in use

Stop the process using port 8080 or change `server.port` in `application.yml`. If you change the backend port, also update `baseURL` in `client/src/services/api.js`.

### Frontend cannot reach the backend

Confirm that:

- The Spring Boot process is running on port 8080.
- The frontend is running on port 5173.
- `http://localhost:8080/api` is the configured frontend API URL.
- CORS allows `http://localhost:5173`.

### SQLite database is locked

Stop duplicate backend processes. SQLite concurrency is intentionally limited to one Hikari connection in the development configuration.

### Start with a fresh local database

Stop the backend and make a backup of `server/authvault.db` before removing or replacing it. The database may contain local user accounts and other development data.

## Suggested Development Roadmap

1. Add automated tests for authentication, user profiles, and dashboard summaries.
2. Complete asset creation, listing, viewing, and deletion APIs.
3. Implement secure document upload and download handling.
4. Connect dashboard actions and navigation to real pages.
5. Implement verification workflows and verification history.
6. Add wallet transaction APIs and UI.
7. Complete notifications and activity feeds.
8. Implement marketplace and ownership-transfer flows.
9. Move secrets and environment-specific URLs to environment variables.
10. Add production deployment, monitoring, and database migration procedures.

## Security Notes

- Do not commit real JWT secrets, production credentials, or personal data.
- Use HTTPS in production.
- Keep uploaded files outside publicly served directories unless access is explicitly authorized.
- Replace development CORS rules with the exact production frontend origin.
- Back up the SQLite database before schema or data migrations.
- Consider a production database such as PostgreSQL when concurrency and deployment requirements grow.

## Contributing

Create a dedicated branch for each feature or fix:

```bash
git checkout -b feature/asset-management
```

Keep changes focused, verify both applications locally, and run the frontend build and backend tests before opening a pull request.

## License

No license has been added to this repository yet. Add a license before distributing or reusing the project outside its intended academic context.
