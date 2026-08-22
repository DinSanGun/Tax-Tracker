# Clear Ledger – Architecture

Concise architecture reference for developers, code review, and interview prep.  
For user flows and package layout detail, see `docs/PROJECT_OVERVIEW.md`. For release planning, see `docs/LAUNCH_PLAN.md`.

_Last updated: 2026-08-22_

---

## Overview

Clear Ledger is a **local-first** Android app: categories define optional custom field schemas; invoices store aligned value lists and explicit service period modes. Clear Ledger does not send your data to the developer or operate its own cloud service; data is stored locally. User-initiated export and manual backup use Storage Access Framework (SAF). Android system backup may apply per device settings.

**Stack:** Kotlin · Jetpack Compose (Material 3) · Navigation Compose · Room v14 · MVVM-style separation

---

## Privacy and local-first design

- No account, developer cloud sync, backend, or analytics — app data in local Room storage
- Clear Ledger does not transmit data to the developer; Android system backup may apply per device settings
- Manual export and backup are **user-initiated** via Storage Access Framework
- Backup ZIPs contain **plaintext JSON** with sensitive financial data — users must store them securely
- Restore is full replace only; validation runs before any data is deleted

---

## MVVM layers

| Layer | Role |
|-------|------|
| **View (Compose)** | Stateless screens; collect `StateFlow` with `collectAsStateWithLifecycle()`; SAF launchers for file I/O |
| **ViewModel** | UI state, user intents, repository calls, derived pipelines (e.g. search/filter/sort) |
| **Repository** | Abstract persistence; map Room entities ↔ domain models |
| **Room** | SQLite source of truth (entities, DAOs, migrations, type converters) |

**Data flow:** `UI → ViewModel → Repository → Room → Repository → ViewModel → UI`

**ViewModel scoping:** Related screens share a parent `NavBackStackEntry` (e.g. invoice list, add/edit, details share `InvoiceListViewModel`). Repositories are wired in `MainActivity` and ViewModel factories — no DI framework.

---

## Domain model highlights

- **Category:** `customFieldTitles: List<String>` (max 10), `orderIndex`, `seedKey`, `userEdited`, colors, pinned defaults
- **Invoice:** core billing fields + `customFieldValues: List<String>` aligned **by index** to category titles
- **ServicePeriodMode:** `MONTH` or `DATE` — explicit source of truth; never inferred from dates alone
- **InvoiceCurrency:** ILS / USD display metadata only — amounts are not converted

See `core/domain/Models.kt` for full contracts.

---

## Repositories

| Repository | Responsibility |
|------------|----------------|
| `RoomCategoryRepository` | CRUD, seeded localization, manual reorder |
| `RoomInvoiceRepository` | CRUD per category, entity ↔ domain mapping |
| `RoomBackupRestoreRepository` | Transactional full-replace restore from `BackupPayload` |

Room database is currently **version 14** with incremental, non-destructive migrations. Avoid schema changes unless explicitly planned and tested.

---

## Compose screens and navigation

Routes are defined in `core/ui/Navigation.kt` (`Screen` sealed class). Category list is the start destination. Invoice screens require `categoryId`; edit/details require `invoiceId`.

**Key feature modules:**
- `feature/category/` — list, add/edit, reorder, export-all-data entry point (backup/restore/reset UI lives in `feature/settings/SettingsScreen`, sharing `CategoryListViewModel`)
- `feature/invoice/` — list, add/edit/details, search/filter/sort, CSV export
- `feature/settings/` — Settings hub screen (Language, Text size, Backup/Restore, Reset, About entry points), manual language switch, About

One-shot snackbar feedback uses `savedStateHandle` flags (e.g. after add/delete).

Back navigation uses a `popIfSafe()` helper (Navigation.kt) that checks `previousBackStackEntry != null` (a public API) before calling `popBackStack()`. All toolbar and programmatic back actions go through this helper. `CategoryList` also registers `BackHandler(enabled = true)` to absorb rapid system back presses. Together these ensure the start destination is never popped and the NavHost cannot be left blank.

---

## Export (human-readable)

**Not for restore.** Localized CSV/ZIP for spreadsheets and personal records.

| Entry | Output | Scope |
|-------|--------|--------|
| Invoice list → Export | `.csv` via SAF | `visibleInvoices` after search/filter/sort |
| Category list → Export all data | `.zip` via SAF | `categories.csv` + invoice CSVs per category with invoices |

**Implementation:** Pure Kotlin in `core/util/` (`InvoiceCsvExporter`, `AllDataZipExporter`, `Utf8CsvWriter`). Labels follow **app locale only** (English or Hebrew). ViewModels supply data/strings; Compose screens write bytes via SAF.

**Known limitation:** Google Sheets on Android may misread mixed English-header / Hebrew-data CSV. Do not add encoding hacks; LibreOffice and desktop Sheets are the target.

### Share Sheet (Aug 2026)

Both export flows above stage the same bytes they write to SAF in an app-cache file (`cacheDir/exports/`), then offer a **Share** action on the success snackbar. Sharing uses `androidx.core.content.FileProvider` (declared in `AndroidManifest.xml`, paths scoped to `cacheDir/exports/` via `res/xml/file_paths.xml`) to mint a temporary `content://` Uri, and `Intent.ACTION_SEND` + `Intent.createChooser` (see `core/util/ShareExportUtil.kt`) to open the standard Android Share Sheet with `FLAG_GRANT_READ_URI_PERMISSION`. No `file://` Uri is ever exposed, no broad storage permission is required, and there is no direct Gmail/Drive integration — the chooser simply lists whatever compatible apps are installed. The SAF save flow is unchanged; sharing is additive only.

---

## Backup and restore (app data)

**Backup** = restore-ready ZIP with single `backup.json` (raw enums, ISO dates, IDs, custom fields — not localized display strings).

**Restore** = full replace of local categories and invoices from a valid backup ZIP only.

| Step | Behavior |
|------|----------|
| Pick file | SAF `OpenDocument` |
| Validate | `BackupZipImporter` + `BackupValidator` — **before** any DB mutation |
| Confirm | Destructive dialog (EN + HE) only after validation succeeds |
| Apply | `RoomBackupRestoreRepository` — `withTransaction` delete all + insert; preserves original IDs |
| Post-restore | Seeding flags updated; `last_applied_language` set to current locale to prevent re-localization of restored category names on next launch; language preference unchanged |

CSV/ZIP exports are **rejected** at validation. Plaintext backup files contain sensitive data — user must store them securely.

Pure Kotlin utilities live in `core/util/backup/`.

---

## Localization

- Manual Hebrew / English switch; preference persisted via `LanguagePreferenceManager`
- Locale applied in `attachBaseContext`; Compose respects RTL/LTR via `LocalLayoutDirection`
- Resources: `values/` (English), `values-iw/` (Hebrew)
- Seeded categories re-localize on language change unless `userEdited == true`
- Reset all data seeds default categories using a locale-correct context built from the saved language preference, ensuring the correct locale is applied regardless of how the Compose context was captured
- Export headers and display values follow active app locale

---

## Validation strategy

Validation is layered so bad input never corrupts local data.

### Forms (UI)
- Category and invoice save flows validate required fields before repository calls
- Scroll-to-first-invalid on failed save; errors surfaced via inline state and snackbars

### Backup restore (defensive)
- `BackupValidator` checks format version, required fields, positive unique IDs, enum/date validity, orphan invoice rejection
- Failed validation → error snackbar; **current data unchanged**
- Successful validation → user must confirm destructive replace

### Domain invariants (preserve in code and tests)
1. Custom field values align by index with category titles — do not filter/reindex in ways that break alignment
2. Service period mode is explicit — never infer from dates
3. Category list sorted by `orderIndex`, not name
4. Export vs backup vs restore remain distinct product paths
5. Hidden category fields (`supplierName`, `pinnedDefaults`) round-trip on update

### Automated tests (S9 — done)
- Unit tests for export utilities, backup mapper/exporter/importer/validator
- Targeted tests for custom-field alignment, CSV edge cases, invalid restore rejection, EN/HE export labels

Optional stretch: Room migration smoke tests, instrumented restore transaction test.

---

## Testing and CI

Local quality gate (mirrored in CI):

```bash
./gradlew test
./gradlew lintDebug
./gradlew assembleDebug
```

**GitHub Actions** (`.github/workflows/android-ci.yml`) runs the same commands on push to `main` and on pull requests (Temurin 17).

See `docs/RELEASE.md` for release build and Play Store checklist.

### Interview demo flow

Category list → add/edit category → invoice list → search/filter/sort → CSV export → create backup → restore (with validation) → switch language → highlight local-first / no cloud design. Full checklist in `docs/RELEASE.md`.

---

## Related docs

| File | Purpose |
|------|---------|
| `docs/PROJECT_OVERVIEW.md` | Full technical overview with package map |
| `docs/ai-context.md` | Concise AI-assisted development context |
| `docs/LAUNCH_PLAN.md` | Pre-release execution plan (S9–S17) |
| `docs/RELEASE.md` | Build, test, and Play Store release checklist |

`docs/ARCHITECTURE_SUMMARY.pdf` is a historical snapshot — regenerate from markdown when needed.
