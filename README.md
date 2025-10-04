# Caveman Project 🦴

A learning project for exploring Clojure web development patterns, featuring a full-stack web application with PostgreSQL, background jobs, and REPL-driven development.

## Project Overview

This project is based on the amazing tutorial https://caveman.mccue.dev.
It is a "caveman-themed" web application built to learn and demonstrate idiomatic Clojure patterns for web development. It showcases:

- **System lifecycle management** with the "system map" pattern
- **REPL-driven development** workflow
- **Database-backed background jobs** using triggers
- **Server-side HTML rendering** with Hiccup
- **Testing with real databases** using Testcontainers
- **Modern Clojure tooling** (Portal, next.jdbc, Reitit, etc.)

## Architecture

### Tech Stack

- **Language:** Clojure 1.12.0
- **Web Server:** Ring + Jetty
- **Routing:** Reitit (data-driven routing)
- **HTML:** Hiccup (Clojure → HTML)
- **Database:** PostgreSQL 17
- **Connection Pool:** HikariCP
- **Database Access:** next.jdbc
- **SQL Generation:** HoneySQL
- **Background Jobs:** Proletarian (Postgres-backed queue)
- **Migrations:** MyBatis Migrations
- **Testing:** Kaocha + Testcontainers
- **Dev Tools:** Portal (data visualization), nREPL

### Project Structure

```
caveman-project/
├── src/caveman/              # Application source code
│   ├── main.clj              # Entry point for production
│   ├── system.clj            # System lifecycle management
│   ├── routes.clj            # Root router
│   ├── middlewares.clj       # Ring middleware stacks
│   ├── jobs.clj              # Background job dispatcher
│   ├── page_html/            # HTML layout components
│   ├── cave/                 # Cave feature (routes + jobs)
│   ├── hello/                # Hello world route
│   ├── goodbye/              # Goodbye route
│   └── static/               # Static file serving
├── dev/                      # Development-only code
│   ├── user.clj              # REPL helpers (start/stop system)
│   └── portal.clj            # Portal debugging setup
├── test/caveman/             # Tests
│   └── test_system.clj       # Testcontainers test infrastructure
├── migrations/               # Database migrations
│   ├── scripts/              # SQL migration files
│   └── environments/         # Migration configuration
├── res/                      # Resources (static files)
├── deps.edn                  # Dependencies and aliases
├── Justfile                  # Task runner (like Make)
└── docker-compose.yaml       # Postgres container for development
```

## Key Patterns & Concepts

### 1. System Map Pattern

The application uses a "system map" to manage stateful components:

```clojure
{::system/env          ; Environment configuration
 ::system/cookie-store ; Session store
 ::system/db           ; Database connection pool
 ::system/worker       ; Background job worker
 ::system/server}      ; HTTP server
```

Components are started in dependency order and stopped in reverse order. This pattern enables:
- Clean startup/shutdown
- REPL-driven development (restart components without restarting JVM)
- Dependency injection (pass system map to handlers)

### 2. Database Triggers → Background Jobs

A unique pattern for decoupling operations:

1. **Insert a cave** → Database trigger fires
2. **Trigger creates job** → Inserts into `proletarian.job` table
3. **Worker polls jobs** → Processes job asynchronously
4. **Job handler runs** → Creates a hominid for the cave

Benefits:
- HTTP requests return immediately
- Database guarantees job creation (same transaction)
- Automatic retries on failure
- Can scale workers independently

See: `migrations/scripts/20251003180736_cave_insert_trigger.sql`

### 3. REPL-Driven Development

Development workflow centers around the REPL:

```clojure
;; In dev/user.clj
(start-system!)        ; Start everything
(db)                   ; Get database connection
(jdbc/execute! (db) ...) ; Run queries
(restart-system!)      ; Reload changes
(stop-system!)         ; Clean shutdown
```

The system supports hot reloading in development mode - just re-evaluate changed functions!

### 4. Portal Integration

[Portal](https://github.com/djblue/portal) provides visual inspection of data:

```clojure
(tap> {:my "data"})  ; Send data to Portal UI
```

Portal opens in your browser and shows beautiful, interactive views of your data.

### 5. Testing with Real Databases using Testcontainers

Tests use [Testcontainers](https://testcontainers.com/) to run real PostgreSQL:

```clojure
(test-system/with-test-db
  (fn [db]
    ;; db is a fresh, migrated database
    (jdbc/execute! db ["INSERT INTO ..."])
    (is (= ...))))
```

Each test gets an isolated database cloned from a template (fast!).

## Getting Started

### Prerequisites

- **Java 23+** (check: `java -version`)
- **Clojure CLI** (install: https://clojure.org/guides/install_clojure)
- **Docker** (for Postgres and tests)
- **Just** (optional task runner, install: `brew install just`)

### Initial Setup

1. **Clone the repository**
   ```bash
   git clone <your-repo> caveman-project
   cd caveman-project
   ```

2. **Create `.env` file** in project root:
   ```bash
   PORT=8080
   ENVIRONMENT=development
   POSTGRES_USERNAME=postgres
   POSTGRES_PASSWORD=yourpassword
   ```

3. **Start PostgreSQL**
   ```bash
   docker-compose up -d
   ```

4. **Run migrations**
   ```bash
   # Manual migration with MyBatis Migrations
   migrate up
   ```

### Running the Application

#### Option 1: REPL Development (Recommended)

1. **Start nREPL server**
   ```bash
   just nrepl
   # or: clojure -M:dev -m nrepl.cmdline
   ```

2. **Connect your editor** (Calva, Cursive, CIDER, Emacs, etc.)

3. **Start Portal** (optional but recommended)
   ```clojure
   (require '[dev.portal :as portal])
   (portal/start-portal!)
   ```

4. **Start the system**
   ```clojure
   (start-system!)
   ```

5. **Visit** http://localhost:8080

6. **Make changes** and re-evaluate functions - no restart needed!

#### Option 2: Command Line

```bash
just run
# or: clojure -M -m caveman.main
```

Visit http://localhost:8080

### Available Commands (Justfile)

```bash
just help          # List all commands
just run           # Run the application
just nrepl         # Start nREPL server
just test          # Run tests
just format        # Format code
just format_check  # Check formatting
just lint          # Run clj-kondo linter
just outdated      # Check for outdated dependencies
```

## Development Workflow

### Typical Day of Development

1. Start REPL: `just nrepl`
2. Connect editor
3. Start Portal: `(portal/start-portal!)`
4. Start system: `(start-system!)`
5. Make changes to code
6. Re-evaluate changed functions in editor
7. Test at REPL with `(tap> ...)` to inspect data
8. For system-level changes: `(restart-system!)`
9. Done: `(stop-system!)`

### Adding a New Feature

1. **Create route namespace** (e.g., `src/caveman/my_feature/routes.clj`)
2. **Define routes function** that returns Reitit route data
3. **Register in `caveman.routes/routes`**
4. **Add background job handlers** in `src/caveman/my_feature/jobs.clj` (optional)
5. **Register handlers** in `caveman.jobs/handlers` (optional)
6. **Create database trigger** in migrations if needed (optional)
7. **Re-evaluate** or `(restart-system!)`

### Database Changes

1. **Create migration**
   ```bash
   migrate new "migration_name"
   ```

2. **Edit the generated SQL file** in `migrations/scripts/`

3. **Run migration**
   ```bash
   migrate up
   ```

## Routes

| Method | Path           | Description                |
|--------|----------------|----------------------------|
| GET    | /              | Hello world with DB query  |
| GET    | /goodbye       | Goodbye message            |
| GET    | /cave          | List caves + creation form |
| POST   | /cave/create   | Create new cave            |
| GET    | /favicon.ico   | Favicon                    |
| GET    | /nested/file   | Static file example        |

## Database Schema

### `prehistoric.cave`
- `id` (uuid, primary key)
- `created_at` (timestamptz)
- `updated_at` (timestamptz, auto-updated by trigger)
- `description` (text)

**Trigger:** On insert → creates background job

### `prehistoric.hominid`
- `id` (uuid, primary key)
- `created_at` (timestamptz)
- `updated_at` (timestamptz)
- `name` (text)
- `cave_id` (uuid, foreign key to cave)

**Created by:** Background job when cave is inserted

### `proletarian.job`
- (Created by Proletarian migrations)
- Stores background jobs with retry logic

## Testing

Run all tests:
```bash
just test
# or: clojure -M:dev -m kaocha.runner
```

Tests automatically:
- Start PostgreSQL in Docker (Testcontainers)
- Run migrations against template database
- Create isolated database per test
- Clean up after each test

## Code Quality

### Linting
```bash
just lint  # Run clj-kondo
```

### Formatting
```bash
just format_check  # Check if formatted
just format        # Auto-format code
```

### Check for Outdated Dependencies
```bash
just outdated
```

## Environment Variables

| Variable            | Description              | Example       |
|---------------------|--------------------------|---------------|
| PORT                | HTTP server port         | 8080          |
| ENVIRONMENT         | development or production| development   |
| POSTGRES_USERNAME   | Database user            | postgres      |
| POSTGRES_PASSWORD   | Database password        | yourpassword  |

## Learning Resources

This project uses many Clojure idioms and libraries. Here are resources for learning more:

### Clojure Fundamentals
- [Clojure for the Brave and True](https://www.braveclojure.com/)
- [Clojure Official Docs](https://clojure.org/)
- [ClojureDocs](https://clojuredocs.org/) - Community documentation

### Libraries Used
- [Ring](https://github.com/ring-clojure/ring) - HTTP abstraction
- [Reitit](https://github.com/metosin/reitit) - Routing
- [Hiccup](https://github.com/weavejester/hiccup) - HTML generation
- [next.jdbc](https://github.com/seancorfield/next-jdbc) - Database access
- [HoneySQL](https://github.com/seancorfield/honeysql) - SQL generation
- [Proletarian](https://github.com/msolli/proletarian) - Background jobs
- [Portal](https://github.com/djblue/portal) - Data visualization

### Patterns
- [Component](https://github.com/stuartsierra/component) - System architecture (similar pattern)
- [REPL-Driven Development](https://clojure.org/guides/repl/introduction)

## Tips for the future, in case I forget

### Remember These Patterns

1. **Var indirection** (`#'my-function` instead of `my-function`)
   - Allows hot reloading from REPL
   - Use in development mode

2. **Namespace aliases** (`:as-alias` in `ns` form)
   - Use namespaced keywords without requiring the namespace
   - Prevents circular dependencies

3. **Threading macros** (`->`, `->>`, `as->`)
   - Clean up nested function calls
   - `as->` when you need to pass to different positions

4. **Rich comment blocks**
   - Use `(comment ...)` for REPL experiments
   - Won't execute on load but can be evaluated manually

5. **tap>** when debugging
   - Debug by sending data to Portal
   - Much better than `println`

### Common Gotchas

- **Reflection warnings:** If you see them, add type hints or `set! *warn-on-reflection* true`
- **Keywords vs strings:** Database/JSON boundaries often involve conversion
- **CSRF tokens:** All POST requests need `(anti-forgery-field)` in the form

## Future Enhancements

Ideas for extending this project:

- [ ] Add authentication/authorization
- [ ] Add API endpoints (RESTful JSON API)
- [ ] Add WebSocket support for real-time updates
- [ ] Add more comprehensive tests
- [ ] Add logging with structured logging (e.g., timbre)
- [ ] Add metrics/monitoring (e.g., Prometheus)
- [ ] Add production deployment (uberjar, Docker)
- [ ] Add ClojureScript frontend
- [ ] Add email sending via background jobs
- [ ] Add scheduled jobs (cron-like)

## License

Educational project - do whatever you want with it!

## Acknowledgments

Built as a learning project to understand Clojure web development patterns. Thanks to the Clojure community for excellent libraries and documentation!
But the special thanks go to [@mccue](https://github.com/mccue) for the amazing caveman tutorial!

---

**Note to future self:** You built this to learn Clojure! The patterns here are intentionally explicit to help you remember the concepts. Don't be afraid to refactor as you learn more. Happy hacking! 🚀

