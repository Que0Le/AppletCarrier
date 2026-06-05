package com.example.applet_carrier.platform

import java.time.Duration
import java.time.Instant
import java.time.Period
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.math.abs

/** One copyable output line: a value plus an optional descriptive label. */
data class FormatRow(val value: String, val label: String? = null)

/** A titled group of output rows (ISO 8601, Epoch, Other, Time zones). */
data class FormatGroup(val title: String, val rows: List<FormatRow>)

/** Offset spellings the user can enable in preferences. */
object OffsetStyles {
    const val Z = "Z"
    const val COLON = "+00:00"
    const val COMPACT = "+0000"
    val ALL = listOf(Z, COLON, COMPACT)
}

private val ISO_Z = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC)
private val ISO_OFFSET_COLON = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssxxx")
private val ISO_OFFSET_COMPACT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssxx")
private val SQL = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
// Explicit HTTP-date (RFC 7231): zero-padded day, unlike RFC_1123_DATE_TIME.
private val HTTP_DATE =
    DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.ENGLISH).withZone(ZoneOffset.UTC)

/**
 * Build every output group for [instant]. Offset-less rows (SQL, day-of-week) are rendered
 * in [sourceZone]; the ISO group is always shown in UTC; the Time zones group renders one
 * row per [favoriteZones]. [now] is injectable so relative time is deterministic in tests.
 */
internal fun buildFormatGroups(
    instant: Instant,
    sourceZone: ZoneId,
    favoriteZones: List<ZoneId>,
    offsetStyles: List<String>,
    now: Instant = Instant.now(),
): List<FormatGroup> {
    val groups = mutableListOf<FormatGroup>()

    // ISO 8601 (UTC).
    val isoRows = buildList {
        if (OffsetStyles.Z in offsetStyles) add(FormatRow(ISO_Z.format(instant)))
        if (OffsetStyles.COLON in offsetStyles) add(FormatRow(instant.atOffset(ZoneOffset.UTC).format(ISO_OFFSET_COLON)))
        if (OffsetStyles.COMPACT in offsetStyles) add(FormatRow(instant.atOffset(ZoneOffset.UTC).format(ISO_OFFSET_COMPACT)))
    }
    if (isoRows.isNotEmpty()) groups += FormatGroup("ISO 8601", isoRows)

    // Epoch.
    groups += FormatGroup(
        "Epoch",
        listOf(
            FormatRow(instant.epochSecond.toString(), "seconds"),
            FormatRow(instant.toEpochMilli().toString(), "milliseconds"),
        ),
    )

    // Other.
    val zoned = instant.atZone(sourceZone)
    val isoWeek = zoned.get(WeekFields.ISO.weekOfWeekBasedYear())
    val dow = zoned.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH)
    groups += FormatGroup(
        "Other",
        listOf(
            FormatRow(HTTP_DATE.format(instant), "RFC 1123 / HTTP"),
            FormatRow(SQL.format(zoned), "SQL datetime · ${zoneLabel(sourceZone)}"),
            FormatRow("$dow · ISO week $isoWeek · day ${zoned.dayOfYear}"),
            FormatRow(relativeTime(now, instant, sourceZone), "relative"),
        ),
    )

    // Time zones — value is the localized ISO, label is the zone id.
    if (favoriteZones.isNotEmpty()) {
        groups += FormatGroup(
            "Time zones",
            favoriteZones.map { zone ->
                FormatRow(instant.atZone(zone).format(ISO_OFFSET_COLON), zoneLabel(zone))
            },
        )
    }

    return groups
}

/** Human-friendly relative time using the largest meaningful unit. */
internal fun relativeTime(now: Instant, instant: Instant, zone: ZoneId): String {
    if (instant == now) return "now"
    val future = instant.isAfter(now)
    val earlier = (if (future) now else instant).atZone(zone)
    val later = (if (future) instant else now).atZone(zone)

    val period = Period.between(earlier.toLocalDate(), later.toLocalDate())
    val unit = when {
        period.years > 0 -> "~${period.years} year${plural(period.years)}"
        period.months > 0 -> "~${period.months} month${plural(period.months)}"
        period.days > 0 -> "${period.days} day${plural(period.days)}"
        else -> {
            val secs = abs(Duration.between(now, instant).seconds)
            when {
                secs >= 3600 -> "${secs / 3600} hour${plural(secs / 3600)}"
                secs >= 60 -> "${secs / 60} minute${plural(secs / 60)}"
                else -> "$secs second${plural(secs)}"
            }
        }
    }
    return if (future) "in $unit" else "$unit ago"
}

private fun plural(n: Long): String = if (n == 1L) "" else "s"
private fun plural(n: Int): String = if (n == 1) "" else "s"
