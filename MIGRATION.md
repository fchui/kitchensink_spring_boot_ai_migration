# Migration Plan: JBoss EAP → Spring Boot

This plan migrates the kitchensink member registration app from JBoss EAP 8.1 (Jakarta EE) to Spring Boot on the `migration/spring-boot` branch.

**Approach:** hybrid, AI-assisted migration. First lock in today's behavior with contract tests, then move one layer at a time. Every commit builds and passes the tests.

**Target:** the current Spring Boot release on Java 21, packaged as an executable jar.

## How the current pieces map to Spring Boot

| Today (JBoss EAP) | Becomes (Spring Boot) |
|---|---|
| `Member` entity (`jakarta.persistence`, validation rules) | Kept almost unchanged (drop `@XmlRootElement`) |
| `MemberRepository` (hand-written `EntityManager` queries) | Spring Data JPA interface: `findByEmail`, `findAllByOrderByNameAsc` |
| `MemberRegistration` (`@Stateless` EJB + CDI `Event`) | `@Service` + `@Transactional` + `ApplicationEventPublisher` |
| `MemberListProducer` (CDI producer + event observer) | Removed: the web controller loads the list on each request |
| `MemberResourceRESTService` + `JaxRsActivator` (JAX-RS) | `@RestController` at `/rest/members`, same responses |
| `MemberController` + `index.xhtml` (JSF) | Spring MVC controller + Thymeleaf template |
| `KitchensinkMessages` (JBoss Logging bundles, EN/ES/FR) | Spring `MessageSource` with `messages*.properties` |
| `ValidationMessages*.properties` | Kept as-is (Hibernate Validator reads them) |
| `Resources` (CDI producers), `beans.xml`, `faces-config.xml` | Removed |
| `persistence.xml` + `kitchensink-quickstart-ds.xml` (H2) | `application.properties` (H2 in dev, PostgreSQL profile) |
| `import.sql` | `data.sql`, later Flyway migrations |
| `wildfly-quickstart-parent`, EAP BOM and plugins, `war` | `spring-boot-starter-parent`, `spring-boot-maven-plugin`, `jar` |
| `charts/helm.yaml` (EAP S2I chart) | Container image (`spring-boot:build-image`) + plain Helm chart |

## Phase 0: Prepare (old app still in place)

1. **Pin the toolchain.** Add the Maven wrapper (`mvnw`) and a Maven Enforcer rule requiring JDK 17 or 21, so builds can't run on an unsupported Java version (such as Java 11 or Java 27).
   - Commit: `build: add maven wrapper and enforce supported JDK`
2. **Add `CLAUDE.md` with the migration rules:** one layer per pull request; contract tests can't be changed in the same pull request as the code; build commands; target versions.
   - Commit: `docs: add migration guidelines for AI-assisted work`

## Phase 1: Lock in current behavior (the safety net)

3. **Turn the existing `MemberRegistrationIT` into a full contract test suite.** It already tests over HTTP and reads the address from `SERVER_HOST`. Move it to its own `contract-tests` module and cover:
   - `GET /kitchensink/rest/members`: returns the seed member, sorted by name
   - `GET /kitchensink/rest/members/{id}`: found, and 404 when missing
   - `POST`: 200 on success; 400 with field-to-message errors for a bad name, email or phone; 409 with `{"email":"Email taken"}` for a duplicate
   - Commit: `test: add black-box contract tests for REST API`
4. **Run the suite against the legacy app on EAP 8.1 with JDK 21, and record the result as the baseline.**
5. **Split the repo into modules:** `legacy/` (the current app, unchanged), `app/` (an empty Spring Boot skeleton), `contract-tests/`, and a parent `pom.xml` that ties them together. The JBoss parent `pom.xml` and the EAP settings move into `legacy/` only.
   - Commit: `build: restructure into legacy, app and contract-tests modules`

## Phase 2: Build the Spring Boot app, bottom up, one pull request per layer

| Step | Layer | Work | Gate |
|---|---|---|---|
| 6 | **Skeleton** | Spring Boot parent, web/JPA/validation/Thymeleaf/H2 starters; `server.servlet.context-path=/kitchensink` to keep the URLs | App starts; a health check passes |
| 7 | **Data model** | Copy `Member`; keep the validation rules and the unique-email rule | Unit tests for the validation rules |
| 8 | **Data access** | `MemberRepository` as a Spring Data interface; `data.sql` seeds John Smith | `@DataJpaTest`: find by ID, find by email, sorted list |
| 9 | **Business service** | `MemberRegistration` as a `@Transactional` service publishing a `MemberRegisteredEvent` | Service tests: saves, publishes the event, rejects invalid data |
| 10 | **REST API** | `@RestController` with the same 200 / 400 / 409 / 404 behavior and error bodies | **All contract tests pass against `app/`**, unchanged |
| 11 | **Web UI** | Thymeleaf version of `index.xhtml` + `default.xhtml` (form, member table, success and error messages); copy CSS and images | MockMvc + manual side-by-side check against the old UI |
| 12 | **Messages** | Move EN/ES/FR texts to `messages*.properties`; locale taken from the browser's `Accept-Language` | Tests for each locale |

Commit names follow the same pattern, for example `feat(api): port member REST endpoints to Spring MVC`.

**Fix along the way, as a separate commit:** the duplicate-email check runs before the save, so two registrations at almost the same moment could both get past it. Also handle the database's unique-constraint error and return a 409 for it.
- Commit: `fix(api): return 409 on unique constraint violation`

**Watch out in step 8:** the seed row uses `id = 0` while Hibernate generates IDs automatically. Confirm that newly registered members don't collide with the seed row.

## Phase 3: Production readiness

13. **Configuration profiles:** `dev` (H2) and `prod` (PostgreSQL via environment variables). Replace "rebuild tables on startup" with Flyway migrations.
    - Commit: `feat(db): add flyway migrations and postgres profile`
14. **Operations:** health and metrics with Spring Boot Actuator, JSON logs, graceful shutdown.
15. **Deployment:** build a container image with `./mvnw spring-boot:build-image` and replace the EAP Helm chart with a plain Helm chart for Kubernetes/OpenShift. It should point at this repo, run 2+ copies, and include health checks.
    - Commit: `build(deploy): replace EAP S2I chart with container image and helm chart`
16. **Security (decide the scope first):** at least protect anything that changes data. For example, require login for `POST /rest/members` and the registration form.

## Phase 4: Cutover and cleanup

17. **Final check:** the contract tests pass on `app/` in a container and on the target cluster, and a manual comparison with `legacy/` matches.
18. **Remove `legacy/`** and the EAP dependencies, docs and chart.
    - Commit: `refactor!: remove legacy JBoss EAP module`
19. **Optional:** rename the `org.jboss.as.quickstarts.kitchensink` packages, and rewrite the README for Spring Boot.
20. **Merge `migration/spring-boot` into `master`.**

## Rules for every step

- **Pull request size:** one layer, or one concern, per pull request. Review before starting the next.
- **Tests as the gate:** contract tests pass and never change in the same pull request as the code. Unit tests are added along with each layer.
- **Always green:** `./mvnw verify` passes at every commit, so any commit can be stopped at or rolled back.
- **Roles:** the AI does mechanical porting and writes tests. A human approves the contract tests, design decisions (UI technology, security scope) and the final cutover.

## Open decisions (before Phase 2)

- [ ] **UI technology:** Thymeleaf (standard Spring approach) or JoinFaces (keeps the JSF pages with less rework)
- [ ] **Production database:** PostgreSQL or another one
- [ ] **Languages:** keep or drop Spanish and French (plan assumes kept)
- [ ] **Security scope:** what needs login
