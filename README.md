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

## Deployment

The app is a server-rendered monolith: one Spring Boot process renders the HTML
and handles the cart and orders. It deploys as a single service.

| Concern | Service | How |
|---|---|---|
| Application | **Render** | Docker web service, declared in `render.yaml` |
| Database | **Render Postgres** | managed, declared in the same blueprint |
| Email | **Resend** | HTTP API, `resend` profile |

There is deliberately **no Vercel deployment**. Vercel hosts static sites and JS
serverless functions; it cannot run Spring Boot, and this app has no separate
frontend to put there. Splitting the Thymeleaf pages into a Next.js app talking
to a REST API would change that, but it is a rewrite, not a config change.

### 1. Database

`render.yaml` declares the Postgres instance alongside the web service, and
wires every connection value into the app with `fromDatabase`. There is nothing
to copy by hand and no password in the repository. Flyway creates the schema on
first boot from `db/migration/postgresql`.

Both run in the same region, so they talk over Render's private network;
`ipAllowList: []` keeps the database off the public internet entirely.

> **The free Postgres plan expires after 30 days.** Render deletes free database
> instances at that point and the data goes with them. Fine for a demo; move to
> a paid instance before this holds anything you care about.

The `postgres` profile is provider-neutral — it is only a set of environment
variables, so the same build runs against Supabase, Neon or a local server by
pointing `DB_HOST` elsewhere. Supabase specifically needs `DB_SSL_MODE=require`.

### 2. Resend

Verify a sending domain, then create an API key at
<https://resend.com/api-keys>. `MAIL_FROM` must be an address on that verified
domain or every send is rejected.

Email is sent over Resend's **HTTP API**, not SMTP — outbound SMTP is commonly
blocked on hosting platforms, and a blocked port 587 only surfaces as a timeout
at send time. `application-resend.properties` documents the SMTP alternative if
you prefer it.

Two emails are sent: a welcome on registration and a confirmation when an order
is placed. Signing out sends nothing — there is no email involved in ending a
session.

### 3. Render

Point Render at this repository and choose **Blueprint**; it reads
`render.yaml` and creates both the database and the web service.

**One value is not in the file**, because it is a secret:

| Variable | Where it comes from |
|---|---|
| `RESEND_API_KEY` | <https://resend.com/api-keys> |

Everything else is handled: the database credentials come from the managed
instance via `fromDatabase`, `SPRING_PROFILES_ACTIVE` is set to
`postgres,resend`, Render injects `PORT` and the container binds it, and
`/actuator/health` gates the deploy going live.

A successful first boot logs:

```
Migrating schema "public" to version "1 - initial schema"
Successfully applied 1 migration to schema "public", now at version v1
Started ShoppingWebappApplication
```

If the entities and the migration ever disagree, `validate` fails startup with
the offending column named — the deploy stops rather than running against a
schema that does not match.

**Demo data never reaches production.** The seeder that creates
`demo@solarupgrade.example` is gated on `app.seed-demo-data`, which is true only
in the default in-memory configuration and false in every profile that points at
a real database.

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

**Registration requires a working mailbox.** A new account is created disabled
and cannot sign in until the six-digit code emailed to it is entered. Format
validation alone is worthless here — `nobody@madeupdomain.test` is perfectly
well-formed — so the address is only trusted once something has actually
arrived at it.

A code rather than a link, because a link travels badly: it wraps in plain-text
mail, gets rewritten by some clients, and cannot be carried from a phone to a
laptop. The cost is that six digits is only a million possibilities, so:

- wrong guesses are capped at `User.MAX_VERIFICATION_ATTEMPTS`, after which the
  code is burned and a new one must be requested
- codes expire in 15 minutes rather than a day
- the code is checked against one named account, never matched across all of
  them — six digits is not unique enough to identify a user on its own
- comparison is constant-time, so it leaks no per-digit timing signal

Wrong, expired and exhausted codes all produce the same message, and the
"resend" form always reports success whether or not the address is registered.
Both are deliberate: either one would let an anonymous visitor enumerate which
email addresses have accounts.

Deployments must set `APP_BASE_URL`, which the email uses to tell people where
to enter the code.

**The catalogue is reference data, the demo account is not.** Products ship as a
Flyway migration so every database including production has something to sell.
The demo account is seeded separately behind `app.seed-demo-data`, because its
password is published in this README and must never reach a real database.

## Running against a real database locally

Both database profiles read every value from the environment — no credentials
are committed.

**PostgreSQL:**

```bash
export DB_HOST=localhost DB_NAME=solarupgrade
export DB_USERNAME=... DB_PASSWORD=...
SPRING_PROFILES_ACTIVE=postgres ./gradlew bootRun
```

`DB_SSL_MODE` defaults to `prefer`, which negotiates TLS when the server offers
it and connects anyway when it does not — so this works against a local server
and Render's private network alike. Set `require` when connecting across the
public internet.

**MySQL:**

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
  h2/V1__initial_schema.sql          <- local development and tests
  postgresql/V1__initial_schema.sql  <- the postgres profile (Render, Supabase)
  mysql/V1__initial_schema.sql       <- the mysql profile
```

One file per dialect because they genuinely disagree: H2 and PostgreSQL want
`TIMESTAMP(6) WITH TIME ZONE` and `NUMERIC`, where MySQL wants `DATETIME(6)` and
`DECIMAL`, and the identity syntax differs again. All three were generated from
the entities using Hibernate's own schema export, not written by hand, so the
column types match what `validate` expects.

**To change the schema**, add a new numbered file to *every* directory — never
edit an applied migration, since Flyway checksums them and will refuse to run:

```
V2__add_product_sku.sql
```

Then run `./gradlew build`. If the change doesn't match the entities, `validate`
fails and tells you which column is wrong.

All three baselines have been run for real, not just written: H2 on every test,
MySQL against 8.0.46, and PostgreSQL against 16.13. In each case Flyway applied
the migration, `validate` accepted every column, and a full
registration-to-checkout flow persisted correctly with exact `NUMERIC`/`DECIMAL`
money and full timestamp precision.

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
