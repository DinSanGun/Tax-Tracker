package com.dinyairsadot.clearledger.core.domain

/**
 * User-selectable app-wide text size preference. Applied centrally via the
 * Typography supplied to [com.dinyairsadot.clearledger.ui.theme.ClearLedgerTheme];
 * never checked individually by screens or Text composables.
 *
 * This complements (and does not replace or disable) Android's system font
 * scaling, which continues to apply on top of whichever Typography is selected.
 */
enum class AppTextSize {
    NORMAL,
    LARGE,
    EXTRA_LARGE
}
