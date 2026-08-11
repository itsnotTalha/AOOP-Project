# Repository Instructions

- Preserve the existing architecture and naming conventions.
- The backend uses Java 21 and Spring Boot; the frontend uses React and Vite.
- Reuse existing DTO, entity, repository, security, and error-handling patterns before creating new abstractions.
- New module APIs must use the `/api/v1` prefix.
- Never expose database IDs; expose and accept asset UUIDs.
- Always resolve the current user from the authenticated JWT context.
- Never trust an original filename when constructing a storage path.
- Never load an entire uploaded file into memory solely to hash it; hash while streaming.
- Keep controllers thin and business logic in services.
- React pages must call service modules, never Axios directly.
- For the upload-integrity MVP, do not implement pHash, OCR, semantic hashing, AI authenticity detection, blockchain, marketplace, or vault functionality.
- Make minimal, focused changes and do not rewrite unrelated working code.
- After every implementation task, run the relevant tests and builds.
- Before finishing any task, check `docs/UPLOAD_INTEGRITY_MVP.md` for the current contract.
- Final reports must contain only: changed files, implementation summary, tests, and remaining issue.
