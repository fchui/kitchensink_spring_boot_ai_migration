# kitchensink

A member registration application on **Spring Boot 4** and **Java 21**. People register through a web page; other systems use a REST API. Members are validated (name, email, phone number), stored in **MongoDB** and listed by name.

This is the JBoss EAP *kitchensink* quickstart, migrated from Jakarta EE (EJB, CDI, JAX-RS, JSF) to Spring Boot. URLs, REST responses and data stay compatible with the original. [MIGRATION.md](MIGRATION.md) has the migration plan, status and next steps.

## Contents

- [Tech stack](#tech-stack)
- [Prerequisites](#prerequisites)
- [Build and run](#build-and-run)
- [Configuration](#configuration)
- [Features](#features)
- [Endpoints](#endpoints)
- [REST API](#rest-api)
- [Validation rules](#validation-rules)
- [Tests](#tests)
- [Project structure](#project-structure)
- [Migration summary](#migration-summary)
- [Known limitations](#known-limitations)

## Tech stack

| Concern | Technology |
|---|---|
| Runtime | Spring Boot 4.1, embedded Tomcat, executable jar |
| Language | Java 21 |
| Web page | Spring MVC + Thymeleaf |
| REST API | Spring MVC `@RestController`, JSON via Jackson |
| Persistence | Spring Data MongoDB on MongoDB 8 |
| Validation | Jakarta Bean Validation (Hibernate Validator) |
| Messages | Spring `MessageSource`, English, Spanish, French |
| Build | Maven |
| Tests | JUnit 5, MockMvc, `@DataMongoTest`, embedded MongoDB (Flapdoodle), black-box HTTP contract tests |

## Prerequisites

- **JDK 21.** Check with `java -version`.
- **Maven 3.9 or newer.** Check with `mvn -v`.
- **Port 8080 free**, or pick another port (see [Configuration](#configuration)).
- **Internet access on the first test run or `test-run`:** embedded MongoDB downloads the official MongoDB server (about 70 MB) from mongodb.com once and caches it in `~/.embedmongo`.

Nothing else needs to be installed for development and tests: no application server, no Docker, no MongoDB. [Flapdoodle](https://github.com/flapdoodle-oss/de.flapdoodle.embed.mongo) starts a real MongoDB server as a local process. To run the packaged jar you need a MongoDB server (see [Configuration](#configuration)).

## Build and run

### 1. Get the code

```bash
git clone https://github.com/fchui/kitchensink_spring_boot_ai_migration.git
```

```bash
cd kitchensink_spring_boot_ai_migration
```

```bash
git checkout migration/spring-boot
```

### 2. Run in development

```bash
mvn spring-boot:test-run
```

This runs the app with the test classpath, which includes embedded MongoDB: a throwaway MongoDB 8.0 server starts on a free port and the app connects to it. The app is ready when the log shows `Started KitchensinkApplication`. Stop it with `Ctrl+C`; the data is removed.

To run against your own MongoDB instead (by default `mongodb://localhost:27017/kitchensink`), use:

```bash
mvn spring-boot:run
```

### 3. Open the app

- Web page: <http://localhost:8080/kitchensink/>
- REST API: <http://localhost:8080/kitchensink/rest/members>

### 4. Build and run the jar

```bash
mvn clean package
```

```bash
java -jar target/kitchensink-1.0.0-SNAPSHOT.jar
```

The jar connects to the MongoDB in `MONGODB_URI`, or `mongodb://localhost:27017/kitchensink` if it isn't set. `mvn package` also runs the unit tests; add `-DskipTests` to skip them.

### 5. Run the full test suite (optional)

```bash
mvn verify -Pintegration-testing
```

See [Tests](#tests).

## Configuration

Settings live in [`src/main/resources/application.properties`](src/main/resources/application.properties). Any of them can be overridden on the command line.

| Setting | Default | Notes |
|---|---|---|
| `server.port` | `8080` | HTTP port |
| `server.servlet.context-path` | `/kitchensink` | Kept from the EAP app so URLs don't change |
| `spring.mongodb.uri` | `mongodb://localhost:27017/kitchensink` | MongoDB connection string, including the database name. Set the `MONGODB_URI` environment variable to override it |
| `spring.data.mongodb.auto-index-creation` | `true` | Creates the unique index on `members.email` at startup. Keep it on: duplicate emails are only fully prevented by this index |

Connect to another server, for example with credentials:

```bash
MONGODB_URI="mongodb://user:password@db.example.com:27017/kitchensink?authSource=admin" java -jar target/kitchensink-1.0.0-SNAPSHOT.jar
```

Run on another port:

```bash
java -jar target/kitchensink-1.0.0-SNAPSHOT.jar --server.port=8181
```

```bash
mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=8181
```

On startup, `MemberDataInitializer` adds the sample member John Smith (id `0`) if no member has id `0`. Existing data is kept between restarts.

### Data in MongoDB

| Collection | Contents |
|---|---|
| `members` | One document per member: `_id` (numeric id), `name`, `email` (unique index), `phone_number` |
| `counters` | `{_id: "members", seq: N}`: the last id handed out. `MemberIdGenerator` increments it atomically for each new member, so ids stay numeric (0, 1, 2...) as in the relational version |

Ids are never reused: a registration rejected by the unique index still uses up an id.

## Features

- **Registration form:** name, email and phone number, validated on the server. Each invalid field shows its own message next to the input.
- **Unique email:** registering an email that is already taken is rejected, on the page ("Email taken") and in the API (409). This also holds when two registrations of the same email arrive at the same time: MongoDB's unique index on `email` catches the one that loses the race.
- **Member list:** all members sorted by name, each with a link to its REST URL. The list is shown full width at the end of the page.
- **Post/redirect/get:** after a successful registration the page redirects and shows "Registered!", so reloading the page doesn't submit the form twice.
- **REST API** for listing, looking up and registering members (see [REST API](#rest-api)).
- **Shared rules:** the web page and the REST API both go through `MemberRegistration`, so they apply the same rules. A new member can't carry an id, which stops a request from overwriting an existing member.
- **Registration event:** each successful registration publishes the new `Member` as an application event (`ApplicationEventPublisher`), so other components can react to it with `@EventListener`.
- **Languages:** page texts and validation messages in English, Spanish and French, chosen from the browser's `Accept-Language` header.
- **Responsive page:** works on phones and desktops, with automatic light and dark mode.
- **Error page:** unknown URLs and server errors show a localized error page instead of a stack trace.
- **Old URLs still work:** `/index.jsf` and `/index.html` from the EAP app redirect to the new page.

## Endpoints

All paths are relative to `http://localhost:8080/kitchensink`.

| Method | Path | Description |
|---|---|---|
| `GET` | `/` | Registration page: form and member list |
| `POST` | `/` | Submits the form (`name`, `email`, `phoneNumber`); redirects to `/` on success |
| `GET` | `/index.jsf`, `/index.html` | Old EAP page URLs; `301` redirect to `/` |
| `GET` | `/rest/members` | All members, sorted by name |
| `GET` | `/rest/members/{id}` | One member by numeric id |
| `POST` | `/rest/members` | Registers a member from JSON |
| `GET` | `/css/app.css` | Page stylesheet |

## REST API

Base URL: `http://localhost:8080/kitchensink/rest/members`. Requests and responses use JSON.

### List members

```bash
curl http://localhost:8080/kitchensink/rest/members
```

`200 OK`, sorted by name:

```json
[{"email":"john.smith@mailinator.com","id":0,"name":"John Smith","phoneNumber":"2125551212"}]
```

### Get one member

```bash
curl http://localhost:8080/kitchensink/rest/members/0
```

`200 OK` with the member, or `404 Not Found` if no member has that id. Non-numeric ids also return `404`.

### Register a member

```bash
curl -i -H "Content-Type: application/json" -d '{"name":"Jane Doe","email":"jane.doe@mailinator.com","phoneNumber":"2125551234"}' http://localhost:8080/kitchensink/rest/members
```

| Status | When | Body |
|---|---|---|
| `200 OK` | Member registered | Empty |
| `400 Bad Request` | One or more fields are invalid | `{field: message}` for each invalid field, for example `{"name":"must contain only letters and spaces","phoneNumber":"size must be between 10 and 12"}` |
| `400 Bad Request` | The request contains an `id` | `{"error":"A new member must not have an id"}` |
| `400 Bad Request` | The body is missing or isn't valid JSON | Spring's default error body |
| `409 Conflict` | The email is already registered | `{"email":"Email taken"}` |

Validation messages follow the `Accept-Language` header; for example, with `Accept-Language: es` a name with digits returns `{"name":"debe contener sólo letras y espacios"}`.

The new member's id isn't returned. Use the list endpoint to find it.

## Validation rules

Defined with Bean Validation annotations on [`Member`](src/main/java/org/spring/quickstarts/kitchensink/model/Member.java); the page and the API apply the same rules.

| Field | Rules |
|---|---|
| `name` | Required; 1 to 25 characters; no digits |
| `email` | Required; a well-formed email address; unique (case-sensitive) |
| `phoneNumber` | Required; 10 to 12 characters; digits only |
| `id` | Assigned by the database; must not be sent when registering |

## Tests

| Command | What runs |
|---|---|
| `mvn test` | Unit, repository (`@DataMongoTest`) and MockMvc tests: validation rules, MongoDB mapping, unique index, numeric ids, registration service, web page |
| `mvn verify -Pintegration-testing` | The above, plus the REST contract tests: Maven starts the app on port 8180 with embedded MongoDB, runs the `*IT` tests against it over HTTP, then stops it |

The tests use embedded MongoDB: Flapdoodle starts a MongoDB 8.0.23 server (`de.flapdoodle.mongodb.embedded.version` in `application.properties`) on a free port for each test context and stops it afterwards. It is a test dependency only, so the packaged jar never starts it.

The **contract tests** (`src/test/java/.../test/*IT.java`) are black-box HTTP tests. They don't depend on Spring or the database, so they ran unchanged against the EAP app, the relational Spring Boot version and this MongoDB version, and they protect the API's behavior during further migration steps. They cover the status codes and bodies above, duplicate and concurrent registrations, lookups and validation.

To run only the contract tests against an app that is already running (for example one started with `mvn spring-boot:run`), set `SERVER_HOST` and call the failsafe goals directly:

```bash
SERVER_HOST=http://localhost:8080 mvn -Pintegration-testing test-compile failsafe:integration-test failsafe:verify
```

The tests register new members, so run them against a throwaway instance, not one with data you want to keep.

## Project structure

```
src/main/java/org/spring/quickstarts/kitchensink/
├── KitchensinkApplication.java         Spring Boot entry point
├── controller/
│   ├── MemberController.java           Registration page (GET/POST /)
│   └── LegacyUrlConfig.java            Redirects for the old EAP URLs
├── rest/
│   ├── MemberResourceRESTService.java  REST API (/rest/members)
│   └── MemberRestExceptionHandler.java Maps errors to 400 / 409 responses
├── service/
│   ├── MemberRegistration.java         Registration rules, event
│   └── DuplicateEmailException.java
├── data/
│   ├── MemberRepository.java           Spring Data MongoDB repository
│   ├── MemberIdGenerator.java          Numeric ids from the counters collection
│   └── MemberDataInitializer.java      Adds the sample member on startup
└── model/
    └── Member.java                     MongoDB document with validation rules

src/main/resources/
├── application.properties              Configuration
├── ValidationMessages*.properties      Custom validation messages (EN/ES/FR)
├── org/spring/quickstarts/kitchensink/bundle/Resources*.properties   Page texts (EN/ES/FR)
├── templates/                          Thymeleaf: layout.html, index.html, error.html
└── static/css/app.css                  Stylesheet
```

[architecture.mmd](architecture.mmd) has a Mermaid diagram of the layers.

## Migration summary

| JBoss EAP (Jakarta EE) | Spring Boot |
|---|---|
| `@Stateless` EJB (container transactions) | `@Service`; a registration is a single-document insert, so it needs no transaction |
| CDI `@Inject`, producers | Constructor injection |
| CDI `Event` / `@Observes` | `ApplicationEventPublisher` |
| JAX-RS resource + `JaxRsActivator` | `@RestController` + `@RestControllerAdvice` |
| JSF page (`index.xhtml`) | Spring MVC controller + Thymeleaf template |
| JPA entity, `EntityManager` queries | MongoDB `@Document`, Spring Data `MongoRepository` |
| H2 database, container datasource (`*-ds.xml`, `persistence.xml`) | MongoDB, `spring.mongodb.uri` in `application.properties` |
| Unique constraint on `email` | Unique index on `email` |
| `@GeneratedValue` numeric ids | Numeric ids from a `counters` collection |
| `import.sql` seed data | `MemberDataInitializer` |
| JBoss Logging message bundles | `MessageSource` with `Resources*.properties` |
| `war` deployed to EAP | Executable `jar` with embedded Tomcat |

Behavior changes compared to the EAP app:

- Malformed JSON returns `400` (the EAP app returned `500`).
- A duplicate email lost in a concurrent race returns `409` (was `400`).
- Registering with an `id` is rejected with `400`, so it can no longer overwrite an existing member.

## Known limitations

- **No schema migrations yet:** the unique index is created from the `@Indexed` annotation at startup, and the seed member by startup code. [MIGRATION.md](MIGRATION.md) plans Mongock change sets for production.
- **No authentication:** anyone who can reach the app can register members.
- **No health endpoint yet:** Spring Boot Actuator isn't added.
- **The `POST` response has no body or `Location` header,** to stay compatible with the EAP API.
