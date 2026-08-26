package com.dinyairsadot.clearledger.core.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.dinyairsadot.clearledger.core.data.converters.DocumentTypeConverter
import com.dinyairsadot.clearledger.core.data.converters.LocalDateConverter
import com.dinyairsadot.clearledger.core.data.converters.PaymentStatusConverter
import com.dinyairsadot.clearledger.core.data.converters.ServicePeriodModeConverter
import com.dinyairsadot.clearledger.core.data.converters.StringListConverter
import com.dinyairsadot.clearledger.core.data.converters.StringMapConverter
import com.dinyairsadot.clearledger.core.data.dao.CategoryDao
import com.dinyairsadot.clearledger.core.data.dao.InvoiceDao
import com.dinyairsadot.clearledger.core.data.entities.CategoryEntity
import com.dinyairsadot.clearledger.core.data.entities.InvoiceEntity

@Database(
    entities = [CategoryEntity::class, InvoiceEntity::class],
    version = 16,
    exportSchema = false
)
@TypeConverters(
    LocalDateConverter::class,
    PaymentStatusConverter::class,
    ServicePeriodModeConverter::class,
    StringListConverter::class,
    StringMapConverter::class,
    DocumentTypeConverter::class
)
abstract class ClearLedgerDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao
    abstract fun invoiceDao(): InvoiceDao
    
    companion object {
        @Volatile
        private var INSTANCE: ClearLedgerDatabase? = null
        
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE invoices ADD COLUMN documentTypeString TEXT")
            }
        }
        
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE invoices ADD COLUMN vendorName TEXT")
                database.execSQL("ALTER TABLE invoices ADD COLUMN issueDateEpochDay INTEGER")
                database.execSQL("ALTER TABLE invoices ADD COLUMN servicePeriodStartEpochDay INTEGER")
                database.execSQL("ALTER TABLE invoices ADD COLUMN servicePeriodEndEpochDay INTEGER")
            }
        }
        
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add new minimal core fields to invoices
                // amountDue defaults to existing amount, documentNumber defaults to invoiceNumber
                database.execSQL("ALTER TABLE invoices ADD COLUMN amountDue REAL")
                database.execSQL("ALTER TABLE invoices ADD COLUMN documentNumber TEXT")
                database.execSQL("ALTER TABLE invoices ADD COLUMN paymentMethodString TEXT")
                database.execSQL("ALTER TABLE invoices ADD COLUMN confirmationNumber TEXT")
                
                // Populate new fields from old ones for existing rows
                database.execSQL("UPDATE invoices SET amountDue = amount WHERE amountDue IS NULL")
                database.execSQL("UPDATE invoices SET documentNumber = invoiceNumber WHERE documentNumber IS NULL")
                
                // Add supplierName to categories
                database.execSQL("ALTER TABLE categories ADD COLUMN supplierName TEXT")
            }
        }
        
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add pinnedDefaultsJson to categories for flexible pinned defaults (e.g., supplierName)
                database.execSQL("ALTER TABLE categories ADD COLUMN pinnedDefaultsJson TEXT")
            }
        }
        
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Add pinnedSnapshotJson to invoices to capture category pinned defaults at creation time
                database.execSQL("ALTER TABLE invoices ADD COLUMN pinnedSnapshotJson TEXT")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Locale-aware seeded categories: only new seeds get seedKey; no backfill
                database.execSQL("ALTER TABLE categories ADD COLUMN seedKey TEXT")
                database.execSQL("ALTER TABLE categories ADD COLUMN userEdited INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Step 1: give every category a default service-period mode of MONTH.
                // New categories also default to MONTH via the Kotlin field default.
                database.execSQL(
                    "ALTER TABLE categories ADD COLUMN defaultServicePeriodMode TEXT NOT NULL DEFAULT 'MONTH'"
                )

                // Step 2: add the mode column to invoices.
                // Temporary default 'MONTH' is overwritten in step 3 where the parent category exists.
                // Any orphaned invoice (no matching category) keeps 'MONTH' as a safe fallback.
                database.execSQL(
                    "ALTER TABLE invoices ADD COLUMN servicePeriodMode TEXT NOT NULL DEFAULT 'MONTH'"
                )

                // Step 3: propagate each category's defaultServicePeriodMode to its invoices.
                // After step 1 every category has 'MONTH', so existing invoices inherit MONTH.
                // This UPDATE is safe on SQLite because it is a correlated sub-select, not a JOIN.
                database.execSQL(
                    """
                    UPDATE invoices
                    SET servicePeriodMode = (
                        SELECT defaultServicePeriodMode
                        FROM categories
                        WHERE categories.id = invoices.categoryId
                    )
                    WHERE EXISTS (
                        SELECT 1 FROM categories WHERE categories.id = invoices.categoryId
                    )
                    """.trimIndent()
                )
                // Net result for existing users:
                // - All categories → defaultServicePeriodMode = MONTH
                // - All invoices that have a valid category → servicePeriodMode = MONTH
                // - Orphaned invoices (category deleted) → servicePeriodMode = MONTH (column default)
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Consolidate PAID_FULL and PAID_CREDIT into PAID
                database.execSQL(
                    "UPDATE invoices SET paymentStatus = 'PAID' WHERE paymentStatus IN ('PAID_FULL', 'PAID_CREDIT')"
                )
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE invoices ADD COLUMN numberOfPayments TEXT")
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE invoices ADD COLUMN amountCurrencyCode TEXT NOT NULL DEFAULT 'ILS'"
                )
            }
        }

        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_invoices_categoryId ON invoices(categoryId)"
                )
            }
        }

        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE categories ADD COLUMN orderIndex INTEGER NOT NULL DEFAULT 0"
                )
                database.execSQL(
                    """
                    UPDATE categories
                    SET orderIndex = CASE
                        WHEN seedKey = 'arnona' THEN 0
                        WHEN seedKey = 'electricity' THEN 1
                        WHEN seedKey = 'water' THEN 2
                        WHEN seedKey = 'gas' THEN 3
                        WHEN seedKey = 'phone_internet' THEN 4
                        WHEN seedKey = 'national_insurance' THEN 5
                        WHEN seedKey = 'health_fund' THEN 6
                        WHEN seedKey = 'car_insurance' THEN 7
                        WHEN seedKey IS NOT NULL THEN 900000 + id
                        ELSE 1000000 + id
                    END
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Remove defaultServicePeriodMode from categories (mode is now per-invoice).
                // SQLite < 3.35 doesn't support DROP COLUMN; use table recreate.
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS categories_new (
                        id INTEGER PRIMARY KEY NOT NULL,
                        name TEXT NOT NULL,
                        colorHex TEXT NOT NULL,
                        description TEXT,
                        customFieldTitlesJson TEXT,
                        supplierName TEXT,
                        pinnedDefaultsJson TEXT,
                        seedKey TEXT,
                        userEdited INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
                database.execSQL("""
                    INSERT INTO categories_new (id, name, colorHex, description, customFieldTitlesJson, supplierName, pinnedDefaultsJson, seedKey, userEdited)
                    SELECT id, name, colorHex, description, customFieldTitlesJson, supplierName, pinnedDefaultsJson, seedKey, userEdited
                    FROM categories
                """.trimIndent())
                database.execSQL("DROP TABLE categories")
                database.execSQL("ALTER TABLE categories_new RENAME TO categories")
            }
        }

        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Optional single local attachment (image/PDF) per invoice. Stores a persisted
                // SAF `content://` Uri string; the document itself is never copied into app
                // storage. Existing rows get NULL (no attachment) — no data loss.
                database.execSQL("ALTER TABLE invoices ADD COLUMN attachmentUri TEXT")
            }
        }

        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Attachments are now copied into app-private storage (filesDir/invoice_attachments/)
                // instead of only referencing an external SAF Uri. These three columns store the
                // managed copy's reference; `attachmentUri` (added in 14->15) is kept as-is to hold
                // any not-yet-migrated legacy attachment and is cleared once migration succeeds.
                // Purely additive — no existing invoice data is touched or lost.
                database.execSQL("ALTER TABLE invoices ADD COLUMN attachmentFileName TEXT")
                database.execSQL("ALTER TABLE invoices ADD COLUMN attachmentDisplayName TEXT")
                database.execSQL("ALTER TABLE invoices ADD COLUMN attachmentMimeType TEXT")
            }
        }

        fun getDatabase(context: Context): ClearLedgerDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ClearLedgerDatabase::class.java,
                    "clear_ledger_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
