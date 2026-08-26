# Clear Ledger (Android) — AI Context (Cursor)

_Last updated: 2026-08-26_

Concise working context for AI-assisted development. For the full overview see `docs/PROJECT_OVERVIEW.md`; for architecture patterns see `docs/ARCHITECTURE.md`; for release planning see `docs/LAUNCH_PLAN.md` and `docs/RELEASE.md`.

---

## 1) What this app is

Local-first Android app for tracking **bills / taxes by category**. Users manage categories (color, custom field titles, order), then add/edit/view/delete invoices per category with search, filter, and sort.

Supports **Hebrew and English** with manual language switching and RTL/LTR layout.

**Data portability (all implemented):**
- **Export** — localized CSV/ZIP for humans/spreadsheets (not for restore); after a successful export, an optional **Share** action opens the Android Share Sheet for that exact file
- **Backup** — restore-ready ZIP with `backup.json`
- **Restore** — full replace of local app data from a backup ZIP

**Invoice attachments (implemented, local-only):** one optional image/PDF attachment per invoice via SAF `OpenDocument`, immediately copied into app-private storage (`filesDir/invoice_attachments/`) so the original source file can be deleted/moved afterwards without breaking it. **Not yet included in backup/restore** — that's the next launch-prep stage.

---

## 2) Tech stack

- Kotlin, Jetpack Compose (Material 3), Navigation Compose
- MVVM: ViewModels + `StateFlow`, lifecycle-aware collection
- Room (SQLite) v16 — `RoomCategoryRepository`, `RoomInvoiceRepository`, `RoomBackupRestoreRepository`
- No DI framework; repos wired in `MainActivity` / ViewModel factories
- Export/backup: pure Kotlin in `core/util/` and `core/util/backup/`; SAF + file I/O in Compose screens
- Share Sheet: `androidx.core.content.FileProvider` (manifest + `res/xml/file_paths.xml`) + `core/util/ShareExportUtil.kt`; `Intent.ACTION_SEND` / `Intent.createChooser` in Compose screens
- Invoice attachments: SAF `OpenDocument` → copy into app-private storage, `core/util/AttachmentStorage.kt`; open via the same `FileProvider` + `core/util/AttachmentUtil.kt`; `Intent.ACTION_VIEW` to open via external viewer
- Gradle KTS with version catalog (`libs.*`)

---

## 3) Key files (read these first)

| File | Why |
|------|-----|
| `MainActivity.kt` | DB init, seeding, locale, repository wiring |
| `core/ui/Navigation.kt` | All routes, ViewModel scoping, nav args |
| `core/domain/Models.kt` | Domain contracts and invariants |
| `core/data/ClearLedgerDatabase.kt` | Migrations — **do not change casually** |
| `feature/invoice/InvoiceListViewModel.kt` | Search/filter/sort pipeline; `buildCsvContent()` |
| `feature/invoice/InvoiceListScreen.kt` | List UI, filter sheet, invoice CSV export SAF + Share action |
| `feature/category/CategoryListViewModel.kt` | Category CRUD, reorder, export/backup/restore |
| `feature/category/CategoryListScreen.kt` | Category list UI; export-all-data SAF + Share action; overflow menu now only Export + Order categories |
| `feature/settings/SettingsScreen.kt` | Settings hub (gear icon on Category list); backup/restore/reset SAF + dialogs; entry points to Language/Text size/About — shares `CategoryListViewModel` with Category list |
| `core/util/InvoiceCsvExporter.kt` | Pure Kotlin invoice CSV generation |
| `core/util/AllDataZipExporter.kt` | Pure Kotlin ZIP (categories.csv + invoice CSVs) |
| `core/util/ShareExportUtil.kt` | Stages export bytes in cache, mints `FileProvider` Uri, opens Share Sheet |
| `core/util/AttachmentStorage.kt` | Copies picked attachments into `filesDir/invoice_attachments/` (collision-safe names), mints managed-file `FileProvider` Uris, deletes managed files |
| `core/util/AttachmentUtil.kt` | Invoice attachment: legacy SAF read permission release, display name lookup, `ACTION_VIEW` open (managed file or legacy Uri) |
| `feature/invoice/InvoiceAttachmentField.kt` | Shared attach/replace/remove control used by Add/Edit invoice forms |
| `core/util/backup/BackupZipExporter.kt` | Pure Kotlin backup ZIP writer |
| `core/util/backup/BackupZipImporter.kt` | Pure Kotlin backup ZIP reader |
| `core/util/backup/BackupValidator.kt` | Defensive backup payload validation |
| `core/util/backup/BackupMapper.kt` | Domain ↔ backup DTO mapping |
| `core/data/repositories/RoomBackupRestoreRepository.kt` | Transactional full-replace restore |
| `core/data/SeedingPreferenceManager.kt` | Seeding flags updated after restore |

---

## 4) Architecture in one paragraph

Compose screens render immutable UI state and forward intents. ViewModels own state, call repositories in `viewModelScope`, and expose `StateFlow`. Repositories map Room entities to domain models. Navigation Compose defines routes; related screens share a ViewModel via parent `NavBackStackEntry`. Export/backup read domain data through ViewModels; CSV/ZIP/JSON bytes written or read via SAF in the screen layer. Restore validates backup JSON before any DB mutation, then `RoomBackupRestoreRepository` replaces all data in a single transaction.

---

## 5) Domain invariants — preserve these

### Custom fields
- Categories store `customFieldTitles: List<String>` (max 10, JSON in DB)
- Invoices store `customFieldValues: List<String>` aligned **by index**
- **Do not** filter blank values in ways that shift indices; **do not** reorder values independently of titles

### Service period
- `ServicePeriodMode` (`MONTH` | `DATE`) is the **explicit source of truth**
- **Never** infer mode from stored dates alone

### Category ordering
- Sort categories by `orderIndex`, not name
- Reorder persisted via `CategoryRepository.updateCategoryOrder(orderedIds)`
- Preserve `isReorderMode` on category `refresh()`

### Category update (hidden persisted fields)
- **`RoomCategoryRepository.updateCategory`** must preserve `supplierName` and `pinnedDefaultsJson` from existing DB row

### Currency
- `InvoiceCurrency` (ILS / USD) is **display metadata only** — amounts not converted

### Seeded categories
- `seedKey` identifies built-in categories; `userEdited` blocks locale overwrite
- After restore, seeding flags are set so first-run seeding does not duplicate restored data
- After restore, `last_applied_language` is set to the current locale so backup category names are not overwritten by re-localization on the next launch

### Localization / RTL
- Language preference persisted; locale applied in `attachBaseContext`
- Hebrew resources in `values-iw/`; test RTL after UI changes
- Read locale via **`LocalConfiguration.current`**, not `LocalContext.current.resources.configuration`
- Export headers follow **app locale only** (English or Hebrew) — **no bilingual headers**, no encoding hacks
- Restore does **not** modify language preference
- Reset all data uses `buildSavedLocaleContext()` (reads `LanguagePreferenceManager`, constructs a `Configuration`-wrapped context) so seeding always uses the correct locale regardless of what `LocalContext.current` resolved at ViewModel creation

### Export vs backup vs restore
- **Export** = localized CSV/ZIP for humans/spreadsheets — **not for restore**
- **Backup** = raw restore-ready JSON in ZIP (`backup.json`) — plaintext, sensitive
- **Restore** = full replace only from backup ZIP — **not** from CSV exports

### UI text display
- **List cards:** ellipsis / `maxLines` acceptable
- **Details screens:** full information; natural wrapping

---

## 6) Invoice list pipeline

```
sourceInvoices → search → service-period filter → status filter → sort → visibleInvoices
```

Invoice CSV export uses **`visibleInvoices`** (and category name/titles from UiState) — do not change export scope without explicit product request.

---

## 7) Export behavior (do not regress)

### Invoice-list CSV (`InvoiceListScreen`)
- Overflow → Export; SAF `text/csv`
- `InvoiceListViewModel.buildCsvContent(labels)` → `InvoiceCsvExporter.generate()`
- `Utf8CsvWriter.writeUtf8CsvWithBom` on output stream

### Category-list ZIP (`CategoryListScreen`)
- Overflow → Export all data; SAF `application/zip`
- `CategoryListViewModel.loadAllDataForExport()` → `AllDataZipExporter.writeZip()`
- ZIP: `categories.csv` (name, description, order, custom field titles — **no color**)
- `invoices/<sanitizedName>_<id>.csv` only when category has ≥1 invoice
- Filenames: preserve Hebrew/English; replace only unsafe path chars (`/ \ : * ? " < > |`, controls); max ~60 chars + `_<id>`

### UTF-8 / BOM
- BOM per **CSV entry** when content has non-ASCII (`Utf8CsvWriter`) — not at ZIP level
- Do not change invoice-list export encoding without explicit request

### Google Sheets Android
- Known limitation: may misread valid UTF-8 CSV (English headers + Hebrew data). **Do not add CSV encoding hacks.** LibreOffice / desktop Sheets are the target. XLSX is a possible future improvement.

### Share Sheet (Aug 2026, additive on top of export — do not regress)
- Both export flows stage the same bytes written to SAF in `context.cacheDir/exports/` first, then copy them into the SAF destination — so SAF output is byte-identical to before this feature.
- `ShareExportUtil.prepareCacheFile()` clears prior staged files, `shareUriFor()` wraps the staged file via `FileProvider.getUriForFile()`, `shareFile()` fires `Intent.ACTION_SEND` + `Intent.createChooser` with `FLAG_GRANT_READ_URI_PERMISSION`.
- Manifest declares `androidx.core.content.FileProvider` at `${applicationId}.fileprovider`, `exported="false"`; `res/xml/file_paths.xml` exposes only the `exports/` cache subdirectory.
- Share action surfaces as an action button on the existing "Export completed" snackbar (`R.string.share`); tapping it launches the chooser for `text/csv` or `application/zip`. `AppSnackbar` gained optional `actionLabel`/`onActionClick` params (default no-op) to support this in `CategoryListScreen`'s custom snackbar renderer — other callers (`SettingsScreen`, `AboutScreen`) are unaffected.
- **No** direct Gmail/Drive/WhatsApp integration, **no** Google auth, **no** custom sharing UI — only the standard system chooser.

---

## 8) Backup and restore behavior (do not regress)

**UI entry point (Aug 2026):** Create backup, Restore from backup, and Reset all data moved from the Category list overflow menu into the new **Settings** screen (`feature/settings/SettingsScreen.kt`, gear icon on Category list). `SettingsScreen` shares the same `CategoryListViewModel` instance as `CategoryList` (via `getBackStackEntry(Screen.CategoryList.route)`) — the calls below are unchanged, only the screen that invokes them moved.

### Create backup (`SettingsScreen`)
- Settings → Create backup; SAF `application/zip`
- `CategoryListViewModel.loadAllDataForBackup()` → `BackupZipExporter.writeZip()`
- ZIP contains single `backup.json` with `formatVersion`, metadata, categories, invoices
- Stores raw enum names, ISO dates, explicit nulls, IDs, order, custom fields — **not** localized display strings

### Restore backup (`SettingsScreen`)
- Settings → Restore from backup; SAF `OpenDocument` for `application/zip`
- `CategoryListViewModel.validateAndParseBackup(uri)` → `BackupZipImporter` + `BackupValidator`
- If valid: show destructive confirmation dialog; on confirm → `performRestore()` → `RoomBackupRestoreRepository.restoreFromBackup()`
- **Full replace only** — not merge; validation before delete; transaction rolls back on failure
- Preserves original category and invoice IDs
- Sets seeding flags after success; sets `last_applied_language` to current locale to prevent re-localization of backup category names on next launch; does not touch language preference
- **CSV/ZIP exports are rejected** — only backup ZIPs with `backup.json`

### Invoice attachments (Aug 2026, app-private storage — do not regress)
- **Local-only for now:** none of `Invoice`'s attachment fields (`attachmentFileName`, `attachmentDisplayName`, `attachmentMimeType`, `attachmentUri`) are included in `BackupInvoiceDto`/`BackupMapper`. Do not add them without explicitly implementing attachment-aware backup/restore as its own stage (decide file embedding vs. documented limitation first).
- **Copied into app-private storage:** a picked document's bytes are copied into `filesDir/invoice_attachments/` under a random, collision-safe filename (`AttachmentStorage.copyIntoManagedStorage`), and the invoice stores that managed filename plus the captured display name/MIME type. The original source file is **not** needed afterwards and can be deleted/moved. Do not revert to storing only an external Uri reference.
- **No broad storage permission, `MediaStore`, or cloud storage** — only a plain `filesDir` subdirectory exposed through the existing `FileProvider` (`res/xml/file_paths.xml`, `invoice_attachments/` path entry).
- **Managed-file cleanup timing matters:** a pending, never-saved copy (replaced again, removed, or the form discarded) is always safe to delete directly — it's a brand-new file nothing could reference yet. The invoice's actual *saved* managed file must only be deleted after a real save/delete succeeds, and only if no other persisted invoice still references it (`InvoiceRepository.countInvoicesWithAttachmentFileName`, mirroring the legacy Uri check below). See `InvoiceListViewModel.updateInvoice`/`deleteInvoice` and the Add/Edit screens' `onAttach`/`onRemove`/discard handlers.
- **Legacy invoices migrate lazily:** invoices created before this storage strategy have only a legacy `attachmentUri`. `InvoiceListViewModel.migrateLegacyAttachmentIfNeeded` (called from `loadInvoicesForScope`, i.e. every list load) tries to copy the legacy source in; on success it clears `attachmentUri` and releases the old permission once unreferenced (see the shared-Uri check below). If the source is inaccessible, the invoice and its legacy reference are left completely untouched — **never delete invoice data because an attachment can't be migrated.** No Room migration does file I/O.
- **Shared Uris across invoices are protected (Aug 2026 fix, still applies to legacy references):** two invoices may legitimately reference the exact same legacy `attachmentUri`. Before releasing any Uri's persisted permission — on update, delete, or migration — callers must confirm via `InvoiceRepository.countInvoicesWithAttachmentUri` that no other **persisted** invoice still needs it. Never reintroduce an unconditional release.
- **Unsaved-changes:** attachment changes are part of `EditableInvoiceSnapshot` (via `attachmentFileName`); canceling the SAF picker must not itself count as a change.
- **Export/Share Sheet unaffected:** CSV export, Export all data ZIP, and Share Sheet do not include attachment files — do not add them without an explicit request.

---

## 9) What NOT to change casually

| Area | Guidance |
|------|----------|
| **Room schema / migrations** | Avoid unless explicitly requested |
| **Custom field index alignment** | High risk of silent misalignment |
| **ServicePeriodMode semantics** | Core invariant |
| **Category orderIndex sorting** | Name-based sort breaks user order |
| **Hidden category fields on update** | Round-trip `supplierName` / `pinnedDefaults` |
| **Export scope & format** | Invoice export = visible only; ZIP skips empty invoice CSVs |
| **Share Sheet scope** | Sharing must reuse existing export bytes as-is; do not add Gmail/Drive-specific APIs, Google auth, or a custom sharing UI |
| **Backup format / restore semantics** | Full replace; validate before delete; preserve IDs |
| **Invoice attachments** | Local-only (copied into app-private `filesDir/invoice_attachments/`, not yet in backup/restore); do not delete a saved managed file, or release a legacy Uri permission, before a real save/delete succeeds and no other invoice still references it |
| **Localization** | Both `values/` and `values-iw/` |
| **Back navigation (`popIfSafe` + BackHandler)** | `popIfSafe()` in Navigation.kt and `BackHandler(enabled = true)` at CategoryList root must stay; removing either re-exposes the blank-screen bug on rapid back presses |

Ask before: DB migrations, conflating export with backup, allowing CSV restore, bilingual CSV headers, changing restore to merge mode.

---

## 10) Recent completed work (Jun 2026)

**Pre-launch refactor** (`cc6e8f5`): debug log removal; hidden category fields; invoice errors; reorder on refresh; list fixes; Hebrew strings; build/lint/test green.

**UI polish** (`9189ace`–`df3d14e`): FAB overlap; edit-category save/discard; invoice list top bar; filter indication; custom field UX.

**Export** (`0819b53`, `36ddae4`): invoice CSV + all-data ZIP.

**Backup** (`37ff651`): create restore-ready backup ZIP with `backup.json`.

**Restore** (`73b7bd6`): full-replace restore with validation, transaction, ID preservation, seeding flag handling.

**Pre-release polish (Jun 2026):** dialog action color semantics (error/onSurface/primary per button role across all 7 dialogs); rapid-back blank-screen fix (`popIfSafe()` in Navigation.kt + `BackHandler(enabled = true)` at CategoryList root, public APIs only, lint passes); custom field UI clarity (OutlinedButton + icon in category form; invoice custom fields use standard floating label consistent with other fields); locale/seeding fix (reset uses `buildSavedLocaleContext()`; restore sets `last_applied_language` to block re-localization of backup names). 7 Play Store screenshots captured.

**Settings screen / navigation cleanup (Aug 2026):** Category list overflow menu reduced to Export + Order categories only; added Settings gear icon → new `SettingsScreen` (`feature/settings/`) grouping Language, Text size, Create backup, Restore from backup, Reset all data, About. Pure navigation/UI reorganization — backup/restore/reset reuse the same `CategoryListViewModel` calls via shared back-stack entry; language implementation itself unchanged.

**Android Share Sheet support for export (Aug 2026):** Added optional sharing on top of the existing export flow (invoice-list CSV + category-list all-data ZIP) — see section 7 above for the do-not-regress details. `FileProvider` + `res/xml/file_paths.xml` added (minimum config, scoped to `cacheDir/exports/` only). No export format/content changes, no backup/restore changes, no Gmail/Drive integration.

**Invoice attachments (Aug 2026):** Added one optional local image/PDF attachment per invoice — see the do-not-regress subsection above. SAF `OpenDocument` + persisted read Uri permission (no file copy into app storage); Room bumped 14 → 15 (`MIGRATION_14_15`, additive `attachmentUri TEXT` column); Add/Edit forms gained an Attachment section wired into unsaved-changes detection; Invoice Details gained an Attachment section with an "Open" action (`ACTION_VIEW`, graceful failure via snackbar). **Local-only** — excluded from backup/restore DTOs on purpose; export/Share Sheet unchanged. Next launch-prep stage: attachment-aware backup/restore.

**Invoice attachment storage refactor (Aug 2026):** Replaced the external-Uri-only strategy above with a copy-into-app-private-storage strategy — see the do-not-regress subsection above. New picks are copied into `filesDir/invoice_attachments/` (`AttachmentStorage.kt`) instead of relying on a persisted external Uri; `Invoice`/`InvoiceEntity` gained `attachmentFileName`/`attachmentDisplayName`/`attachmentMimeType` (Room bumped 15 → 16, `MIGRATION_15_16`, additive columns); pre-existing `attachmentUri` invoices migrate lazily on list load (`InvoiceListViewModel.migrateLegacyAttachmentIfNeeded`) and are left untouched if the source is inaccessible. Opening uses the app's `FileProvider` for managed files (legacy Uris still open directly). Still **local-only** — attachment fields remain excluded from backup/restore; export/Share Sheet unchanged.

---

## 11) Project status (Jun 2026)

**Done:** Room, bilingual UI, custom fields, search/filter/sort, service period, category reorder, UI polish, pre-launch refactor, user-facing export (CSV + ZIP), Android Share Sheet support for export, backup creation, full-replace restore, targeted unit tests (S9), GitHub Actions CI (S10), release polish (S11), documentation polish (S12), release identity (S13 — `com.dinyairsadot.clearledger`, v1.0.0), pre-release polish pass (dialog colors, navigation fix, custom field UI, locale/seeding fixes), one local image/PDF attachment per invoice (Aug 2026, local-only, now copied into app-private storage with lazy migration of pre-refactor invoices).

**Next launch-prep stage:** attachment-aware backup/restore (not started).

**Pre-release (priority order — see `LAUNCH_PLAN` S9–S17):**
1. **S9** — Targeted test hardening — **Done**
2. **S10** — CI (`test`, `lintDebug`, `assembleDebug`) — **Done**
3. **S11** — Release polish — **Done**
4. **S12** — Project docs — **Done**
5. **S13** — Release identity — **Done**
6. **S14** — Privacy policy and Play Store materials — **Complete except Play Console external tasks** (repo deliverables done; remaining: verify hosted policy URL, enter listing, complete Data Safety + content rating in Play Console)
7. **S15** — Internal Play testing (signing deferred here)
8. **S16** — Launch blocker fixes only
9. **S17** — Production release + GitHub/interview presentation

**Not implemented:** Play production release, cloud sync, encryption, automatic backup, selective merge restore.

---

## 12) Non-blocking follow-ups

- Deprecated `menuAnchor()`, `Locale(String)`, `LocalLifecycleOwner`
- Dependency/version warnings, unused resources, portrait lint
- ViewModel `Context` usage — optional cleanup
- Future XLSX export for Google Sheets Android (not CSV hacks)
- Instrumented restore transaction test (optional)
- Category deletion cascade-deletes its invoices at the Room/SQL level (`ForeignKey.CASCADE`), bypassing `InvoiceListViewModel.deleteInvoice` — leaks legacy attachment Uri permissions and orphans managed attachment files on disk for those invoices; pre-existing, not introduced by the Aug 2026 attachment work, worth a dedicated follow-up

---

## 13) Historical note

`docs/ARCHITECTURE_SUMMARY.pdf` may be outdated. Treat `PROJECT_OVERVIEW.md` and this file as current until the PDF is regenerated.
