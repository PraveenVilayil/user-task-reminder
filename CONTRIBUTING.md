# Contributing

Thanks for taking an interest. This is a small project with a small team, so the
process is light — but a few conventions keep the codebase coherent.

## Getting set up

You need **JDK 21** and nothing else. The Maven wrapper is committed, and the
default `dev` profile runs on in-memory H2, so a fresh clone builds and boots
with no configuration.

```bash
git clone https://github.com/PraveenVilayil/user-task-reminder.git
cd user-task-reminder
./mvnw clean verify
```

On Windows, use `mvnw.cmd` in place of `./mvnw`.

| Command | What it does |
| --- | --- |
| `./mvnw clean verify` | Full build: compile, unit tests, integration tests. This is what CI runs. |
| `./mvnw test` | Unit tests only. Faster feedback loop. |
| `./mvnw -pl user-task-reminder-service test` | One module. |
| `./mvnw -pl user-task-reminder-web spring-boot:run` | Start the API on <http://localhost:8080>. |
| `./mvnw -B -DskipTests install` | Build the artefacts without running tests. |

`./mvnw clean verify` must pass before you open a pull request. If a test fails,
fix the cause rather than the assertion.

## Branches and commits

Branch from `master`, named after what the branch does:

```
implement/reminder-cancellation
fix/audit-actor-fallback
task/upgrade-springdoc
```

Commits follow [Conventional Commits](https://www.conventionalcommits.org/):

```
feat(reminder): support cancelling without deleting
fix(service): resolve the owner before saving a task
test: cover the delivery retry budget
docs: document the search parameters
chore(build): bump springdoc to 2.8.6
```

Common types: `feat`, `fix`, `refactor`, `test`, `docs`, `chore`, `build`.

Write the body to explain **why**, not what — the diff already shows what. If
you fixed a bug, say what the broken behaviour was, so the next person reading
`git log` understands the change without reconstructing it.

## Where code goes

The module boundaries are the main structural rule. Putting something in the
wrong module is the easiest way to make the build cyclic.

| Module | Put this here | Do not put this here |
| --- | --- | --- |
| `user-task-reminder-model` | Entities, DTOs, enums, validation groups | Anything Spring, any business logic |
| `user-task-reminder-db` | Repositories, specifications, Flyway migrations | Business rules, HTTP concerns |
| `user-task-reminder-service` | Services, scheduling, notification senders, aspects, the error catalogue | Controllers, request or response handling |
| `user-task-reminder-web` | Controllers, API constants, configuration, `application.yaml` | Business logic of any kind |

Dependencies point one way only: `web → service → db → model`.

## Conventions

These are followed throughout; please match them rather than introducing a
second style.

**Errors.** Every failure raises a `UserTaskReminderException` with an
`ErrorCode`. Add new codes to the catalogue in the right numeric block:

| Block | Meaning |
| --- | --- |
| `001`–`099` | Resource not found |
| `100`–`199` | Validation |
| `200`–`299` | Internal |
| `300`–`399` | Auth (reserved) |
| `400`–`499` | Duplicates and conflicts |

Never return a bare string or a map from a controller on failure — the handler
produces `ErrorResponse` for everything.

**Time.** Do not call `LocalDateTime.now()`. Inject `Clock` and call
`LocalDateTime.now(clock)`. This is what makes the scheduling logic testable
without sleeping, and a single direct call to `now()` undoes it.

**Transactions.** Service methods are `@Transactional`, and reads are
`@Transactional(readOnly = true)`. Entity-to-DTO mapping happens inside the
transaction; `open-in-view` is off deliberately and should stay off.

**Injection.** Constructor injection, not field `@Autowired`.

**Mapping.** ModelMapper handles the read direction (entity to DTO) through the
explicit type maps in `ModelMapperConfig`. The write direction is written out by
hand in the services, because it has to resolve associations against the
database. Please do not reintroduce a reflective field-copy loop — the one this
project used to have silently corrupted patches involving associations.

**Schema.** Hibernate never generates DDL. Schema changes are a new Flyway
migration (`V2__...`, `V3__...`), written in SQL that works on both PostgreSQL
and H2, since the repository tests run the migrations on H2. Never edit an
applied migration.

**Logging.** SLF4J, at the class level. No `System.out.println`.

**Secrets.** Nothing goes in `application.yaml`. Configuration reads from
environment variables with defaults, and anything sensitive has no default. Add
new variables to `.env.example` and to the configuration table in the README.

## Tests

New behaviour needs a test. The suite is the main thing keeping this project
honest, and it is expected to stay green.

- **Service logic** — JUnit 5 and Mockito, no Spring context. Fast.
- **Repositories and queries** — `@DataJpaTest`, which runs on H2 against the
  real Flyway migrations. If a mapping and a migration disagree, this is where
  it surfaces.
- **Controllers** — `@WebMvcTest` with MockMvc and `@MockitoBean`. Cover the
  error mapping as well as the happy path.
- **Whole-application behaviour** — `@SpringBootTest` with the `test` profile.
  Name these `*IT` so failsafe runs them in the `integration-test` phase.

Nothing may depend on an external service: no PostgreSQL, no broker, no SMTP
server, no Docker. Everything runs on H2 and in-process doubles, which is why
`./mvnw clean verify` works on a fresh clone and in CI. Do not add Testcontainers
without discussing it first.

Do not write a test that sleeps. If you need time to pass, move the injected
`Clock`.

## Documentation

The README documents only what exists. If you add an endpoint, add it to the API
reference; if you add an environment variable, add it to the configuration table
and `.env.example`. If you build toward something on the roadmap, move the entry
out of the roadmap when it actually works — not before.

Annotate new endpoints with `@Operation` and `@ApiResponse` so the OpenAPI
document stays accurate.

## Pull requests

- One concern per pull request.
- Say what changed and why, and how you verified it.
- Make sure `./mvnw clean verify` passes.
- If you could not verify part of it — no Docker, no PostgreSQL — say so
  explicitly rather than leaving it implied.
