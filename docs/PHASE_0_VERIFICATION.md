# Phase 0 verification — 2026-11-02

## Final versions

Java toolchain 25 (installed 25.0.1); Spring Boot 4.1.2; Groovy Gradle wrapper 9.8.2; Node 26.13.0; Vite 7.3.7. Gradle's launcher used the machine's JDK 26 while compilation/tests target toolchain 25. Explicit user instructions supersede starter Java 21/Maven/Boot 3 defaults.

Latest stable Node was determined by fetching the [official distribution index](https://nodejs.org/dist/index.json), excluding prerelease versions (the first index entry was an alpha). Node 26.13.0 was downloaded to `/private/tmp`, verified against official SHASUMS256.txt, and used for the final frontend build. `.nvmrc` pins that release; no global runtime was modified. [Spring requirements](https://docs.spring.io/spring-boot/4.1/system-requirements.html) support Java 25 and Gradle 9.x.

## Commands and observed results

- `git status --short`: exit 128, `fatal: not a git repository`. No commit/checkpoint created.
- Initial sandbox Gradle invocation failed to initialize native services/file-lock sockets; subsequent authorized runs used `GRADLE_USER_HOME=/private/tmp/finance-gradle` and completed dependency downloads.
- `./gradlew wrapper --gradle-version 9.8.2 --no-daemon`: **BUILD SUCCESSFUL**, wrapper generated.
- `./gradlew clean test bootJar --no-daemon` on Spring Boot 4.1.2 / Gradle 9.8.2: compiled main and test code and produced the executable JAR; **6 tests completed, 2 failed**, overall **BUILD FAILED**. Four owner bootstrap unit tests passed. Both PostgreSQL integration tests failed during container initialization: `Could not find a valid Docker environment`; Docker Desktop strategy returned `503: Docker Desktop is manually paused`.
- `./gradlew test --tests '*OwnerBootstrapTest' bootJar --no-daemon`: **BUILD SUCCESSFUL**; four unit tests passed. Integration tests are intentionally excluded by this command, not verified.
- Clean frontend `npm ci` using Node 26.13.0: **88 packages added**, **0 vulnerabilities** reported by npm at that time. npm warned about unapproved esbuild/fsevents installation scripts; build still succeeded using the installed platform binaries.
- `npm run build` using Node 26.13.0: **passed** TypeScript and Vite production compilation, **86 modules transformed**, assets generated in `frontend/dist/`. Vite build reported 487ms; output JS about 300 kB before gzip.
- `DB_PASSWORD=compose-validation-only docker compose -f infrastructure/compose.yaml config --quiet`: exit 0, valid Compose configuration. This dummy value was used only for config parsing, never for a running database.
- `docker info`: Docker client installed (29.8.0); daemon reports **Docker Desktop is manually paused. Unpause it through the Whale menu or Dashboard.**

Earlier compilation errors in a Java regex and a moved test annotation were corrected before the final runs. The first frontend attempt picked up Node 16 from the shell and failed Vite's runtime requirement; a clean install and final build on the requested latest stable Node succeeded.

## Pending acceptance and blockers

PostgreSQL could not start because Docker Desktop is paused. Migration execution, actual backend launch/health, session cookies/CSRF over HTTP, browser sign-in/out, 360px browser accessibility/layout checks, and fresh-clone end-to-end startup remain **unverified**. Integration tests are provided and fail visibly when Docker is unavailable. Unpause Docker, then rerun `./gradlew test` and the README startup/API smoke commands before marking Phase 0 accepted.

UI was compiled, not visually inspected: responsive navigation uses three columns on small screens, a sidebar on desktop, keyboard focus indicators, semantic labels, and a skip link. Finance pages explicitly identify themselves as placeholders; no balances or fabricated records are displayed.

Only the owner table is migrated. Financial entities, financial calculations, payment posting, ownership checks on real financial records, and financial invariant tests belong to Phase 1. Password recovery/change, rate limiting, production TLS hosting, and backup/restore are outside this milestone and are not represented as complete.
