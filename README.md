# kitchensink

A member registration web application on Spring Boot 4 and Java 21. People register through a web page; other systems use a REST API. Members are validated (name, email, phone number), stored in a database, and listed by name.

Migrated from the JBoss EAP kitchensink quickstart; see [MIGRATION.md](MIGRATION.md) for the plan and status.

## Requirements

- JDK 21
- Maven 3.9+

## Run

```bash
mvn spring-boot:run
```

Or build and run the jar:

```bash
mvn package
java -jar target/kitchensink-1.0.0-SNAPSHOT.jar
```

Then open <http://localhost:8080/kitchensink>. Use `--server.port=8181` (or `-Dspring-boot.run.arguments=--server.port=8181` with Maven) if port 8080 is taken.

The database is an in-memory H2 instance, recreated on every start and seeded with one member from `src/main/resources/import.sql`.

## REST API

Base URL: `http://localhost:8080/kitchensink/rest/members`

| Method | Path | Result |
|---|---|---|
| `GET` | `/rest/members` | All members, sorted by name |
| `GET` | `/rest/members/{id}` | One member, or 404 |
| `POST` | `/rest/members` | Registers a member from JSON `{"name", "email", "phoneNumber"}`: 200 with an empty body; 400 with `{field: message}` for invalid data; 409 with `{"email": "Email taken"}` for a duplicate email |

## Tests

```bash
mvn verify -Pintegration-testing
```

Runs the unit tests, starts the app on port 8180, runs the black-box REST contract tests (`*IT`) against it, and stops it. To run the contract tests against an app that is already running, set `SERVER_HOST` (for example `http://localhost:8080`).

## Languages

The web page and validation messages are available in English, Spanish and French, chosen from the browser's `Accept-Language` header.
