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
| Demo password | `sunny-rooftop-42` |
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

Four emails are sent:

| When | Email |
|---|---|
| Registration | the six-digit verification code |
| Order placed | what was ordered, still awaiting payment |
| Payment confirmed | the receipt — what was bought, what was paid, where it is going |
| 24h unpaid | one reminder, never a second |

Signing out sends nothing — there is no email involved in ending a session.

**The receipt is welded to the status transition.** `OrderService.markPaid` is
the single place an order becomes `PAID`, whichever route the news arrived by,
and it sends the receipt there. Emailing from each caller instead is how a
customer ends up with two receipts when a webhook lands just after they refresh
the page. Both are guarded by the transition itself: an order already paid
returns untouched and sends nothing.

### 3. PayPal

Create an app at <https://developer.paypal.com/dashboard/applications/sandbox>
(toggle to **Live** for the real one) and copy its **Client ID** and **Secret**.

Then create a webhook on the same app pointing at:

```
<APP_BASE_URL>/payments/paypal/webhook
```

subscribed to **PAYMENT.CAPTURE.COMPLETED**, and copy the **webhook id** it is
given. Four variables:

| Variable | Value |
|---|---|
| `PAYPAL_CLIENT_ID` | from the app |
| `PAYPAL_CLIENT_SECRET` | from the app |
| `PAYPAL_WEBHOOK_ID` | from the webhook |
| `PAYPAL_ENV` | exactly `sandbox` or `live` |

`PAYPAL_ENV` is validated at startup and anything else stops the app with the
offending value named. Guessing sandbox for an unrecognised value would be
friendlier and much worse: one typo on a production deployment would send real
customers to the test PayPal, where no money moves and no order ever settles,
and nothing would say so.

Leave them unset and the app still runs: `PayPalClient` is never registered, the
method is reported not-live, and the order page shows an explanation in place of
a pay button — nothing on this site can settle an order by itself. Set the first
three but not the webhook id and payments still work
through the return-and-capture path — the webhook endpoint just ignores
everything, because without the id it cannot tell a genuine notification from a
forged one.

`APP_BASE_URL` must be the real public address, since it is what the return,
cancel and webhook URLs are built from.

### 4. Render

Point Render at this repository and choose **Blueprint**; it reads
`render.yaml` and creates both the database and the web service.

**One value is not in the file**, because it is a secret:

| Variable | Where it comes from |
|---|---|
| `RESEND_API_KEY` | <https://resend.com/api-keys> |
| `PAYPAL_CLIENT_ID` / `PAYPAL_CLIENT_SECRET` / `PAYPAL_WEBHOOK_ID` | see above |

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

## Security notes

**Rate limits, because there were none.** `/login`, `/register`, `/verify` and
`/resend-verification` were unlimited, so a password could be guessed as fast as
the network allowed and a resend loop could drain the mail quota — which costs
real money and can get the sending domain marked as a spam source. Only POSTs
are limited; reading the sign-in page costs nothing.

| Endpoint | Allowance |
|---|---|
| `POST /resend-verification` | 3 per hour (each one sends an email) |
| `POST /register` | 5 per hour |
| `POST /verify` | 15 per 15 minutes |
| `POST /login` | 10 per 15 minutes |

**The limiter's own limits, stated rather than hidden.** The counters are in
memory, so they reset on restart and are per-instance — running two copies of
the app doubles every limit. A fixed window also lets a caller spend one
window's allowance at its end and again at the start of the next. Both are
acceptable at this size; neither is acceptable forever, and Redis is where this
goes when there is more than one instance.

**Callers are identified by `getRemoteAddr()`, never by reading
`X-Forwarded-For` directly.** That header is trivially forged, so parsing it
here would let an attacker mint a fresh identity per request and defeat the
filter entirely. Instead `server.forward-headers-strategy=framework` makes the
container resolve the real client from the proxy it trusts. The two settings
only work together — drop the property and every request behind Render's proxy
looks like one client.

**Account lockout is the second layer, following the account rather than the
caller,** so moving between addresses buys no fresh allowance. Five wrong
passwords, then a fifteen-minute cooldown, implemented as Spring Security's own
`accountLocked` flag so it is enforced before the password is compared.

Anything that locks an account on failed attempts also hands a stranger a way
to lock someone out on demand by guessing their address. That is why the lock
expires on its own rather than needing support, and why a correct password
clears the count immediately. Only wrong passwords count: an unverified account
is refused for an unrelated reason, and counting it would let a new customer who
had not checked their inbox lock themselves out by trying twice.

The locked page says sign-in is paused, not "too many wrong passwords for this
account" — the second phrasing would confirm to a stranger that an address they
guessed has an account here.

**Session cookie:** `Secure` (on the profiles that run behind TLS), `HttpOnly`
and `SameSite=Lax`. Lax rather than Strict because the return from PayPal is a
top-level cross-site navigation, and Strict would drop the session on the way
back.

**HSTS and a Content-Security-Policy** with no `unsafe-inline` — the concession
that usually makes a CSP decorative. It costs nothing here because the six
inline `style` attributes that existed were replaced with classes rather than
allowing inline styles for their sake. HSTS is only emitted on requests the
container considers secure, which is the other reason
`forward-headers-strategy` matters.

**Passwords: length and a blocklist, not composition rules.** Ten characters
minimum, a refusal list of the most-guessed choices, and no password that
contains the email address it protects. Demanding a capital, a digit and a
symbol reliably produces `Password1!` — it pushes people into the corner of the
keyspace attackers try first, and towards writing the result down. This follows
NIST SP 800-63B instead: insist on length, refuse known-bad, otherwise stay out
of the way. The blocklist is short on purpose; a real one is a file of several
thousand breached passwords and swapping it in changes one constant.

### Verified by running it

- `Content-Security-Policy`, `X-Frame-Options`, `X-Content-Type-Options` present
  on every response; `Strict-Transport-Security` appears as soon as a request
  carries `X-Forwarded-Proto: https`, proving the forward-headers setting works
- `Set-Cookie: JSESSIONID=...; Secure; HttpOnly; SameSite=Lax`
- Chromium walked every page signed in with the CSP active: **no violations**,
  and the JavaScript-driven features (password reveal, AJAX add-to-basket,
  payment-method disclosure) all still work — a CSP that silently breaks the
  site is worse than none
- `POST /resend-verification` five times: `302, 302, 302, 429, 429`
- `POST /login` until refused, with `Retry-After: 896`

### Still open

- **The database password was pasted into a chat transcript and should be
  rotated.** No configuration change matters more than this one.
- **The app connects as `neondb_owner`,** which can drop tables. A restricted
  role with only `SELECT/INSERT/UPDATE/DELETE` would mean a stolen app
  credential could not destroy the schema.
- **`ipAllowList: []` in `render.yaml` is dead config** left from when the plan
  was Render Postgres. Neon's free tier has no IP allowlist; TLS is what
  protects the connection.
- **Backups are unverified.** An untested restore is a hope, not a backup.
- No 2FA, no audit log, and the `ADMIN` role still does nothing.

## Design notes

**Prices are `BigDecimal`,** never floating point, so totals stay exact.

**The base currency is the naira.** The catalogue is priced in it, totals are
computed in it, and the books are kept in it — one currency, so there is never a
question of what an order was "really" worth.

**PayPal is the exception, and it is charged in euro.** PayPal has no naira
support, so a naira-only site could not offer it at all. The receiving account is
a German one, so euro lands there natively instead of being converted twice.
Card and bank transfer are to settle through a Nigerian provider, charged the
naira figure unchanged; they are held back from checkout until that provider is
connected.

The checkout states the exact amount each method will ask for, next to that
method — nobody should pick PayPal and then meet an unexplained number on
PayPal's own page.

**The converted amount and the rate are stored on the order**, not recalculated
on the way to the provider. The rate can move between placing an order and
paying for it, and the customer must be charged what they were quoted; keeping
the rate as well makes the arithmetic on any past order checkable instead of
having to be trusted. `Order.exchangeRate` is null when no conversion happened,
which is what `isConverted()` reads.

The rate itself is a configured constant (`APP_NAIRA_PER_EURO`), not a live
feed — a real limitation, and a stale one quietly changes what the shop earns on
every PayPal sale. Swapping in a rates API means changing
`ExchangeRates.nairaPerEuro()` and nothing else, because every caller already
snapshots the rate it was handed.

**Money is formatted in `Money`, with `Locale.ROOT`.** The JDK's currency
formatter follows the server's default locale, so the same order would render
`₦2,490,000.00` on one host and `₦2.490.000,00` on another. `OrderServiceTest`
pins this by formatting under `Locale.GERMANY`.

The seeded catalogue prices are placeholders converted at a round rate. Set real
ones before taking real money.

**Product specifications are a key-value table, not columns.** The attributes
genuinely differ by category -- a panel has a cell count and a module
efficiency, a battery has a chemistry and a cycle life, a mounting kit has a
wind load -- so columns on `products` would be a wide table that is mostly null.
The column is `spec_value` rather than `value` because `VALUE` is a reserved
word in H2, which refuses the `CREATE TABLE` outright while Postgres and MySQL
accept it.

> **The seeded specification values are invented.** They were not taken from any
> datasheet. Publishing made-up dimensions or warranty terms is worse than
> publishing none -- someone orders panels that do not fit their roof, or
> believes a warranty that does not exist. Replace every row with the
> manufacturer's own figures before selling against them.

**"Usually bought with this" is a relationship between categories, not SKUs.**
`Category.pairsWith()` holds it, because a solar install is a system: panels
need an inverter and something to bolt them to, an inverter without storage is
daytime-only. That does not change when the catalogue does, so it needs no
schema and no maintenance.

**Every product has its own illustration.** `Product.getImage()` still falls
back to a category picture, but no seeded product relies on that now. It used
to: eight of the ten showed a duplicate, so both panels, both inverters, both
batteries and both mounting kits were the same picture at different prices --
which on a grid reads as a broken shop rather than a range.

**The illustrations carry no background of their own.** The thumbnail container
paints `surface-2`, so the artwork follows the theme instead of glowing as a
pale slab in dark mode. Only the hero keeps its own sky, being a scene rather
than an object.

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

**The catalogue is public; doing something with it is not.** Every product page
is readable signed out — price, description, stock and all. Only the basket and
the checkout require an account, and `ProductControllerTest` pins both halves of
that split, because the easy mistake is to gate the detail page along with the
basket.

**Adding to the basket does not leave the page.** `CartController` has two
handlers on `/cart/add`: the plain one redirects to the basket, and a second,
selected by the `X-Requested-With` header that `app.js` sets, answers with JSON
carrying the new basket size. The script updates the header badge in place. A
browser without JavaScript never sends the header, falls through to the redirect
and still works — the form is a real form, the script only intercepts its
submit. Sending the form's own `FormData` carries Spring Security's hidden
`_csrf` field along with it, so no token handling is duplicated in JavaScript.

**The address is fields, not a textarea.** One block of free text cannot be
validated, cannot be handed to a courier's API, and cannot be searched or sorted
afterwards. Line 2 and the postcode are optional on purpose — most Nigerian
addresses carry no postcode in daily use, so requiring one would block a
perfectly correct address. A missing optional field is stored as `null`, never
as `""`: "no postcode" and "an empty postcode" must not be two different things.
`Order.getShippingLines()` assembles the label, so no view decides the layout.

**A method is offered only when something can charge on it.** Checkout offers
PayPal alone today. Card and Nigerian bank transfer are `COMING_SOON` and do not
appear, because nothing can take money on them yet; they return when OPay is
connected. `PaymentMethod.Availability` carries that distinction — `OFFERED`,
`COMING_SOON`, `WITHDRAWN` — and the checkout page names the coming ones so a
customer who wanted transfer is told rather than left hunting.

This replaced a stand-in that was worse than nothing. Every method got a pay
button, and where no provider was configured that button called `markPaid`
itself. The form belonged to the buyer, so choosing card and pressing it marked
their own order paid. `OrderService.markPaid` now takes a loaded `Order` and the
id-and-user overload is gone, because that overload read like a permission check
while being the opposite: the customer is exactly the person who must not decide
their order is paid.

Apple Pay, SEPA and Klarna are `WITHDRAWN` but survive as `PaymentMethod`
constants. Deleting them would make Hibernate throw on any order already placed
with one, turning a historical row into a broken page. The enum is persisted as
a name rather than an ordinal, so constants can be reordered or regrouped
without touching stored rows.

**No card number, IBAN or bank detail is collected anywhere,** and the schema has
nowhere to put one. Those belong on the provider's own hosted page or embedded
element. A form here that looked like it took card details would invite someone
to type a real one into an app that cannot process or protect it.

**PayPal is integrated.** Choosing it creates a PayPal order for the exact
amount snapshotted on our order, sends the buyer to PayPal's own page, and
captures on their return. `PaymentService.isLive` decides whether the order page
shows a pay button at all, so an unconfigured provider degrades to an honest
explanation rather than to a broken button — or, as it once did, to a button
that marked the order paid for free.

**An order becomes `PAID` only because a provider told us, in an exchange we
started, that money moved.** The buyer arriving back at the return URL is not
that — a URL can be typed, bookmarked or replayed. What the return does is
trigger a capture call; the *capture's response* is the evidence, because we
made that call ourselves. The webhook is the same evidence by another route, for
when the buyer closes the tab before returning.

This corrects an earlier note in this file that said only the webhook may settle
an order. The distinction that matters is not webhook-versus-return, it is
provider-said-so versus browser-said-so.

Four things are checked before an order is settled, each with a test:

- the capture must report `COMPLETED`
- the captured amount and currency must match what the order asked for, or the
  order is refused rather than dispatched
- a settled order is never captured or settled twice, because providers retry
  and buyers refresh
- a webhook carrying a different payment's reference settles nothing

**Webhooks are treated as hostile until PayPal confirms it signed them.** The
endpoint is open to the internet by necessity, so every body goes to PayPal's
`verify-webhook-signature` first, and verification that errors counts as
verification that failed. With `PAYPAL_WEBHOOK_ID` unset there is no way to tell
genuine from forged, so the endpoint ignores everything — the safe default, not
a bug. It is exempt from CSRF because a provider has no token to send; the
exemption is scoped to `/payments/*/webhook` and the signature check stands in
for it.

**Provider trouble never marks an order paid.** Transport failures are wrapped
as `PaymentException`, so a DNS failure and a 401 reach the caller the same way
and both leave the order `PENDING_PAYMENT` with a retry available.

**One reminder per unpaid order.** `PaymentReminderJob` chases orders left in
`PENDING_PAYMENT` past `app.payment-reminders.after-hours`, and records the fact
on the order row so a restart cannot make it chase the same order twice — an
unpaid order that emails someone hourly is worse than one that never emails at
all. Off unless `APP_PAYMENT_REMINDERS_ENABLED=true`; running more than one
instance would need a lock added here, since two schedulers would otherwise both
claim the same order.

`PaymentMethod` is nullable on `Order`, because orders placed before the column
existed have no choice recorded and back-filling one would be inventing data.

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
  static/js/    app.js -- progressive enhancement only, never required
```

## Known gaps

These are deliberate and worth picking up next:

- **No payment provider.** The method is chosen and recorded, but nothing is
  charged; see above.
- **No admin UI.** The `ADMIN` role exists and is assignable, but nothing uses
  it; products can only be changed through the seeder or directly in the
  database.
- **No stock reservation.** Stock is checked and decremented when the order is
  placed. Two shoppers racing for the last unit are resolved by whoever commits
  first; the loser gets an error at checkout rather than at add-to-basket.
- **Java 21, not 17.** The original build pinned a Java 17 toolchain. Since the
  code is new, this targets the current LTS instead. `settings.gradle` includes
  the foojay resolver so Gradle can provision a JDK when the machine lacks one.
