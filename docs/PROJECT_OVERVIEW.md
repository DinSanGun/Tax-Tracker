# Clear Ledger - Project Overview

Technical overview of the Clear Ledger Android app for future development and interview context.  
For the pre-release execution plan, see `docs/LAUNCH_PLAN.md`. For architecture patterns and validation, see `docs/ARCHITECTURE.md`. For AI-assisted work, see `docs/ai-context.md`. For release checklist, see `docs/RELEASE.md`.

> **Note:** `docs/ARCHITECTURE_SUMMARY.pdf` is a historical snapshot and may not reflect the current codebase. Prefer this file and `docs/ai-context.md` until the PDF is regenerated.

---

## A. App Purpose and Main User Flows

**Purpose:** Local-first Android app for managing bills and tax invoices by category. Users create categories (e.g. Electricity, Water, Arnona), assign colors and optional custom field schemas, then track invoices within each category.

**Main flows:**

1. **Category management**
   - Category list → add / edit / delete category
   - Manual reorder mode (persisted `orderIndex`) — Category list overflow → Order Categories
   - **Export all data** → Category list overflow → Export — ZIP via Storage Access Framework (`categories.csv` + invoice CSVs per category with invoices) — human-readable, not for restore
   - After export completes, an optional **Share** action on the confirmation snackbar opens the standard Android Share Sheet for that ZIP
   - **Settings** (gear icon on Category list) → Language, Text size, Create backup, Restore from backup, Reset all data, About / Privacy Policy

2. **Invoice management**
   - Select category → invoice list (search, filter, sort)
   - Add / view details / edit / delete invoice
   - **Export** → localized CSV of currently visible invoices via SAF
   - After export completes, an optional **Share** action on the confirmation snackbar opens the standard Android Share Sheet for that CSV

3. **Navigation pattern**
   - Category list is the start destination
   - Invoice screens require `categoryId`; edit/details require `invoiceId`
   - Related screens share a parent ViewModel via navigation back-stack entry

---

## B. Architecture Pattern

**Pattern:** MVVM with Jetpack Compose, Navigation Compose, and Room.

| Layer | Responsibility |
|-------|----------------|
| **View** | Stateless Compose screens; collect `StateFlow` with `collectAsStateWithLifecycle()` |
| **ViewModel** | UI state, user intents, repository calls, derived list pipelines |
| **Repository** | Abstract persistence; map entities ↔ domain models |
| **Room** | SQLite source of truth (entities, DAOs, migrations, type converters) |

**Data flow:** `UI → ViewModel → Repository → Room → Repository → ViewModel → UI`

**ViewModel scoping:**
- `CategoryListViewModel` — shared across category list, add/edit category
- `InvoiceListViewModel` — shared across invoice list, add/edit, details
- `LanguageViewModel` — language settings screen

**Key decisions:**
- Repositories created in `MainActivity` and passed into the nav host (no DI framework)
- Invoice list keeps `sourceInvoices` and recomputes `visibleInvoices` after search/filter/sort
- Service period semantics use explicit `ServicePeriodMode` — never inferred from dates
- Currency (`InvoiceCurrency`) is display metadata; amounts are not converted

---

## C. Package Structure

```
com.dinyairsadot.clearledger/
├── MainActivity.kt              # Entry, DB init, seeding, locale, repo wiring
├── core/
│   ├── domain/
│   │   ├── Models.kt            # Category, Invoice, enums
│   │   ├── CategoryRepository.kt
│   │   └── InvoiceRepository.kt
│   ├── data/
│   │   ├── ClearLedgerDatabase.kt  # Room v14, migrations
│   │   ├── dao/, entities/, converters/, repositories/
│   │   │   └── RoomBackupRestoreRepository.kt
│   │   ├── LanguagePreferenceManager.kt
│   │   └── SeedingPreferenceManager.kt
│   └── ui/
│       ├── Navigation.kt        # Routes, NavHost, ViewModel factories
│       ├── CategoryColorUtils.kt
│       ├── DropdownPositioning.kt
│       ├── SwipeDismissSnackbarHost.kt
│       └── AppSnackbar.kt
│   └── util/
│       ├── InvoiceCsvExporter.kt, InvoiceCsvExportLabels.kt
│       ├── Utf8CsvWriter.kt, AllDataZipExporter.kt
│       ├── CategoriesCsvLabels.kt, AllExportData.kt
│       ├── ShareExportUtil.kt   # Share Sheet: FileProvider Uri + ACTION_SEND chooser
│       └── backup/
│           ├── BackupFormat.kt, BackupDtos.kt, BackupMapper.kt
│           ├── BackupZipExporter.kt, BackupZipImporter.kt
│           └── BackupValidator.kt, BackupValidationResult.kt
├── feature/
│   ├── category/                # List, add, edit, reorder, CategoryForm
│   ├── invoice/                 # List, add, edit, details, search/filter/sort
│   └── settings/                # SettingsScreen (hub), LanguageSettingsScreen, AboutScreen
├── ui/theme/                    # Material 3 theme
└── archive/                     # Unused in-memory repos (reference only)
```

---

## D. Navigation

**Library:** Navigation Compose

**Routes** (`Screen` sealed class in `Navigation.kt`):

| Route | Purpose |
|-------|---------|
| `category_list` | Start destination |
| `add_category` | Add category |
| `edit_category/{categoryId}` | Edit category |
| `invoice_list/{categoryId}` | Invoice list for category |
| `add_invoice/{categoryId}` | Add invoice |
| `invoice_details/{invoiceId}` | Read-only details |
| `edit_invoice/{invoiceId}` | Edit invoice |
| `settings` | Settings hub — Language, Text size, Backup/Restore, Reset, About |
| `language_settings` | Manual language switch |
| `about` | App info and privacy policy link |

**ViewModel sharing:** Child screens resolve the parent back-stack entry (e.g. `invoice_list/{categoryId}`) and reuse the same ViewModel instance.

**Back navigation safety:** All toolbar and programmatic back actions use `popIfSafe()` (Navigation.kt), which checks `previousBackStackEntry != null` before calling `popBackStack()`. `CategoryList` also registers `BackHandler(enabled = true)` to absorb rapid system back presses. Together these prevent the start destination from being popped and the NavHost from going blank.

**Snackbar feedback:** One-shot flags via `savedStateHandle` (e.g. `"category_added"`).

---

## E. Domain Models

Source: `core/domain/Models.kt`

### Category
- `id`, `name`, `colorHex`, `description`
- `customFieldTitles: List<String>` — up to 10 titles (JSON in Room)
- `seedKey`, `userEdited` — seeded category identity and locale protection
- `orderIndex` — persisted manual list order
- `pinnedDefaults: Map<String, String>` — e.g. default supplier name

Backward-compat getters: `customFieldTitle1..3`

### Invoice
- Core: `documentNumber`, `amountDue`, `paymentStatus`, `amountCurrency`
- Dates: `paymentDate`, `dueDate`, `servicePeriodStart`, `servicePeriodEnd`
- `servicePeriodMode: ServicePeriodMode` — `MONTH` or `DATE` (explicit source of truth)
- Payment: `paymentMethod`, `numberOfPayments`, `confirmationNumber`
- `customFieldValues: List<String>` — aligned by index to category titles
- Legacy fields retained for migration: `invoiceNumber`, `amount`, etc.

### Enums
- `PaymentStatus`: `PAID`, `NOT_PAID` (legacy DB values mapped via converter)
- `ServicePeriodMode`: `MONTH`, `DATE`
- `DocumentType`: `BILL_DEMAND`, `TAX_INVOICE`, `INVOICE_RECEIPT`
- `InvoiceCurrency`: `ILS`, `USD` — display only
- `PaymentMethodOption`: includes `NOT_SPECIFIED`, `CREDIT`, `OTHER`, etc.

### Planned / partial types
- `CustomFieldDefinition`, `InvoiceCustomFieldValue`, `InvoiceImage` — defined for future use; active UI uses indexed title/value lists

---

## F. Data Layer

**Room database:** version **14**, non-destructive migration chain.

**Repositories:**
- `RoomCategoryRepository` — CRUD, seeded localization, `updateCategoryOrder()`
- `RoomInvoiceRepository` — CRUD per category, entity ↔ domain mapping
- `RoomBackupRestoreRepository` — transactional full-replace restore from `BackupPayload`

**Entity highlights:**
- `CategoryEntity` — `customFieldTitlesJson`, `orderIndex`, `seedKey`, `userEdited`
- `InvoiceEntity` — converters for dates, enums, JSON lists/maps, `amountCurrencyCode`

**CategoryRepository extras:**
- `updateLocalizedSeededCategories(context)` — refresh unedited seeded names/descriptions
- `clearCustomFieldsForSeededCategories()` — one-time migration helper
- `updateCategoryOrder(orderedIds)` — persist reorder

**ID generation:** Room auto-generates IDs.

---

## G. State Management

ViewModels expose immutable `UiState` data classes via `StateFlow`.

**CategoryListUiState:** categories (with invoice counts), loading, error, reorder mode

**InvoiceListUiState:** category header, `sourceInvoices`, `visibleInvoices`, search/filter/sort state, loading, error

**Invoice list pipeline** (`InvoiceListViewModel`):
1. Load invoices into `sourceInvoices`
2. Apply search (mode: invoice number or amount)
3. Apply service period range filter
4. Apply payment status filter
5. Apply sort (date/amount, asc/desc)
6. Emit `visibleInvoices`

**Form state:** `rememberSaveable` for inputs; validation errors in local `remember` state; scroll-to-first-invalid on failed save.

---

## H. UI Conventions

### Top app bars
- Category list: default theme, title “Bills & Taxes”, Settings gear icon (→ Settings hub) + a small overflow menu with only Export all data and Order categories
- Invoice flows: category-colored bar via `categoryTopAppBarColors()` with contrast-aware text/icons
- Edit Category: top-bar Save action; discard warning for unsaved changes
- Invoice list: overflow Export; active filter indication and clear-filters action

### Category colors
- Hex `#RRGGBB`; parsed with fallback
- Pastel preset palette + 7×7 extended grid
- Cards: colored border/stripe; list reorder mode with animated moves

### Forms
- Picker-first dates (`ServicePeriodInput`, date rows tap-to-open picker)
- Dropdown menus positioned via `DropdownPositioning` to avoid overlapping anchors
- Snackbars: `SwipeDismissSnackbarHost` for swipe-to-dismiss

### Dialogs
- Confirmation before delete (category or invoice)
- Warning when removing custom field definitions
- Action color semantics: destructive confirms (Delete/Reset/Restore/Remove/Discard) use `MaterialTheme.colorScheme.error`; cancel/dismiss use `onSurface`; positive confirms use default primary

---

## I. Localization and RTL

- Manual language switch (Hebrew / English) persisted via `LanguagePreferenceManager`
- Locale applied in `attachBaseContext`; Compose uses `LocalLayoutDirection`
- String resources: `values/` (English), `values-iw/` (Hebrew)
- Seeded categories re-localize on language change unless `userEdited == true`
- Reset all data builds a locale-correct context from the saved language preference to ensure seeded categories are inserted in the correct language
- After restore, `last_applied_language` is set to the current language to prevent `MainActivity` from re-localizing seeded backup category names on the next launch

---

## J. First Launch and Seeding

- Loading screen while initialization runs
- Idempotent flags in `SeedingPreferenceManager`
- Default categories inserted once with stable `seedKey`
- One-time cleanup for seeded custom fields (historical migration)

---

## K. Non-Obvious Invariants

1. **Custom field alignment:** Invoice `customFieldValues[i]` maps to `category.customFieldTitles[i]`. Do not filter/reindex values in ways that break index alignment.
2. **Service period mode:** Always persist and read `ServicePeriodMode`; never infer MONTH vs DATE from stored dates alone.
3. **Category order:** List sorted by `orderIndex`, not name — preserves user order across locale changes.
4. **Currency:** Store and display `amountCurrency`; never convert amounts between ILS and USD.
5. **Seeded categories:** `userEdited` blocks automatic locale overwrite of name/description.
6. **ViewModel scope:** Invoice/category mutations must go through the shared parent ViewModel so list state stays consistent.
7. **Migrations:** Avoid new DB migrations unless explicitly requested and tested.
8. **Export vs backup vs restore:** User-facing export (CSV/ZIP) is localized and spreadsheet-oriented — **not for restore**. Backup (ZIP with `backup.json`) is restore-ready raw app data. Restore is full replace only and accepts backup ZIPs, not CSV exports.

---

## L. Data Export (implemented)

**Product distinction:** Export = user-readable files for spreadsheets and personal records. **Not for restore.**

| Entry point | Output | Scope |
|-------------|--------|--------|
| Invoice list overflow → Export | Single `.csv` via SAF | `visibleInvoices` after search/filter/sort in current category |
| Category list overflow → Export all data | `.zip` via SAF | All categories in `categories.csv`; one invoice CSV per category **with invoices** |

**Implementation notes:**
- Pure Kotlin: `InvoiceCsvExporter`, `AllDataZipExporter`, `Utf8CsvWriter` in `core/util/`
- Labels: `InvoiceCsvExportLabels` + `rememberInvoiceCsvExportLabels()`; `CategoriesCsvLabels` for category metadata CSV
- UTF-8 with conditional BOM per CSV entry (existing `Utf8CsvWriter` logic)
- File I/O and SAF launchers live in Compose screens; ViewModels supply data / CSV strings
- **Known limitation:** Google Sheets Android may misread mixed English-header / Hebrew-data CSV; desktop Sheets and LibreOffice are fine

**Share Sheet (Aug 2026):** After either export succeeds, the same bytes written to SAF are also staged in `cacheDir/exports/` and exposed as a temporary `content://` Uri via `androidx.core.content.FileProvider` (`core/util/ShareExportUtil.kt`). A **Share** action on the success snackbar opens the standard Android Share Sheet (`Intent.ACTION_SEND` + `Intent.createChooser`) with read permission granted only to the app the user picks. No `file://` Uri, no broad storage permission, and no direct Gmail/Drive/WhatsApp integration — the chooser just lists installed apps that accept the file's MIME type.

---

## M. Backup and Restore (implemented)

**Product distinction:** Backup = restore-ready app data in plaintext JSON. Restore = full replacement of local categories and invoices from a backup ZIP.

| Entry point | Output / input | Behavior |
|-------------|----------------|----------|
| Settings → Create backup | `.zip` via SAF containing `backup.json` | Exports all categories and invoices with IDs, order, colors, custom fields, service period modes, currencies, raw enums, ISO dates, metadata |
| Settings → Restore from backup | User picks backup `.zip` via SAF | Validates `backup.json` → destructive confirmation → transactional full replace |

**Implementation notes:**
- Pure Kotlin: `BackupZipExporter`, `BackupZipImporter`, `BackupValidator`, `BackupMapper` in `core/util/backup/`
- `BackupRestoreRepository` interface; `RoomBackupRestoreRepository` uses `withTransaction` (delete all + insert)
- Validation before any DB mutation; failed validation leaves current data unchanged
- Preserves original category and invoice IDs
- After successful restore, `SeedingPreferenceManager` flags set to prevent first-run seeding from duplicating restored data
- Language preference not restored or modified
- Backup files are **plaintext** and contain sensitive financial/user data
- **CSV/ZIP exports cannot be restored** — only backup ZIPs with `backup.json`

---

## N. Current Status and Evolving Areas

**Stable / complete for MVP:**
- Room persistence (v14), incremental migrations
- Category and invoice CRUD
- Dynamic custom fields (indexed title/value lists)
- Invoice search, filter, sort (`sourceInvoices` → `visibleInvoices`)
- Explicit service period mode (`MONTH` / `DATE`)
- Bilingual UI with RTL and manual language switching
- Category manual reorder (`orderIndex`)
- UI polish pass (May–Jun 2026)
- Pre-launch safety refactor (Jun 2026)
- **User-facing export:** invoice-list CSV + category-list all-data ZIP (Jun 2026)
- **Android Share Sheet support for export** (Aug 2026): optional Share action after export, via `FileProvider` + `ACTION_SEND`
- **Backup and restore:** create backup + full-replace restore (Jun 2026)
- **Targeted unit tests** (S9), **GitHub Actions CI** (S10), **release polish** (S11)
- **Release identity** (`com.dinyairsadot.clearledger`, v1.0.0, launcher icon) and **documentation polish** (S12)
- **Pre-release polish pass (Jun 2026):** dialog action color semantics, rapid-back navigation fix, custom field UI improvements, locale/seeding correctness fixes

**Not yet implemented:**
- Play Store production release
- Cloud sync, encryption, automatic backup, selective merge restore
- Receipt image/PDF attachments (next launch-prep stage)

**Pre-release focus (see `docs/LAUNCH_PLAN.md` S9–S17):**
- **Done:** S9 tests, S10 CI, S11 release polish, S12 docs, S13 release identity, S14 repo deliverables (privacy policy, store materials, screenshots, icon)
- **S14:** Complete except Play Console external tasks — verify hosted privacy policy URL (`https://dinsangun.github.io/clear-ledger/privacy-policy`), enter store listing, complete Data Safety + content rating in Play Console
- **Next:** S15 internal Play testing (signing + upload)

**Known improvement areas (non-blocking follow-ups):**
- In-memory filter/sort may need DAO queries at scale
- Startup/seeding logic concentrated in `MainActivity`
- Deprecation warnings (`menuAnchor`, `Locale(String)`, `LocalLifecycleOwner`)
- Google Sheets Android CSV encoding limitation (do not add CSV hacks; XLSX possible later)
- Large Compose files could benefit from selective extraction
- ViewModel `Context` usage could be narrowed over time

---

## Summary

Clear Ledger is a Kotlin + Jetpack Compose Android app using MVVM, Room, and Navigation Compose. Categories define optional custom field schemas; invoices store aligned value lists and explicit service period modes. The invoice list recomputes visible results through a single ViewModel pipeline. The app supports Hebrew and English with manual switching and locale-aware seeded data. User-facing export and restore-ready backup/restore are implemented via Storage Access Framework. **S14 repo deliverables are complete;** remaining external tasks are hosted privacy policy URL verification and Play Console entry. **Next focus:** S15 internal Play testing (signing). See `docs/LAUNCH_PLAN.md` and `docs/RELEASE.md`.
