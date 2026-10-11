# OrderFlow

**OrderFlow** is a Java 21 / Spring Boot order and inventory application. It demonstrates transactional stock reservation, idempotent order creation, optimistic locking, role-based API access, versioned database migrations, and PostgreSQL integration testing.

> **Render free-tier note:** Render’s free web service sleeps after 15 minutes without inbound traffic, so a first visit after a quiet spell can take about a minute to wake.

## Live demo

The deployment URL is added to this repository's GitHub page.

The demo opens without an account and starts with three sample products. Visitors can add products, adjust inventory, place orders, and cancel them. The playground is shared, clearly labeled, and isolated from the protected API's data. It resets after 20 minutes without demo activity; the **Reset demo** button resets it immediately for everyone.

## Features

- Product catalog with inventory management and request validation.
- Order lifecycle with atomic inventory reservation and cancellation/stock restoration.
- Idempotency keys with request fingerprinting to make safe retries possible.
- Optimistic locking and database constraints to protect concurrent updates.
- Stateless HTTP Basic authentication with `ADMIN` and `OPERATOR` roles for the private API.
- Anonymous public playground with demo/private data isolation and inactivity reset.
- Consistent API errors using RFC 9457 problem details.
- Flyway-managed PostgreSQL schema and Testcontainers-backed integration tests.
- Responsive browser console served by Spring Boot from the same origin as the API.

## Screenshot

<!-- Add your screenshot here after committing it, for example:
![OrderFlow dashboard](docs/images/orderflow-dashboard.png)
-->

## Technology

Java 21 · Spring Boot · Spring Security · Spring Data JPA / Hibernate · PostgreSQL · Flyway · JUnit · Testcontainers · Docker Compose

## Run locally

Requirements: Docker Desktop. Java 21 is only required if running the app directly through Maven; the Compose workflow builds and runs the Java application in Docker.

From the repository root:

```powershell
docker compose up --build -d
```

Open [http://localhost:8080](http://localhost:8080). Compose starts the application and PostgreSQL. The database is published on host port `5433` to avoid conflict with a local PostgreSQL installation; the app connects to it over the Compose network. Data persists in a named Docker volume.

Stop the services without deleting database data:

```powershell
docker compose down
```

To remove the local database volume as well (this permanently deletes local Compose data):

```powershell
docker compose down -v
```

### Run with VS Code and the Maven wrapper

From the repository root, start PostgreSQL:

```powershell
docker compose up -d db
```

Then run Spring Boot on the host:

```powershell
.\orderflow\mvnw.cmd spring-boot:run
```

## API overview

Protected API routes are under `/api/v1` and require HTTP Basic credentials configured in the environment. Product reads and order operations allow either role; product creation and inventory adjustment require `ADMIN`.

| Method | Path | Access | Purpose |
| --- | --- | --- | --- |
| GET | `/actuator/health` | Public | Health check |
| GET | `/api/v1/session` | Authenticated | Current username and role |
| GET | `/api/v1/products` | ADMIN / OPERATOR | List products |
| GET | `/api/v1/products/{id}` | ADMIN / OPERATOR | Get a product |
| POST | `/api/v1/products` | ADMIN | Create a product |
| PATCH | `/api/v1/products/{id}/inventory` | ADMIN | Set available stock |
| POST | `/api/v1/orders` | ADMIN / OPERATOR | Place an order; requires `Idempotency-Key` |
| GET | `/api/v1/orders` | ADMIN / OPERATOR | List orders |
| GET | `/api/v1/orders/{id}` | ADMIN / OPERATOR | Get an order |
| POST | `/api/v1/orders/{id}/cancel` | ADMIN / OPERATOR | Cancel and restore stock |

The public browser console uses `/api/v1/demo/**`. It provides demo status/reset plus product and order operations, but can only access demo-scoped records.

## Configuration

Set these as environment variables in deployment. Never commit production credentials.

| Variable | Purpose |
| --- | --- |
| `ORDERFLOW_DB_URL` | PostgreSQL JDBC URL |
| `ORDERFLOW_DB_USERNAME` / `ORDERFLOW_DB_PASSWORD` | Database credentials |
| `ORDERFLOW_ADMIN_USERNAME` / `ORDERFLOW_ADMIN_PASSWORD` | Protected ADMIN API credentials |
| `ORDERFLOW_OPERATOR_USERNAME` / `ORDERFLOW_OPERATOR_PASSWORD` | Protected OPERATOR API credentials |
| `ORDERFLOW_DEMO_IDLE_TTL` | Shared demo inactivity timeout; default `PT20M` |
| `PORT` | HTTP port supplied by the host; defaults to `8080` |

The demo is anonymous; do not put API credentials in the frontend. Use HTTPS and configure host/proxy rate limits for anonymous write requests. Free hosting may sleep while idle, so the first visit after a quiet period can take longer to load.

## Tests

With Docker Desktop running, from the repository root:

```powershell
.\orderflow\mvnw.cmd test
```

Testcontainers starts an isolated PostgreSQL 17 instance. The suite covers authorization, validation, idempotency and concurrent retries, inventory conflicts, rollback/cancellation, public demo isolation, and demo expiry/reseeding. It does not use the local Compose database.

## Architecture notes

- Flyway owns schema changes; Hibernate validates its mappings against the migrated schema.
- Controllers handle HTTP and DTO validation; services hold business rules; repositories provide persistence access.
- Order placement and cancellation are transactions. Product and order versions plus database uniqueness constraints protect concurrent changes.
- The public demo uses a `demo_mode` scope and a singleton PostgreSQL activity row to coordinate its shared idle reset.
- The frontend is static HTML/CSS/JavaScript served from the Spring Boot JAR; no separate Node server or CORS configuration is needed.
- Docker Compose runs the app and PostgreSQL locally; the Dockerfile builds with Maven/JDK 21 and runs on a smaller Java 21 runtime image.

## License

MIT. See [LICENSE](LICENSE).
