# SolarUpgrade

A Spring Boot storefront for domestic solar equipment — panels, inverters, battery
storage, mounting, EV charging and monitoring. Server-rendered with Thymeleaf.

## Quick start

```bash
./gradlew bootRun
```

Then open <http://localhost:8080>. No database setup is needed: the default
profile uses an in-memory H2 database, seeded with a demo catalogue and one
account.

| | |
|---|---|
| Demo login | `demo@solarupgrade.example` |
| Demo password | `password123` |
| H2 console | <http://localhost:8080/h2-console> (JDBC URL `jdbc:h2:mem:solarupgrade`) |

Run the tests with `./gradlew build`.

## History: this codebase was rebuilt

The original application source was lost. `src/main` had been committed as a
**gitlink** — a submodule pointer to commit `a290b666`, recorded because the
directory contained its own nested `.git` when it was staged. No `.gitmodules`
ever existed, and that commit was never pushed anywhere, so the pointer resolved
to nothing. Every controller, entity, template and property file was unreachable,
and `./gradlew bootJar` failed with `Main class name has not been configured`.

Recovery was attempted and ruled out: no unreachable objects in the repository,
a complete (non-shallow) history of only two commits, one branch on the server,
and no releases, tags, pull requests or forks holding a copy.

What survived was enough to infer the original design, and this codebase is
rebuilt from it:

- the dependency set in `build.gradle` (Thymeleaf, JPA, MySQL, Mail, Security,
  Validation, Actuator, and a commented-out PayPal SDK)
- the package and application class name, from the one surviving test
- the pages named in the final commit message: checkout, order summary, orders

**The domain model here is a reconstruction, not the original.** Entity fields,
page layouts and business rules were written fresh to match that outline. Treat
them as a starting point to edit, not as recovered code.

## Design notes

**Prices are `BigDecimal`,** never floating point, so totals stay exact.

**Order lines snapshot the product name and price** at purchase time
(`OrderItem.unitPrice`). Repricing or renaming a catalogue item never rewrites
the value of an order already placed — `OrderTest` covers this.

**The basket is persisted per user** rather than held in the HTTP session, so it
survives sign-out. Adding a product already in the basket increases the quantity
instead of creating a second line; a unique constraint on `(user_id, product_id)`
enforces that at the database level.

**Ownership is checked on every basket and order read.** `CartService` and
`OrderService` scope lookups by user, so changing an id in the URL cannot reach
someone else's data. Both paths have tests.

**Payment is not integrated.** Orders are created as `PENDING_PAYMENT`, and
`OrderService.markPaid` is a clearly marked stand-in — the "Pay now" button flips
the status without contacting a provider. A PayPal Orders API integration
replaces that method body and the form in `order-summary.html`, flipping the
status only once a capture is confirmed.

**Email is optional.** Spring Boot only creates a `JavaMailSender` when
`spring.mail.host` is set, so `EmailService` injects it lazily and logs instead
of sending when mail is unconfigured. A send failure is caught rather than
thrown, so it can never roll back an order that was already written.

## Running against MySQL

The `mysql` profile reads every value from the environment — no credentials are
committed:

```bash
export DB_HOST=localhost DB_NAME=solarupgrade
export DB_USERNAME=... DB_PASSWORD=...
SPRING_PROFILES_ACTIVE=mysql ./gradlew bootRun
```

Create an empty database and grant the user rights to it; Flyway builds the
schema on first start, so there is nothing else to set up by hand.

The connection uses `sslMode=PREFERRED`, the driver's own default. Do not
replace it with the older `useSSL=false`: MySQL 8 authenticates with
`caching_sha2_password`, which over an unencrypted connection needs the server's
public key, and requesting that key is refused by default — the connection then
fails with `Public Key Retrieval is not allowed`. Set `DB_SSL_MODE=REQUIRED` to
insist on TLS in production. `DISABLED` only works against a server whose
account uses `mysql_native_password`, and gives up transport encryption.

## Database schema

Flyway owns the schema. Hibernate runs with `ddl-auto=validate` on every profile
and never creates or alters a table — it only checks that the entities still
match what the migrations produced. If the two drift apart, startup fails, which
means **the test suite fails**: drift is caught in CI rather than on deploy.

Migrations are per-dialect, selected by the `{vendor}` placeholder in
`spring.flyway.locations`:

```
src/main/resources/db/migration/
  h2/V1__initial_schema.sql      <- local development and tests
  mysql/V1__initial_schema.sql   <- the mysql profile
```

Two files rather than one because the dialects genuinely disagree: H2 wants
`TIMESTAMP(6) WITH TIME ZONE` and `NUMERIC` where MySQL wants `DATETIME(6)` and
`DECIMAL`, and the identity syntax differs. Both were generated from the
entities using Hibernate's own schema export, not written by hand, so the column
types match what `validate` expects.

**To change the schema**, add a new numbered file to *both* directories — never
edit an applied migration, since Flyway checksums them and will refuse to run:

```
V2__add_product_sku.sql
```

Then run `./gradlew build`. If the change doesn't match the entities, `validate`
fails and tells you which column is wrong.

Both baselines have been run for real: the H2 one on every test, and the MySQL
one against MySQL 8.0.46, where Flyway applied it, `validate` accepted every
column, and a full registration-to-checkout flow persisted correctly
(`datetime(6)` precision and `decimal(10,2)` money both intact).

## Layout

```
src/main/java/com/shoppingapp/shoppingwebapp/
  config/      SecurityConfig, DataSeeder
  controller/  product, cart, checkout, order and auth controllers
  dto/         validated form backing objects
  model/       Product, User, CartItem, Order, OrderItem and enums
  repository/  Spring Data JPA repositories
  service/     ProductService, CartService, OrderService, UserService, EmailService
src/main/resources/
  db/migration/ Flyway migrations, one directory per dialect
  templates/    Thymeleaf pages; fragments/layout.html holds the shared header
  static/css/   stylesheet
```

## Known gaps

These are deliberate and worth picking up next:

- **No payment provider**, as described above.
- **No admin UI.** The `ADMIN` role exists and is assignable, but nothing uses
  it; products can only be changed through the seeder or directly in the
  database.
- **No stock reservation.** Stock is checked and decremented when the order is
  placed. Two shoppers racing for the last unit are resolved by whoever commits
  first; the loser gets an error at checkout rather than at add-to-basket.
- **Java 21, not 17.** The original build pinned a Java 17 toolchain. Since the
  code is new, this targets the current LTS instead. `settings.gradle` includes
  the foojay resolver so Gradle can provision a JDK when the machine lacks one.
