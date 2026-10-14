# Migration Plan: JBoss EAP → Spring Boot + MongoDB

This plan migrates the kitchensink member registration app from JBoss EAP 8.1 (Jakarta EE, JPA/Hibernate, H2) to Spring Boot with MongoDB on the `migration/spring-boot` branch.

**Approach:** hybrid, AI-assisted migration. First lock in today's behavior with contract tests, then move one layer at a time. Every commit builds and passes the tests.

**Target:** the current Spring Boot release on Java 21, Spring Data MongoDB, packaged as an executable jar.

## How the current pieces map to Spring Boot + MongoDB

| Today (JBoss EAP) | Becomes (Spring Boot + MongoDB) |
|---|---|
| `Member` JPA entity (`@Entity`, `@Table` unique email, `@GeneratedValue` id) | `@Document("members")` with `@Indexed(unique = true)` on email; validation rules unchanged (drop `@XmlRootElement`) |
| `MemberRepository` (hand-written `EntityManager` queries) | `MongoRepository`: `findByEmail`, `findAllByOrderByNameAsc` |
| `MemberRegistration` (`@Stateless` EJB + CDI `Event`) | `@Service` + `ApplicationEventPublisher`; `@Transactional` only if MongoDB transactions are enabled (see Phase 3) |
| `MemberListProducer` (CDI producer + event observer) | Removed: the web controller loads the list on each request |
| `MemberResourceRESTService` + `JaxRsActivator` (JAX-RS) | `@RestController` at `/rest/members`, same responses |
| `MemberController` + `index.xhtml` (JSF) | Spring MVC controller + Thymeleaf template |
| `KitchensinkMessages` (JBoss Logging bundles, EN/ES/FR) | Spring `MessageSource` with `messages*.properties` |
| `ValidationMessages*.properties` | Kept as-is (Hibernate Validator is also Spring Boot's validator) |
| `Resources` (CDI producers), `beans.xml`, `faces-config.xml` | Removed |
| `persistence.xml` + `kitchensink-quickstart-ds.xml` (H2) | `spring.data.mongodb.uri` in `application.properties`; Testcontainers MongoDB for tests |
| `import.sql` (seed member, id 0) | Startup seed loader or Mongock change set |
| Database unique constraint on email | Unique index on `email`; `spring.data.mongodb.auto-index-creation=true` (off by default) or created by a migration |
| `wildfly-quickstart-parent`, EAP BOM and plugins, `war` | `spring-boot-starter-parent`, `spring-boot-maven-plugin`, `jar` |
| `charts/helm.yaml` (EAP S2I chart) | Container image (`spring-boot:build-image`) + plain Helm chart |

## Contract tests (safety net)

The REST contract tests are black-box HTTP tests: they don't depend on JBoss, JPA, Hibernate or SQL, so they run unchanged against both apps. Current suite:

| Test | Covers | Migration note |
|---|---|---|
| `BasicRuntimeIT` | `GET /kitchensink` returns 200 | Smoke test of the JSF landing page; replace with `/kitchensink/actuator/health` in Phase 3 |
| `MemberDuplicateEmailIT` | 409 on duplicate email, empty body on success, seed email taken, case-sensitive uniqueness, exactly one member stored under concurrent registrations | Catches a missing MongoDB unique index or seed data |
| `MemberLookupIT` | Lookup by id for seed and new members, 404 for unknown and non-numeric ids, numeric id format | `memberIdIsANumber` pins the id format (see the ID decision below) |
| `MemberValidationIT` | 400 with `{field: message}` errors, missing fields, null and empty bodies, malformed JSON | `malformedJsonCurrentlyReturns500` records a legacy bug; Spring returns 400, so change it to expect 400 when the REST layer is migrated |
| `MemberValidationTest` (unit) | Every validation rule on `Member`, including recorded edge cases | Only the `Member` import changes |

Characterization tests record current behavior, including known bugs. Change them deliberately, in their own commit, never alongside the code that makes them fail.

## Phase 0: Prepare (old app still in place)

1. **Pin the toolchain.** Add the Maven wrapper (`mvnw`) and a Maven Enforcer rule requiring JDK 17 or 21, so builds can't run on an unsupported Java version (such as Java 11 or Java 27).
   - Commit: `build: add maven wrapper and enforce supported JDK`
2. **Add `CLAUDE.md` with the migration rules:** one layer per pull request; contract tests can't be changed in the same pull request as the code; build commands; target versions.
   - Commit: `docs: add migration guidelines for AI-assisted work`

## Phase 1: Lock in current behavior (the safety net)

3. **Contract test suite.** ✅ Done for duplicate email, validation and id lookup (see the table above). Still to add: member list sorted by name, including mixed-case names, since sort order can differ between H2 and MongoDB.
   - Commits: `test: add contract tests for duplicate email and validation rules`, `test: add member lookup contract tests and retarget plan to MongoDB`
4. **Run the suite against the legacy app on EAP 8.1 with JDK 21, and record the result as the baseline.** (So far it has only run against EAP 8.2 Beta.)
5. **Split the repo into modules:** `legacy/` (the current app, unchanged), `app/` (an empty Spring Boot skeleton), `contract-tests/` (the HTTP tests and `MemberApi`), and a parent `pom.xml` that ties them together. The JBoss parent `pom.xml` and the EAP settings move into `legacy/` only.
   - Commit: `build: restructure into legacy, app and contract-tests modules`

## Phase 2: Build the Spring Boot app, bottom up, one pull request per layer

| Step | Layer | Work | Gate |
|---|---|---|---|
| 6 | **Skeleton** | Spring Boot parent, web/data-mongodb/validation/Thymeleaf starters; Testcontainers MongoDB; `server.servlet.context-path=/kitchensink` to keep the URLs; JUnit 5 (all tests already use Jupiter) | App starts against MongoDB; a health check passes |
| 7 | **Data model** | `Member` as a `@Document` with a unique index on email; validation rules unchanged; id type per the ID decision | Unit tests for the validation rules (`MemberValidationTest`) |
| 8 | **Data access** | `MemberRepository` as a `MongoRepository`; unique index created at startup; seed loader for John Smith | `@DataMongoTest` with Testcontainers: find by id, find by email, sorted list, duplicate email rejected by the index |
| 9 | **Business service** | `MemberRegistration` as a `@Service` publishing a `MemberRegisteredEvent` only after a successful save | Service tests: saves, publishes the event, no event when the save fails |
| 10 | **REST API** | `@RestController` with the same 200 / 400 / 409 / 404 behavior and error bodies | **All contract tests pass against `app/`**, unchanged except the planned malformed-JSON change |
| 11 | **Web UI** | Thymeleaf version of `index.xhtml` + `default.xhtml` (form, member table, success and error messages); copy CSS and images | MockMvc + manual side-by-side check against the old UI |
| 12 | **Messages** | Move EN/ES/FR texts to `messages*.properties`; locale taken from the browser's `Accept-Language` | Tests for each locale |

Commit names follow the same pattern, for example `feat(api): port member REST endpoints to Spring MVC`.

**Fix along the way, as separate commits:**
- **Duplicate-email race:** the duplicate check runs before the save, so simultaneous registrations can both pass it (confirmed in the server log). Map MongoDB's `DuplicateKeyException` to 409 and tighten `concurrentRegistrationsWithSameEmailStoreExactlyOneMember` to require 409.
  - Commit: `fix(api): return 409 on duplicate key`
- **Malformed JSON:** return 400 instead of 500 and update `malformedJsonCurrentlyReturns500`.
  - Commit: `fix(api): return 400 for malformed JSON`

**Watch out in step 8:** Spring Data MongoDB does not create indexes from annotations unless `auto-index-creation` is enabled. Without the unique index, duplicates are only caught by the non-atomic pre-check, and the concurrent contract test will fail.

## Phase 3: Production readiness

13. **Configuration profiles:** `dev` (local or Testcontainers MongoDB) and `prod` (MongoDB connection string from environment variables). Manage the unique index and seed data with Mongock change sets instead of startup code. If registration needs transactions, run MongoDB as a replica set (required for MongoDB transactions, also in development).
    - Commit: `feat(db): add mongock migrations and production mongodb profile`
14. **Operations:** health and metrics with Spring Boot Actuator (including the MongoDB health check), JSON logs, graceful shutdown. Replace `BasicRuntimeIT` with a health check test.
15. **Deployment:** build a container image with `./mvnw spring-boot:build-image` and replace the EAP Helm chart with a plain Helm chart for Kubernetes/OpenShift. It should point at this repo, run 2+ copies, include health checks, and connect to a managed MongoDB (for example, a MongoDB operator or MongoDB Atlas).
    - Commit: `build(deploy): replace EAP S2I chart with container image and helm chart`
16. **Security (decide the scope first):** at least protect anything that changes data. For example, require login for `POST /rest/members` and the registration form.

## Phase 4: Cutover and cleanup

17. **Final check:** the contract tests pass on `app/` in a container and on the target cluster, and a manual comparison with `legacy/` matches.
18. **Remove `legacy/`** and the EAP dependencies, docs and chart.
    - Commit: `refactor!: remove legacy JBoss EAP module`
19. **Optional:** rename the `org.jboss.as.quickstarts.kitchensink` packages and rewrite the README for Spring Boot.
20. **Merge `migration/spring-boot` into `master`.**

## Rules for every step

- **Pull request size:** one layer, or one concern, per pull request. Review before starting the next.
- **Tests as the gate:** contract tests pass and never change in the same pull request as the code. Unit tests are added along with each layer.
- **Always green:** `./mvnw verify` passes at every commit, so any commit can be stopped at or rolled back.
- **Roles:** the AI does mechanical porting and writes tests. A human approves the contract tests, design decisions (UI technology, ID strategy, security scope) and the final cutover.

## Open decisions (before Phase 2)

- [ ] **ID strategy:** keep numeric ids (with a counter collection, so the API and `GET /rest/members/{id}` stay the same) or switch to MongoDB ObjectId strings (a breaking API change for clients that store or parse ids; update `memberIdIsANumber` and the id URL pattern)
- [ ] **Transactions:** whether registration needs MongoDB transactions (requires a replica set) or a single-document save is enough
- [ ] **Email case:** keep case-sensitive uniqueness (MongoDB's default) or make it case-insensitive with a collation
- [ ] **UI technology:** Thymeleaf (standard Spring approach) or JoinFaces (keeps the JSF pages with less rework)
- [ ] **Languages:** keep or drop Spanish and French (plan assumes kept)
- [ ] **Security scope:** what needs login
