package com.dinyairsadot.clearledger.core.util.backup

object BackupFormat {
    const val FORMAT_VERSION = 1
    const val JSON_ENTRY_NAME = "backup.json"
    const val PRODUCER = "clear-ledger-android"

    // IMPORTANT: Update this constant whenever ClearLedgerDatabase @Database(version = ...) changes.
    const val DB_SCHEMA_VERSION = 15
}
