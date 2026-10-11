# OrderFlow

OrderFlow is a Java 21 / Spring Boot order and inventory application backed by PostgreSQL. It demonstrates transactional inventory reservation, idempotent order creation, optimistic locking, role-protected API routes, Flyway migrations, and PostgreSQL integration tests with Testcontainers.

The application includes a responsive, no-login operations console for portfolio visitors. It opens with three sample products and supports product creation, inventory changes, order checkout, and cancellation. The public playground is intentionally shared, clearly labeled, and isolated from the authenticated API's data. It returns to the sample catalog after 20 minutes without demo activity.

## Run the complete app locally

From the `Springboot` workspace folder, with Docker Desktop running:

```powershell
docker compose -f .\orderflow\compose.yaml up --build -d
```

Open [http://localhost:8080](http://localhost:8080). This starts both the Spring Boot app and PostgreSQL. PostgreSQL is available on host port `5433` to avoid conflicts with a Windows PostgreSQL installation; containers communicate over port `5432`. Flyway applies migrations on startup. The named volume preserves data across restarts.

Stop the app and database (without deleting database data):

```powershell
docker compose -f .\orderflow\compose.yaml down
```

For the VS Code / Maven workflow, start only PostgreSQL and run Spring Boot on the host:

```powershell
docker compose -f .\orderflow\compose.yaml up -d db
.\orderflow\orderflow\mvnw.cmd -f .\orderflow\orderflow\pom.xml spring-boot:run
```

The browser displays the shared-playground notice and inactivity countdown. Anyone using the deployment can see changes made by other visitors before the idle reset. The **Reset demo** button immediately restores the three sample products for everyone.

## Deployment configuration

Build and run the included Docker image with a managed PostgreSQL database. Configure these environment variables in the hosting platform; do not commit production secrets:

| Variable | Purpose |
| --- | --- |
| `ORDERFLOW_DB_URL` | JDBC URL for PostgreSQL, e.g. `jdbc:postgresql://host:5432/orderflow` |
| `ORDERFLOW_DB_USERNAME` / `ORDERFLOW_DB_PASSWORD` | Database credentials |
| `ORDERFLOW_ADMIN_USERNAME` / `ORDERFLOW_ADMIN_PASSWORD` | Credentials for the protected admin API |
| `ORDERFLOW_OPERATOR_USERNAME` / `ORDERFLOW_OPERATOR_PASSWORD` | Credentials for the protected operator API |
| `ORDERFLOW_DEMO_IDLE_TTL` | Shared demo idle timeout; default `PT20M` |
| `PORT` | HTTP port supplied by the hosting platform; defaults to `8080` |

Use the host's HTTPS endpoint. The public demo routes intentionally need no login; the ADMIN/OPERATOR routes remain HTTP Basic protected and should use unique secrets in deployment. The local Compose credentials are for development only.

## API overview

The authenticated API is under `/api/v1`. Product reads are available to either role; product creation and inventory adjustment require `ADMIN`. Order reads and lifecycle operations are available to either role.

| Method | Path | Access | Purpose |
| --- | --- | --- | --- |
| GET | `/actuator/health` | Public | Application health |
| GET | `/api/v1/session` | ADMIN / OPERATOR | Current authenticated username and role |
| POST | `/api/v1/products` | ADMIN | Create a product |
| GET | `/api/v1/products` | ADMIN / OPERATOR | List products |
| GET | `/api/v1/products/{id}` | ADMIN / OPERATOR | Get a product |
| PATCH | `/api/v1/products/{id}/inventory` | ADMIN | Set available inventory |
| POST | `/api/v1/orders` | ADMIN / OPERATOR | Create/reserve an order; requires `Idempotency-Key` |
| GET | `/api/v1/orders` | ADMIN / OPERATOR | List orders |
| GET | `/api/v1/orders/{id}` | ADMIN / OPERATOR | Get an order |
| POST | `/api/v1/orders/{id}/cancel` | ADMIN / OPERATOR | Cancel and restore stock |

The public console uses a separate `/api/v1/demo/**` route family. It supports `GET /status`, `POST /reset`, product list/create/inventory adjustment, and order list/create/cancel. These routes are anonymous but can only read or change records marked as demo data; the private API cannot see demo products or orders, and vice versa.

Order creation accepts product IDs and positive quantities. The server snapshots SKU, name, and price into each order item, computes totals, and reserves inventory in one transaction. Repeating an idempotency key with the same request returns the original order without reserving stock again; using it with a different request returns `409 Conflict`. Cancelling an order restores stock once.

## Tests

Run from the `Springboot` workspace folder with Docker Desktop running:

```powershell
.\orderflow\orderflow\mvnw.cmd -f .\orderflow\orderflow\pom.xml clean test
```

Testcontainers starts an isolated PostgreSQL 17 instance. The integration suite covers API authorization, demo/private data isolation, idle reset and reseeding, validation, idempotency (including concurrent retries), inventory conflicts, cancellation, and transaction rollback. It does not use or modify the local Compose database.

## Design notes

- Flyway owns schema changes; Hibernate validates mappings and does not generate schema at runtime.
- Product/order optimistic versions and transactional order placement protect inventory under concurrent requests.
- Public playground data is isolated with a database discriminator and scoped repository queries; SKU and idempotency uniqueness are scoped separately for demo and private data.
- A singleton PostgreSQL activity row serializes demo operations and coordinates the scheduled idle reset across application instances. The reset deletes demo order items, orders, and products in dependency order, then seeds the sample catalog.
- Request/response DTOs keep the API independent from JPA entities; centralized problem responses keep failures consistent.
- The frontend is static HTML/CSS/JavaScript served by Spring Boot from the same origin—no Node runtime, separate frontend host, or CORS setup is needed.
