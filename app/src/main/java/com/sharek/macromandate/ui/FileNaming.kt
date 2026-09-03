package com.sharek.macromandate.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A filename-safe local timestamp for exported files, e.g. "2026-09-03_1425".
 *
 * Export filenames previously carried a raw `System.currentTimeMillis()` value
 * (`MacroMandate_Backup_1798123456789.json`) — accurate, but unreadable when
 * managing several exports later in a file browser. `Locale.US` here only
 * fixes the digit characters used to render the date, not any wording — there
 * is none — so this stays a plain, unambiguous filename in every locale.
 */
fun exportTimestamp(): String =
    SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.US).format(Date())
