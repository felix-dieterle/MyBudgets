# MyBudgets – Contributor Instructions

## Project Overview
MyBudgets is a personal finance management app for individuals and families.
- **Android app** (Kotlin, MVVM, Material Design 3) in `app/`
- **PHP REST backend** in `public/apps/finn/` → URL: `server/apps/finn/api.php`

---

## Repository Structure

```
MyBudgets/
├── app/                             # Android app (Kotlin)
│   └── src/main/java/de/mybudgets/app/
│       ├── data/                    # Room DB, models, repositories, Retrofit API
│       ├── di/                      # Hilt DI modules
│       ├── ui/                      # Fragments + Adapters (one package per screen)
│       ├── util/                    # Helpers (JSON export, formatting, pattern matching)
│       └── viewmodel/               # ViewModels
├── public/apps/finn/                # PHP REST API
│   ├── api.php                      # Entry point / router
│   ├── config.php                   # Configuration (copy → config.local.php for dev)
│   ├── db.php                       # PDO MySQL singleton
│   ├── endpoints/                   # One file per resource
│   └── sql/schema.sql               # MySQL schema + seed data
├── keystore/debug.keystore          # Persistent debug signing key (committed intentionally)
├── gradle/libs.versions.toml        # Dependency version catalog
└── .github/INSTRUCTIONS.md         # This file
```

---

## Android Architecture

- **Pattern:** MVVM
- **DI:** Hilt (`@HiltAndroidApp`, `@AndroidEntryPoint`, `@HiltViewModel`)
- **Database:** Room – entities in `data/model/`, DAOs in `data/db/`
- **Network:** Retrofit + OkHttp → `data/api/`
- **Navigation:** Jetpack Navigation Component (`nav_graph.xml`)
- **UI:** Material Design 3, ViewBinding

### Key Features
| Feature | Location |
|---------|----------|
| Real + virtual accounts | `data/model/Account.kt` (isVirtual, parentAccountId) |
| Auto-categorization (regex) | `util/PatternMatcher.kt` + `Category.pattern` |
| Category hierarchy (2 levels) | `data/model/Category.kt`, seeded in `util/DataSeeder.kt` |
| Labels (multiple per tx) | `data/model/Label.kt` + `TransactionLabel.kt` |
| JSON import / export | `util/JsonImportExport.kt` |
| Gamification badges | `data/model/GamificationBadge.kt` + `GamificationRepository.kt` |
| AI support (openrouter.ai) | `data/api/ApiService.kt` → `POST ai/suggest` |
| Backend sync (offline-first) | `data/repository/TransactionRepository.kt` + `/sync` endpoint |
| Configurable backend URL | `ui/settings/SettingsFragment.kt` → SharedPreferences |

---

## Android Build

### Prerequisites
- JDK 17+, Android SDK API 34

```bash
# Debug APK (signed with persistent debug keystore)
./gradlew assembleDebug

# Output: app/build/outputs/apk/debug/app-debug.apk
```

### Debug Keystore (committed intentionally for consistent CI signing)
| Field | Value |
|-------|-------|
| File | `keystore/debug.keystore` |
| Store password | `mybudgets` |
| Key alias | `mybudgets` |
| Key password | `mybudgets` |

> ⚠️ Never commit a production keystore.

---

## PHP Backend

### Setup
```bash
mysql -u root -p < public/apps/finn/sql/schema.sql
```
Set environment variables: `DB_HOST`, `DB_NAME`, `DB_USER`, `DB_PASS`, `MYBUDGETS_API_SECRET`.

### Auth
Every request needs header: `X-API-Key: <API_SECRET>`

### Endpoints
| Method | Path | Description |
|--------|------|-------------|
| GET/POST/PUT/DELETE | `/accounts` | Account CRUD |
| GET/POST/PUT/DELETE | `/transactions` | Transaction CRUD |
| GET/POST/PUT/DELETE | `/categories` | Category CRUD |
| GET/POST/PUT/DELETE | `/labels` | Label CRUD |
| POST | `/sync` | Bulk push+pull |
| GET | `/version` | API version |

---

## Code Style
- Kotlin: follow official conventions, no unnecessary comments
- PHP: PSR-12, one endpoint per file
- Naming: `PascalCase` classes, `camelCase` functions/vars
