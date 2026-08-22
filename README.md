# Clear Ledger

[![Android CI](https://github.com/DinSanGun/clear-ledger/actions/workflows/android-ci.yml/badge.svg)](https://github.com/DinSanGun/clear-ledger/actions/workflows/android-ci.yml)

**Simple Bill & Tax Tracker**

Clear Ledger is a local-first Android app for structured tracking of invoices, bills, and tax-related expenses. Users organize records by customizable categories and fields, search and filter invoices, and export or back up data on their own terms.

The app targets real-world billing and tax workflows, including Israel-specific fields, with full Hebrew and English support.

---

## Privacy-first / local-first

- **No account, no developer cloud sync, no backend** — categories and invoices are stored locally in a Room (SQLite) database
- **Clear Ledger does not send your data to the developer** — no analytics or developer-operated upload
- **Android system backup** — if enabled on your device, Android/Google may back up app data per your device and Google Account settings (see `docs/privacy-policy.md`)
- **Manual export and backup via Storage Access Framework (SAF)** — files go only where you save them
- **Optional sharing via the standard Android Share Sheet** — after an export completes, you may choose to share that exact file through any app you already have installed (email, messaging, cloud drives, etc.); the app has no direct Gmail/Drive integration and does not share anything automatically
- **Backup files are plaintext JSON** — treat them like private financial documents

---

## Screenshots

7 screenshots at [`docs/assets/screenshots/play-store/`](docs/assets/screenshots/play-store/) (EN × 6, HE × 1). Upload to Play Console during S15.

---

## Features

### Category-Based Organization
- Expenses and invoices organized by categories with color theming
- Dynamic custom field definitions (up to 10 titles per category)
- Built-in seeded categories for common use cases (e.g. Electricity, Arnona, Water)
- Manual category reordering with persisted `orderIndex` (stable across locale changes)
- Category card overflow menu for edit, delete, and reorder actions

### Flexible Invoice Model
- Core fields: document number, amount, vendor, payment status, notes
- Category-specific custom field values (ordered list aligned to category titles)
- Explicit service period mode (`MONTH` or `DATE`) — never inferred from dates
- Separate payment date and due date from service period
- Document type, payment method (including custom “Other”), and credit payment count
- Currency stored and displayed as metadata (ILS / USD); amounts are not converted

### Invoice List Productivity
- Search by invoice number or amount with mode selector
- Filter by payment status and service period range (bottom sheet with Apply / Clear)
- Sort by date or amount (ascending / descending)
- Scope-aware retrieval pipeline: `sourceInvoices` → search → filter → sort → `visibleInvoices`

### Localization
- Full Hebrew and English support with manual language switching
- Persistent language preference and proper RTL / LTR layout handling
- Locale-aware seeded categories (user-edited seeded rows are protected from overwrite)
- Export headers and display values follow the active app locale

### Polished UI (Jetpack Compose)
- Category color theming with contrast-aware top app bars
- Picker-first date inputs with formatted display
- Form validation with scroll-to-first-invalid on save
- Swipe-to-dismiss snackbars and improved dropdown positioning
- Consistent invoice and category card layouts
- Edit Category top-bar Save action with discard warning for unsaved changes
- Invoice list active filter indication and clear-filters action
- Consistent dialog action color semantics: destructive actions (Delete/Reset/Restore/Remove/Discard) in error red; cancel/dismiss neutral; positive actions in primary
- Rapid repeated toolbar or system back presses handled safely — guarded navigation prevents popping the start destination and leaving a blank screen
- "Add custom field" in category forms uses an outlined button with + icon; invoice custom fields visually match standard form fields
- Portrait-optimized layout

### Data Portability

The app offers three distinct data portability features. **Do not confuse them:**

| Feature | Menu location | Format | Purpose |
|---------|---------------|--------|---------|
| **Export** (invoice list) | Invoice overflow → Export | Localized CSV | Human-readable spreadsheet of currently visible invoices |
| **Export all data** | Category list overflow → Export | ZIP with CSV files | Human-readable archive for review, accountants, or records |
| **Backup** | Category list → Settings → Create backup | ZIP with `backup.json` | Restore-ready app data snapshot |
| **Restore** | Category list → Settings → Restore from backup | Reads backup ZIP | Full replacement of local app data from a backup |

#### Export (human-readable)
- **Invoice list:** exports currently visible invoices (after search, filter, and sort) to localized CSV via Storage Access Framework (SAF)
- **Category list:** exports all data to a ZIP (`categories.csv` + one invoice CSV per category that has invoices)
- Localized English/Hebrew headers and display values; custom fields as CSV columns
- UTF-8 with conditional BOM for spreadsheet compatibility (LibreOffice, desktop Google Sheets)
- **Not for restore** — CSV/ZIP exports cannot be imported back into the app
- **Share the exported file:** after a successful export, the confirmation snackbar includes a **Share** action that opens the standard Android Share Sheet for that exact file (`text/csv` or `application/zip`), via a secure temporary `content://` link — no separate Gmail/Drive integration, no broad storage permission

#### Backup and restore (app data)
- **Create backup:** writes a ZIP containing a single `backup.json` with restore-oriented data (categories, invoices, IDs, order, colors, custom fields, service period modes, currencies, raw enum values, ISO dates, metadata, format version)
- **Restore backup:** user selects a backup ZIP via SAF; app validates `backup.json`, shows a destructive confirmation dialog, then **fully replaces** current local categories and invoices
- Restore is **full replace only** — not merge, not selective
- Validation runs **before** any data is deleted; if validation fails, current data stays unchanged
- Restore uses a Room transaction (delete + insert) and preserves original category and invoice IDs
- After a successful restore, seeding flags are updated so first-run default seeding does not duplicate restored data
- After restore, `last_applied_language` is updated to prevent the next launch from re-localizing seeded backup category names
- Language preference is **not** restored or modified
- Backup files are **plaintext JSON** and contain sensitive financial/user data — treat them like private documents

> **Before restoring:** create a fresh backup of your current data if you may want to return to the present state. Restore cannot be undone unless you have another backup.

### Local Persistence
- Offline-first design with Room (SQLite), currently at schema version 14
- Incremental migrations for backward compatibility
- First-run seeding with idempotent preference flags

---

## Tech Stack

- **Language:** Kotlin
- **UI:** Jetpack Compose (Material 3)
- **Navigation:** Navigation Compose
- **Architecture:** MVVM-style separation (UI / ViewModel / Repository)
- **Persistence:** Room (schema version 14) with KSP, entity ↔ domain mapping
- **State:** ViewModels + `StateFlow`, lifecycle-aware collection
- **Localization:** Android string resources (`values/`, `values-iw/`) with RTL support
- **Build:** Gradle (KTS), version catalog
- **CI:** GitHub Actions (Temurin 17) — `test`, `lintDebug`, `assembleDebug`

**Package:** `com.dinyairsadot.clearledger` · **Version:** `1.0.0` (versionCode 1)

---

## Architecture Overview

- **MVVM:** Compose screens render state; ViewModels own UI state and call repositories; repositories map Room entities ↔ domain models
- **No DI framework:** repositories wired in `MainActivity` and ViewModel factories
- **Feature modules:** `feature/category/`, `feature/invoice/`, `feature/settings/` with shared `core/` layer
- **Export / backup:** pure Kotlin utilities in `core/util/`; file I/O via SAF in Compose screens
- **Restore safety:** validate backup JSON before any DB mutation; full replace in a single Room transaction

See [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) for MVVM layers, validation strategy, and interview-oriented detail. See [`docs/PROJECT_OVERVIEW.md`](docs/PROJECT_OVERVIEW.md) for package layout.

---

## Project Structure

- `core/domain/` — domain models and repository interfaces
- `core/data/` — Room entities, DAOs, converters, repositories
- `core/ui/` — navigation, shared UI helpers
- `core/util/` — CSV/ZIP export and backup/restore utilities
- `feature/category/` — category list, add/edit, reorder, export/backup/restore
- `feature/invoice/` — invoice list, add/edit/details, search/filter/sort, CSV export
- `feature/settings/` — Settings hub, language settings, about

---

## Testing & CI

**Unit tests** cover export utilities, backup mapper/exporter/importer/validator, custom-field alignment, CSV edge cases, and invalid-restore rejection (EN/HE export labels included).

**GitHub Actions** (`.github/workflows/android-ci.yml`) runs on push to `main` and on pull requests:

```bash
./gradlew test
./gradlew lintDebug
./gradlew assembleDebug
```

For release build commands and Play Store checklist, see [`docs/RELEASE.md`](docs/RELEASE.md).

---

## Release Status

Clear Ledger is **feature-complete** for MVP and in late pre-release preparation.

| Stage | Status |
|-------|--------|
| S9 — Targeted test hardening | Done |
| S10 — GitHub Actions CI | Done |
| S11 — Release polish | Done |
| S12 — Project documentation & release identity (S12A–C) | Done |
| **S14 — Privacy policy & Play Store materials** | **Complete except Play Console external tasks** |
| S15 — Internal Play Store testing (signing + upload) | Pending |
| S16 — Launch blocker fixes | Pending |
| S17 — Production release | Pending |

**Release build:** `versionName = 1.0.0`, unsigned `./gradlew bundleRelease` documented; signing deferred to S15.

See [`docs/LAUNCH_PLAN.md`](docs/LAUNCH_PLAN.md) for the full execution plan (S9–S17).

### Not yet implemented
- Google Play production release
- Cloud sync, encryption, automatic backup, or selective merge restore

---

## Documentation

| File | Purpose |
|------|---------|
| `docs/PROJECT_OVERVIEW.md` | Technical architecture overview |
| `docs/ARCHITECTURE.md` | MVVM, Room, export/backup/restore, localization, validation |
| `docs/RELEASE.md` | Test, build, and Play Store release checklist |
| `docs/ai-context.md` | Concise context for AI-assisted development |
| `docs/CHANGELOG.md` | User-facing development history |
| `docs/LAUNCH_PLAN.md` | Pre-release execution plan (S9–S17) |
| `docs/privacy-policy.md` | Privacy policy (hosted at `https://dinsangun.github.io/clear-ledger/privacy-policy`) |
| `docs/PLAY_STORE_MATERIALS.md` | Play Store listing copy, Data Safety guidance, asset checklist |
| `docs/ARCHITECTURE_SUMMARY.pdf` | Historical architecture snapshot — may be outdated; regenerate from current docs when needed |

---

## Notes

This project emphasizes:
- clean data modeling with explicit domain invariants
- extensibility via dynamic custom fields
- practical, real-world billing scenarios
- maintainable Android architecture

---

## License

This project is licensed under the MIT License.  
See the [LICENSE](LICENSE) file for details.
