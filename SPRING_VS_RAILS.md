# Spring Boot vs Rails — Full CRUD Comparison

## File Structure

| Rails | Spring Boot | What it does |
|---|---|---|
| `db/migrate/001_create_users.rb` | `entity/User.java` (@Entity) | Defines DB table shape |
| `app/models/user.rb` | `entity/User.java` + `repository/UserRepository.java` | Rails model = entity + repo combined |
| `app/controllers/users_controller.rb` | `controller/UserController.java` | HTTP routing + request handling |
| — (logic lives in model/controller) | `service/UserService.java` | Business logic layer (Rails has no strict equiv) |
| `app/serializers/user_serializer.rb` | `dto/UserResponse.java` | Controls JSON output shape |
| `params.require(:user).permit(...)` | `dto/CreateUserRequest.java` | Controls allowed input fields |
| — (not needed) | `mapper/UserMapper.java` | Converts between entity ↔ DTO |
| `config/routes.rb` | `@GetMapping` / `@PostMapping` on methods | URL → code mapping |
| `config/database.yml` + `.env` | `application.properties` | All configuration |
| `Gemfile` | `build.gradle` or `pom.xml` | Dependencies |

**Count: Rails 4 files → Spring 7 files for same CRUD.**

---

## Layer-by-Layer Comparison

### 1. Table Definition

<table>
<tr><th>Rails</th><th>Spring Boot</th></tr>
<tr><td>

```ruby
# db/migrate/001_create_users.rb
class CreateUsers < ActiveRecord::Migration[7.1]
  def change
    create_table :users do |t|
      t.string :name, null: false
      t.string :email, null: false
      t.timestamps
    end
    add_index :users, :email, unique: true
  end
end
```

</td><td>

```java
// entity/User.java
@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // getters + setters omitted
}
```

</td></tr>
<tr><td colspan="2">

**Common:** Both define table columns, constraints (not null, unique), and primary key auto-increment. Rails uses a migration file (imperative), Spring uses annotations on a class (declarative). Rails auto-adds `id`, `created_at`, `updated_at`. Spring you declare all fields explicitly.

</td></tr>
</table>

### 2. Model / Validation

<table>
<tr><th>Rails</th><th>Spring Boot</th></tr>
<tr><td>

```ruby
# app/models/user.rb
class User < ApplicationRecord
  validates :name, presence: true
  validates :email,
    presence: true,
    uniqueness: true,
    format: { with: URI::MailTo::EMAIL_REGEXP }
end
```

</td><td>

```java
// dto/CreateUserRequest.java
public record CreateUserRequest(
    @NotBlank String name,
    @NotBlank @Email String email
) {}

// dto/UpdateUserRequest.java
public record UpdateUserRequest(
    @NotBlank String name,
    @NotBlank @Email String email
) {}
```

</td></tr>
<tr><td colspan="2">

**Common:** Both validate presence and format before hitting DB. Rails validates on the model. Spring validates on the DTO (input shape) — the entity itself has no validation, it's just a DB mirror. `@Valid` on the controller param triggers validation.

</td></tr>
</table>

### 3. Query Layer

<table>
<tr><th>Rails</th><th>Spring Boot</th></tr>
<tr><td>

```ruby
# Built into the model
User.all
User.find(id)
User.find_by(email: "a@b.com")
user.save
user.destroy

# Custom scopes
scope :active, -> { where(active: true) }
```

</td><td>

```java
// repository/UserRepository.java
@Repository
public interface UserRepository
    extends JpaRepository<User, Long> {

  // Free: findAll(), findById(), save(),
  //       deleteById(), count(), existsById()

  // Custom — Spring generates SQL from name:
  List<User> findByEmail(String email);
  List<User> findByNameContainingIgnoreCase(String q);
}
```

</td></tr>
<tr><td colspan="2">

**Common:** Both give you standard CRUD for free. Custom queries: Rails uses chainable scopes + `where()`. Spring generates SQL by parsing the method name (`findByEmail` → `SELECT * FROM users WHERE email = ?`). You write zero SQL in either case for basic operations.

</td></tr>
</table>

### 4. Serializer / Response Shape

<table>
<tr><th>Rails</th><th>Spring Boot</th></tr>
<tr><td>

```ruby
# app/serializers/user_serializer.rb
class UserSerializer < ActiveModel::Serializer
  attributes :id, :name, :email, :created_at

  # Hide updated_at by not listing it
  # Add computed fields:
  attribute :display_name do
    "#{object.name} <#{object.email}>"
  end
end

# Usage:
render json: user, serializer: UserSerializer
```

</td><td>

```java
// dto/UserResponse.java
public record UserResponse(
    Long id,
    String name,
    String email,
    LocalDateTime createdAt
    // updated_at hidden by not including it
) {}

// For computed fields, build in mapper:
// mapper/UserMapper.java
public UserResponse toResponse(User user) {
    return new UserResponse(
        user.getId(),
        user.getName(),
        user.getEmail(),
        user.getCreatedAt()
    );
}

// Usage: return UserResponse from controller
// Jackson auto-converts to JSON
```

</td></tr>
<tr><td colspan="2">

**Common:** Both control which fields appear in JSON output. Rails: serializer filters at render time. Spring: DTO defines shape at compile time, Jackson converts all DTO fields to JSON automatically. The DTO *is* the serializer — it defines the contract upfront as a type.

**How Jackson works:** When controller returns an object, Spring calls Jackson which: (1) reads all fields via reflection, (2) converts field names → JSON keys (camelCase), (3) converts values → JSON values. You never call `toJson()`.

</td></tr>
</table>

### 5. Input Handling

<table>
<tr><th>Rails</th><th>Spring Boot</th></tr>
<tr><td>

```ruby
# Strong parameters (in controller)
def user_params
  params.require(:user).permit(:name, :email)
end

# params is a hash-like object
# .permit whitelist = security measure
```

</td><td>

```java
// @RequestBody + DTO
public UserResponse create(
    @Valid @RequestBody CreateUserRequest req
) { ... }

// Jackson deserializes JSON → DTO
// @Valid triggers validation annotations
// Only fields in DTO are accepted
// Unknown JSON fields are silently ignored
```

</td></tr>
<tr><td colspan="2">

**Common:** Both whitelist allowed input fields. Rails: runtime filtering via `permit()`. Spring: compile-time — only fields defined in the DTO record are deserialized. Both ignore extra fields sent by client.

</td></tr>
</table>

### 6. Service Layer

<table>
<tr><th>Rails</th><th>Spring Boot</th></tr>
<tr><td>

```ruby
# Rails has no mandatory service layer
# Logic often lives in model or controller

# Optional pattern:
# app/services/user_service.rb
class UserService
  def self.create(params)
    user = User.new(params)
    user.save!
    UserMailer.welcome(user).deliver_later
    user
  end
end
```

</td><td>

```java
// service/UserService.java — REQUIRED pattern
@Service
public class UserService {
    @Autowired private UserRepository repo;
    @Autowired private UserMapper mapper;

    @Transactional
    public UserResponse create(CreateUserRequest req) {
        User user = mapper.toEntity(req);
        user.setCreatedAt(LocalDateTime.now());
        User saved = repo.save(user);
        return mapper.toResponse(saved);
    }
}
```

</td></tr>
<tr><td colspan="2">

**Common:** Both orchestrate business logic. Rails: optional, most teams put logic in models (fat model, skinny controller). Spring: mandatory convention — controller never talks to repository directly. The service layer is where `@Transactional` lives (DB transaction boundaries).

</td></tr>
</table>

### 7. Controller

<table>
<tr><th>Rails</th><th>Spring Boot</th></tr>
<tr><td>

```ruby
# app/controllers/users_controller.rb
class UsersController < ApplicationController
  def index
    users = User.all
    render json: users,
      each_serializer: UserSerializer
  end

  def show
    user = User.find(params[:id])
    render json: user,
      serializer: UserSerializer
  end

  def create
    user = User.new(user_params)
    if user.save
      render json: user,
        serializer: UserSerializer,
        status: :created
    else
      render json: { errors: user.errors },
        status: :unprocessable_entity
    end
  end

  def update
    user = User.find(params[:id])
    if user.update(user_params)
      render json: user,
        serializer: UserSerializer
    else
      render json: { errors: user.errors },
        status: :unprocessable_entity
    end
  end

  def destroy
    User.find(params[:id]).destroy
    head :no_content
  end

  private
  def user_params
    params.require(:user).permit(:name, :email)
  end
end

# config/routes.rb
resources :users
```

</td><td>

```java
// controller/UserController.java
@RestController
@RequestMapping("/api/users")
public class UserController {

    @Autowired
    private UserService userService;

    @GetMapping
    public List<UserResponse> index() {
        return userService.findAll();
    }

    @GetMapping("/{id}")
    public UserResponse show(@PathVariable Long id) {
        return userService.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse create(
        @Valid @RequestBody CreateUserRequest req
    ) {
        return userService.create(req);
    }

    @PutMapping("/{id}")
    public UserResponse update(
        @PathVariable Long id,
        @Valid @RequestBody UpdateUserRequest req
    ) {
        return userService.update(id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void destroy(@PathVariable Long id) {
        userService.delete(id);
    }
}
```

</td></tr>
<tr><td colspan="2">

**Common:** Both map HTTP verbs to methods. Rails: routes defined in `routes.rb`, `resources :users` generates all 7 RESTful routes. Spring: routes defined on each method via annotations, no separate routes file. Spring controller is thinner — delegates everything to service. Rails controller often contains more logic.

</td></tr>
</table>

---

## Request Data Flow — Object Level

### What happens when `POST /api/users` with `{"name":"John","email":"j@x.com"}` hits each framework:

### Rails Flow

```
                    ┌─────────────────────────────────────────────────┐
                    │              Single Ruby Process                │
                    │              (Puma worker thread)               │
                    │                                                 │
 HTTP request ──────┤►  Rack middleware chain                        │
 POST /api/users    │   │                                            │
 {"name":"John"}    │   ▼                                            │
                    │   Router (config/routes.rb)                     │
                    │   routes.match("POST /users") → UsersController#create
                    │   │                                            │
                    │   ▼                                            │
                    │   UsersController.new                          │
                    │   │  params = ActionController::Parameters     │
                    │   │  (hash-like: {"user"=>{"name"=>"John"}})  │
                    │   │                                            │
                    │   ▼                                            │
                    │   user_params → .require(:user).permit(:name, :email)
                    │   │  returns: {"name"=>"John","email"=>"j@x.com"}
                    │   │                                            │
                    │   ▼                                            │
                    │   User.new(user_params)                        │
                    │   │  ActiveRecord model instance               │
                    │   │  runs validations on .save                 │
                    │   │                                            │
                    │   ▼                                            │
                    │   user.save → ActiveRecord → SQL INSERT        │
                    │   │                                            │
                    │   ▼                                            │
                    │   render json: user, serializer: UserSerializer│
                    │   │  serializer.new(user).as_json              │
                    │   │  picks :id, :name, :email, :created_at    │
                    │   │  .to_json → string                        │
                    │   │                                            │
                    │   ▼                                            │
                    │   Response: 201 {"id":1,"name":"John",...}     │
                    └─────────────────────────────────────────────────┘

 Objects created:
   1. ActionController::Parameters  (input hash)
   2. User (ActiveRecord instance)  (model + validation + persistence)
   3. UserSerializer                (output filter)
```

### Spring Boot Flow

```
                    ┌──────────────────────────────────────────────────┐
                    │              JVM (Tomcat thread)                  │
                    │                                                   │
 HTTP request ──────┤►  Servlet Filter chain (like Rack middleware)    │
 POST /api/users    │   │                                              │
 {"name":"John"}    │   ▼                                              │
                    │   DispatcherServlet (the front controller)        │
                    │   │  checks route map built at startup            │
                    │   │  "POST /api/users" → UserController.create   │
                    │   │                                              │
                    │   ▼                                              │
                    │   Jackson ObjectMapper.readValue(json, CreateUserRequest.class)
                    │   │  deserializes JSON → DTO object              │
                    │   │  CreateUserRequest("John", "j@x.com")       │
                    │   │                                              │
                    │   ▼                                              │
                    │   Hibernate Validator (@Valid)                    │
                    │   │  checks @NotBlank, @Email on DTO fields     │
                    │   │  if invalid → 400 Bad Request (never hits controller)
                    │   │                                              │
                    │   ▼                                              │
                    │   UserController.create(req)  ← singleton bean  │
                    │   │  (NOT new instance — reused across requests) │
                    │   │                                              │
                    │   ▼                                              │
                    │   UserService.create(req)     ← singleton bean  │
                    │   │  @Transactional → opens DB transaction      │
                    │   │                                              │
                    │   ▼                                              │
                    │   UserMapper.toEntity(req)    ← singleton bean  │
                    │   │  DTO → Entity: new User() + setName/setEmail│
                    │   │                                              │
                    │   ▼                                              │
                    │   UserRepository.save(user)                      │
                    │   │  Hibernate → SQL INSERT                      │
                    │   │  returns User with id populated              │
                    │   │  transaction commits                         │
                    │   │                                              │
                    │   ▼                                              │
                    │   UserMapper.toResponse(user) ← singleton bean  │
                    │   │  Entity → DTO: new UserResponse(id,name,..) │
                    │   │                                              │
                    │   ▼                                              │
                    │   Jackson ObjectMapper.writeValue(UserResponse)  │
                    │   │  serializes DTO → JSON string                │
                    │   │  only fields in record become JSON keys      │
                    │   │                                              │
                    │   ▼                                              │
                    │   Response: 201 {"id":1,"name":"John",...}       │
                    └──────────────────────────────────────────────────┘

 Objects created per request:
   1. CreateUserRequest  (DTO — input shape)    ← new instance
   2. User               (entity — DB row)      ← new instance
   3. UserResponse       (DTO — output shape)   ← new instance

 Singletons reused across ALL requests:
   • UserController   (1 instance for entire app lifetime)
   • UserService      (1 instance for entire app lifetime)
   • UserMapper       (1 instance for entire app lifetime)
   • UserRepository   (1 instance for entire app lifetime)
   • ObjectMapper     (Jackson — 1 instance, thread-safe)
```

---

## Concurrency Model

### Rails (Puma)

```
                         Puma Server
                    ┌─────────────────────┐
                    │  Master Process      │
                    │                      │
                    │  ┌── Worker 1 (fork) │    Each worker = separate
 Request A ────────►│  │  Thread 1 ◄──────│──  OS process with own
 Request B ────────►│  │  Thread 2 ◄──────│──  memory space
                    │  │  Thread 3        │
                    │  └──────────────────│
                    │  ┌── Worker 2 (fork)│
 Request C ────────►│  │  Thread 1 ◄──────│──  GIL/GVL limits true
 Request D ────────►│  │  Thread 2 ◄──────│──  parallelism per worker
                    │  └──────────────────│    (threads take turns)
                    └─────────────────────┘

 Key facts:
 - Puma default: 5 threads per worker, 2-4 workers
 - GVL means only 1 thread runs Ruby at a time per worker
 - True parallelism comes from multiple workers (processes)
 - Each request gets its own controller instance (new UsersController)
 - ActiveRecord has a connection pool per worker
 - Typical: ~10-20 concurrent requests
```

### Spring Boot (Tomcat — thread-per-request)

```
                         Tomcat (embedded)
                    ┌───────────────────────────┐
                    │  Single JVM Process        │
                    │                            │
                    │  Thread Pool (200 default) │
                    │  ┌─────────────────┐       │
 Request A ────────►│  │ Thread-1 ──────►│ controller ──► service ──► repo
 Request B ────────►│  │ Thread-2 ──────►│ controller ──► service ──► repo
 Request C ────────►│  │ Thread-3 ──────►│ controller ──► service ──► repo
     ...           │  │   ...           │       │
 Request N ────────►│  │ Thread-200 ───►│       │
                    │  └─────────────────┘       │
                    │                            │
                    │  All threads share:         │
                    │  • Same controller instance │  ← singleton!
                    │  • Same service instance    │  ← singleton!
                    │  • Same repo instance       │  ← singleton!
                    │  • HikariCP connection pool │
                    └───────────────────────────┘

 Key facts:
 - Default: 200 threads, 1 process
 - TRUE parallelism — JVM has no GIL
 - All 200 threads run simultaneously on your 14 cores
 - Controller/Service/Repo are SINGLETONS (1 instance shared by all threads)
 - Thread safety comes from: singletons have no mutable state
   (they only hold references to other singletons)
 - Each request creates its own DTOs and entities (short-lived, thread-local)
 - DB connections managed by HikariCP pool (default: 10 connections)
 - Typical: ~200 concurrent requests per instance
```

### Why Singletons Are Thread-Safe

```java
@Service
public class UserService {
    // These are just pointers to other singletons — set once at startup, never change
    @Autowired private UserRepository repo;    // immutable reference
    @Autowired private UserMapper mapper;      // immutable reference

    public UserResponse create(CreateUserRequest req) {
        // All local variables live on the thread's own stack — no sharing
        User user = mapper.toEntity(req);      // new object, this thread only
        User saved = repo.save(user);          // new object, this thread only
        return mapper.toResponse(saved);       // new object, this thread only
    }
}
```

```
 Thread-1 stack          Thread-2 stack          Shared heap
 ┌──────────────┐       ┌──────────────┐       ┌──────────────────┐
 │ req = {John} │       │ req = {Jane} │       │ userService (1)  │
 │ user = User  │       │ user = User  │       │ userRepo    (1)  │
 │ saved = User │       │ saved = User │       │ userMapper  (1)  │
 └──────────────┘       └──────────────┘       └──────────────────┘
  own objects            own objects             shared but stateless
```

**Rule:** singletons hold no per-request state → thread safe.
If you put a field like `private User currentUser` on a service → BUG. All threads would overwrite each other.

### Concurrency Comparison Summary

| | Rails (Puma) | Spring Boot (Tomcat) |
|---|---|---|
| Parallelism model | Multi-process + threads | Single process, multi-thread |
| True parallel threads? | No (GVL per worker) | Yes (JVM has no GIL) |
| Default concurrency | ~10-20 | ~200 |
| Controller instances | New per request | 1 singleton, shared |
| Service instances | New per call (or singleton) | 1 singleton, shared |
| Thread safety via | Isolation (each request = own objects) | Stateless singletons + local variables |
| Memory per concurrent req | High (process per worker) | Low (thread per request) |
| DB connection pool | Per worker process | Per JVM (HikariCP) |
| Scaling strategy | More workers = more RAM | More threads = same RAM |

---

## Rails Ecosystem → Java Ecosystem Map

Alternatives listed by popularity (most popular first).

### Web Framework

| Rails concept | Java alternatives | Notes |
|---|---|---|
| Rails (full framework) | **Spring Boot**, Quarkus, Micronaut, Jakarta EE, Vert.x | Spring Boot ~70% market share. Quarkus gaining for cloud-native (fast startup). Micronaut = compile-time DI |
| Sinatra / Roda (micro) | **Spring WebFlux**, Javalin, Spark, Helidon SE | Javalin = closest to Sinatra. Spark ≠ Apache Spark |
| Rack (HTTP interface) | **Jakarta Servlet API** | Every Java web framework sits on top of servlets |

### ORM / Database

| Rails concept | Java alternatives | Notes |
|---|---|---|
| ActiveRecord | **Hibernate (JPA)**, jOOQ, MyBatis, Spring Data JDBC, EclipseLink | Hibernate = dominant. jOOQ = type-safe SQL (no magic). MyBatis = raw SQL with mapping |
| ActiveRecord migrations | **Flyway**, Liquibase | Flyway = plain SQL files. Liquibase = XML/YAML/JSON changeset format |
| ActiveRecord scopes | **Spring Data JPA** (method-name queries), Querydsl, JPA Criteria API | `findByEmailAndStatus()` → auto-generated SQL |
| `rails db:seed` | No standard. Custom `CommandLineRunner` or Flyway seed scripts | Usually a class with `@Component` that runs on startup |
| Database Cleaner (test) | **@Transactional test rollback** (built-in), Testcontainers | Spring tests auto-rollback transactions by default |

### Background Jobs

| Rails concept | Java alternatives | Notes |
|---|---|---|
| Sidekiq / GoodJob / Resque | **Spring @Async**, Spring Batch, Quartz, Jobrunr | No single dominant like Sidekiq. @Async = simplest. Quartz = cron-like scheduling |
| ActiveJob (interface) | **Spring TaskExecutor** | Abstraction over thread pool execution |
| Sidekiq Pro (reliable) | **Kafka + Spring Kafka**, RabbitMQ + Spring AMQP, AWS SQS | Java world leans on message brokers instead of Redis-backed queues |
| Sidekiq cron / Whenever | **@Scheduled** (built-in), Quartz Scheduler | `@Scheduled(cron = "0 0 * * *")` — no extra gem needed |

### Testing

| Rails concept | Java alternatives | Notes |
|---|---|---|
| RSpec | **JUnit 5**, TestNG | JUnit 5 is the standard. TestNG rare in Spring projects |
| RSpec mocks / Mocha | **Mockito**, EasyMock | Mockito ~99% of Java projects |
| FactoryBot | **Instancio**, java-faker, Easy Random, Test fixtures | No single dominant. Many teams just use builder pattern or constructors |
| Capybara (browser test) | **Selenium**, Playwright (Java), Cypress (separate) | Selenium = old standard. Playwright gaining fast |
| VCR (HTTP recording) | **WireMock**, OkHttp MockWebServer | WireMock = most popular for stubbing HTTP |
| SimpleCov (coverage) | **JaCoCo** | Integrated with Gradle/Maven. Industry standard |
| Rails system tests | **@SpringBootTest + MockMvc**, REST Assured | MockMvc = test controllers without starting server. REST Assured = readable HTTP assertions |
| `rails test` / `rspec` | `./gradlew test` / `mvn test` | Build tool runs tests |

### Authentication / Authorization

| Rails concept | Java alternatives | Notes |
|---|---|---|
| Devise | **Spring Security**, Apache Shiro, Keycloak (external) | Spring Security = dominant but verbose. Keycloak = full auth server (like Auth0 self-hosted) |
| Pundit / CanCanCan | **Spring Security method-level** (`@PreAuthorize`), custom interceptors | `@PreAuthorize("hasRole('ADMIN')")` on methods |
| has_secure_password | **BCryptPasswordEncoder** (Spring Security) | `passwordEncoder.encode(raw)` / `.matches(raw, hash)` |
| JWT (ruby-jwt gem) | **jjwt**, Nimbus JOSE, Spring Security OAuth2 Resource Server | jjwt (io.jsonwebtoken) most popular standalone |
| OmniAuth (social login) | **Spring Security OAuth2 Client**, Keycloak | Built-in Google/GitHub/etc login support |

### API & Serialization

| Rails concept | Java alternatives | Notes |
|---|---|---|
| ActiveModelSerializers | **Jackson** (built-in), Gson, Moshi | Jackson = default in Spring. Handles 99% of cases. No extra dependency needed |
| Jbuilder (JSON templates) | **Jackson custom serializer**, JsonView | Rarely needed — DTOs + Jackson covers it |
| `as_json` / `to_json` | **Jackson ObjectMapper** | `objectMapper.writeValueAsString(obj)` |
| Grape (API framework) | Spring Boot is already API-first | No need for separate API gem |
| GraphQL (graphql-ruby) | **Spring for GraphQL**, Netflix DGS | Both production-ready. DGS from Netflix |
| `respond_to` format | **Content negotiation** (built-in) | `Accept: application/json` header → Jackson. `Accept: application/xml` → JAXB |
| Versioning (API) | URL path (`/api/v1/`), custom headers | No standard library — convention-based |

### Caching

| Rails concept | Java alternatives | Notes |
|---|---|---|
| Rails.cache (memory/Redis) | **Spring Cache** (`@Cacheable`), Caffeine, Ehcache, Redis (Lettuce/Jedis) | `@Cacheable("users")` on method = cached. Caffeine = in-memory default |
| Fragment caching | Not common in API projects. Thymeleaf cache for server-rendered HTML | Less relevant for JSON APIs |
| Russian doll caching | No direct equiv. Nested `@Cacheable` calls achieve similar | Pattern doesn't translate well |
| Low-level `Rails.cache.fetch` | **CacheManager.getCache("x").get(key)** | Direct cache API access |

### HTTP Client

| Rails concept | Java alternatives | Notes |
|---|---|---|
| Faraday / HTTParty | **RestClient** (Spring 6.1+), WebClient, OkHttp, Apache HttpClient | RestClient = newest, simplest. WebClient = reactive/async. OkHttp = standalone |
| Typhoeus (parallel HTTP) | **WebClient** (async), CompletableFuture + RestClient | WebClient is non-blocking by default |

### Configuration & Environment

| Rails concept | Java alternatives | Notes |
|---|---|---|
| `config/credentials.yml.enc` | **Spring Vault**, AWS Secrets Manager, env vars | No built-in encrypted credentials. External secret management |
| `config/environments/*.rb` | **application-{profile}.properties** | `application-dev.properties`, `application-prod.properties`. Activated via `SPRING_PROFILES_ACTIVE=prod` |
| `config/initializers/` | **@Configuration classes** | `@Bean` methods in config classes = initializer code |
| `.env` / dotenv gem | **spring-dotenv**, or just OS env vars | Less common — Spring profiles handle most cases |
| `config/locales/` (i18n) | **Spring MessageSource**, ICU4J | `messages_en.properties`, `messages_ru.properties` |

### Deployment & Infrastructure

| Rails concept | Java alternatives | Notes |
|---|---|---|
| Heroku | **Railway**, Render, Fly.io, AWS Elastic Beanstalk | All support Java. Spring Boot builds to single JAR — easy to deploy |
| Capistrano | **Docker + K8s**, Ansible, Jenkins pipeline | Java world is heavily Docker/K8s oriented |
| Puma (app server) | **Embedded Tomcat** (default), Jetty, Undertow, Netty | No separate server install — Spring Boot packages server inside JAR |
| Foreman / Procfile | **Docker Compose**, or just `java -jar app.jar` | Single JAR = single process. No Procfile needed |
| `rails s` | `./gradlew bootRun` or `mvn spring-boot:run` | Dev server with hot reload via Spring DevTools |

### Monitoring & Debugging

| Rails concept | Java alternatives | Notes |
|---|---|---|
| Pry / Byebug (debugger) | **IDE debugger** (IntelliJ/VS Code), JDB | Java debugging is IDE-first. Breakpoints > print statements |
| Better Errors (dev page) | **Spring Boot error page**, Spring DevTools | Auto error page in dev mode |
| New Relic / Scout | **Micrometer + Prometheus**, Datadog, New Relic, OpenTelemetry | Micrometer = metrics abstraction (like ActiveSupport::Notifications) |
| Rails logger | **SLF4J + Logback** (default), Log4j2 | `log.info("msg")` — Logback is included in Spring Boot |
| Bullet (N+1 detector) | **Hibernate query logging**, p6spy, spring-data-jpa-entity-graph | No auto-detector like Bullet. Manual: enable `spring.jpa.show-sql=true` |

### Frontend / Views

| Rails concept | Java alternatives | Notes |
|---|---|---|
| ERB / Slim / Haml | **Thymeleaf**, Freemarker, JSP (legacy) | Thymeleaf = dominant for server-rendered HTML. Most Spring projects are API-only now |
| Turbo / Hotwire | **HTMX + Thymeleaf**, Vaadin | HTMX gaining traction in Java world. Vaadin = full Java UI framework |
| Asset Pipeline / Propshaft | **Webpack/Vite** (separate frontend), Spring Resources | Usually a separate React/Vue app |
| ActionCable (WebSockets) | **Spring WebSocket**, STOMP, Socket.IO (Java) | Built-in WebSocket support in Spring |

### Code Quality & Tooling

| Rails concept | Java alternatives | Notes |
|---|---|---|
| RuboCop (linter) | **Checkstyle**, PMD, SpotBugs, SonarQube | Checkstyle = style. PMD = bugs. SonarQube = all-in-one |
| Bundler (dependencies) | **Gradle**, Maven | Gradle = modern (Groovy/Kotlin DSL). Maven = older (XML). Both manage deps + build |
| `Gemfile` | **build.gradle** / `pom.xml` | Dependency declaration file |
| `Gemfile.lock` | **gradle.lock** / `pom.xml` (versions pinned) | Gradle lock is opt-in. Maven pins versions directly |
| `rails generate` (scaffold) | **Spring Initializr** (start.spring.io), JHipster | Initializr = project generator. JHipster = full CRUD scaffold (closest to `rails g scaffold`) |
| `rails console` | **Spring Shell**, JShell (Java REPL) | JShell = plain Java REPL. No Rails-console equiv with full app context (major gap) |
| Rake tasks | **Gradle tasks**, Maven plugins, `CommandLineRunner` | `./gradlew myTask` or implement `CommandLineRunner` for startup tasks |

### Middleware & Cross-cutting

| Rails concept | Java alternatives | Notes |
|---|---|---|
| Rack middleware | **Servlet Filters**, Spring Interceptors | Filters = before/after HTTP. Interceptors = before/after controller |
| `before_action` / `after_action` | **@Aspect (AOP)**, `HandlerInterceptor`, Servlet Filter | AOP = cross-cutting via annotations. Interceptor = per-controller |
| ActiveSupport::Concern | **@Aspect**, default interface methods | AOP = "mix in" behavior via annotations |
| ActiveSupport (core ext) | **Apache Commons**, Guava, Lombok | Commons Lang (StringUtils, etc). Guava (collections). Lombok (reduce boilerplate) |
| ActiveSupport::Notifications | **Micrometer**, Spring ApplicationEvent | Event/metrics system |

### File Storage & Email

| Rails concept | Java alternatives | Notes |
|---|---|---|
| ActiveStorage | **Spring Content**, AWS SDK, MinIO client | No built-in equivalent. Usually direct S3 SDK |
| ActionMailer | **Spring Mail** (JavaMailSender), SendGrid SDK | `JavaMailSender.send(message)` — built-in |
| ActionText (rich text) | No direct equiv. Usually frontend handles rich text | Java APIs typically store raw HTML/markdown |

### Real-time & Async

| Rails concept | Java alternatives | Notes |
|---|---|---|
| ActionCable | **Spring WebSocket + STOMP**, Server-Sent Events | STOMP = messaging protocol over WebSocket |
| Turbo Streams | **SSE (Server-Sent Events)**, WebSocket push | SSE = simpler than WebSocket for one-way push |
| Async (Ruby 3 Fibers) | **Virtual Threads (Java 21+)**, CompletableFuture, Project Reactor | Virtual threads = game changer. Millions of lightweight threads. Closest to Ruby fibers |

### Key Gaps (Rails has it, Java world struggles)

| Rails feature | Java situation |
|---|---|
| `rails console` (full app REPL) | No real equivalent. JShell exists but no app context. Biggest gap |
| `rails generate scaffold` | JHipster exists but heavy. Most teams write boilerplate manually |
| Convention over configuration | Spring Boot improved this massively, but still more explicit than Rails |
| Single-file model (AR pattern) | Always split into entity + repo + service. More files, more typing |
| Hot reload in dev | Spring DevTools helps but not as seamless as Rails auto-reload |
| `rails db:rollback` | Flyway has no rollback by default (paid feature). Liquibase can rollback |
