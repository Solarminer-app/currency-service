# Agent work log

## 2026-10-04 — Standalone repository migration

- Migrated the current Currency Service source, tests, Flyway migrations and
  public deployment documentation from `Solar-Miner-Node/currency-rates` into
  the dedicated `currency-service` repository.
- Added an independent Java 21 Gradle build, repository-owned version and image
  metadata, a complete wrapper, Flyway runtime support, OCI build configuration
  and Dockerfile.
- Added pull-request verification and tag-driven amd64/arm64 Docker publishing.
  Stable versions update `latest`; prerelease versions update `beta`.
- Runtime endpoints, the public hostname and the Docker image repository remain
  unchanged. Production deployment and MariaDB restart persistence remain
  rollout gates.
- Verification: all 16 tests pass under GraalVM Java 21; `clean check bootJar`,
  `printVersion` and `printDockerImage` pass. The executable JAR starts with an
  H2 database, applies Flyway V1 and V2, returns `[]` from the empty
  `mining-networks` endpoint and serves OpenAPI. Container construction was not
  executed because the local Docker socket is unavailable to this user.
