# VaultChain

VaultChain contains a Spring Boot backend at the project root and a React frontend in `client/`. The legacy Node backend is no longer part of this checkout.

## Requirements

- Java 21
- Maven
- SQLite database file (created automatically when absent)
- Optional OCR tools: Tesseract with English language data and Poppler (`pdfinfo`, `pdftotext`, `pdftoppm`)

## Run

```bash
cd VaultChain
export JWT_SECRET='replace-with-a-long-secret'
export DATABASE_PATH=./data/vaultchain.sqlite
export UPLOAD_DIRECTORY=./data/uploads
export DOCUMENT_UPLOAD_DIRECTORY=./data/documents
export PORT=3000
mvn clean install
java -jar target/spring-server-0.1.0-SNAPSHOT.jar
```

The API is available at `http://localhost:3000/api`. The default database path is `./data/vaultchain.sqlite`; the SQLite schema initializer runs before JPA starts. Do not point `DATABASE_PATH` at a production legacy file without first making a backup. `spring.jpa.hibernate.ddl-auto=none` preserves the initialized schema.

The API uses Spring Security with Bearer JWTs. Asset and document files are stored under `UPLOAD_DIRECTORY` and `DOCUMENT_UPLOAD_DIRECTORY`. Multipart uploads are limited to 20 MB. Printed image and scanned PDF OCR need the optional OCR tools listed above; OCR failures are persisted and can be retried through the document API.

## React frontend

In another terminal, from `VaultChain`:

```bash
npm ci
npm run dev
```

Vite serves the React app at its displayed local address (normally `http://localhost:5173`). The frontend uses `VITE_API_URL` when set and otherwise calls `http://localhost:3000/api`, matching the Spring port above. For a different backend port, set `VITE_API_URL=http://localhost:<port>/api` before starting Vite.

## Test

```bash
mvn clean test
mvn clean install
npm run build
```

The 50 source API routes are mapped to Spring controllers. See [the route tracker](docs/ROUTE_MIGRATION_TRACKER.md) and [the migration report](SPRING_BOOT_MIGRATION.md) for parity status and known limitations.
