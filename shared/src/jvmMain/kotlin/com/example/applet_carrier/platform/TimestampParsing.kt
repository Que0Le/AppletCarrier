package com.example.applet_carrier.platform

import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Pure, testable parsing for the Timestamp & timezone converter. Turns a single free-text
 * field into an [Instant] plus a transparent description of how it was interpreted, so the
 * applet never guesses silently (AGENTS.md design notes for this applet).
 */

enum class TsKind { NOW, DATE, EPOCH_SECONDS, EPOCH_MILLIS, ISO }

/** A forced re-interpretation the user can pick when a digit string is contestable. */
enum class ForcedMode { DATE, EPOCH_SECONDS, EPOCH_MILLIS }

data class TsOverride(val mode: ForcedMode, val label: String)

sealed interface TsParse {
    /** Empty field → the applet shows a live "now". */
    data object Empty : TsParse

    data class Invalid(val message: String) : TsParse

    data class Ok(
        val instant: Instant,
        val kind: TsKind,
        val readAs: String,
        val canonical: String?,            // snap target for the input field, or null
        val overrides: List<TsOverride>,
    ) : TsParse
}

private val CALENDAR_LENGTHS = setOf(4, 6, 8, 12, 14)

/**
 * Parse [raw] using [sourceZone] for offset-less calendar inputs. When [forced] is set,
 * the input is interpreted that way regardless of the digit-count heuristic.
 */
internal fun parseTimestamp(raw: String, sourceZone: ZoneId, forced: ForcedMode? = null): TsParse {
    val s = raw.trim()
    if (s.isEmpty()) return TsParse.Empty

    if (forced != null) {
        return when (forced) {
            ForcedMode.EPOCH_SECONDS -> epochParse(s.filter(Char::isDigit), millis = false)
            ForcedMode.EPOCH_MILLIS -> epochParse(s.filter(Char::isDigit), millis = true)
            ForcedMode.DATE -> calendarParse(s.filter(Char::isDigit), sourceZone)
                ?: TsParse.Invalid("Can't read those digits as a calendar date.")
        }
    }

    if (s.equals("now", ignoreCase = true)) {
        return TsParse.Ok(Instant.now(), TsKind.NOW, "keyword · now", canonical = null, overrides = emptyList())
    }

    if (s.all(Char::isDigit)) return parseDigits(s, sourceZone)

    val isoInstant = tryParseIsoish(s, sourceZone)
    if (isoInstant != null) {
        return TsParse.Ok(isoInstant, TsKind.ISO, "ISO 8601 · ${zoneLabel(sourceZone)}", canonical = null, overrides = emptyList())
    }

    return TsParse.Invalid("Unrecognized format. Try YYYYMMDD, epoch (10 or 13 digits), or ISO 8601.")
}

private fun parseDigits(s: String, zone: ZoneId): TsParse = when (s.length) {
    in CALENDAR_LENGTHS -> calendarParse(s, zone) ?: TsParse.Invalid("Not a valid date.")
    10 -> epochParse(s, millis = false)
    13 -> epochParse(s, millis = true)
    // Unusual length → most likely epoch; offer the alternatives.
    else -> epochParse(s, millis = s.length >= 12)
}

/** Parse a 4/6/8/12/14-digit calendar string, padding missing fields with sensible defaults. */
private fun calendarParse(s: String, zone: ZoneId): TsParse? {
    if (s.length !in CALENDAR_LENGTHS) return null

    val year = s.substring(0, 4).toInt()
    val month = if (s.length >= 6) s.substring(4, 6).toInt() else 1
    val day = if (s.length >= 8) s.substring(6, 8).toInt() else 1
    val hour = if (s.length >= 12) s.substring(8, 10).toInt() else 0
    val minute = if (s.length >= 12) s.substring(10, 12).toInt() else 0
    val second = if (s.length >= 14) s.substring(12, 14).toInt() else 0

    if (month !in 1..12) return TsParse.Invalid("Not a valid date — month $month doesn't exist.")
    if (day !in 1..31) return TsParse.Invalid("Not a valid date — day $day is out of range.")
    if (hour !in 0..23) return TsParse.Invalid("Not a valid time — hour $hour is out of range.")
    if (minute !in 0..59) return TsParse.Invalid("Not a valid time — minute $minute is out of range.")
    if (second !in 0..59) return TsParse.Invalid("Not a valid time — second $second is out of range.")

    val ldt = try {
        LocalDateTime.of(year, month, day, hour, minute, second)
    } catch (e: DateTimeException) {
        return TsParse.Invalid("Not a valid date — ${"%04d-%02d-%02d".format(year, month, day)} doesn't exist.")
    }

    val instant = ldt.atZone(zone).toInstant()
    val zl = zoneLabel(zone)
    val (canonical, readAs) = when (s.length) {
        4 -> "%04d".format(year) to "year only · Jan 1 · midnight · $zl"
        6 -> "%04d-%02d".format(year, month) to "year-month (YYYYMM) · day 1 · midnight · $zl"
        8 -> "%04d-%02d-%02d".format(year, month, day) to "calendar date (YYYYMMDD) · midnight · $zl"
        12 -> "%04d-%02d-%02dT%02d:%02d".format(year, month, day, hour, minute) to "datetime (YYYYMMDDHHMM) · $zl"
        else -> "%04d-%02d-%02dT%02d:%02d:%02d".format(year, month, day, hour, minute, second) to "datetime (YYYYMMDDHHMMSS) · $zl"
    }
    return TsParse.Ok(instant, TsKind.DATE, readAs, canonical, overridesFor(TsKind.DATE, s.length))
}

private fun epochParse(s: String, millis: Boolean): TsParse {
    val value = s.toLongOrNull() ?: return TsParse.Invalid("Number is too large to be an epoch value.")
    val instant = if (millis) Instant.ofEpochMilli(value) else Instant.ofEpochSecond(value)
    val kind = if (millis) TsKind.EPOCH_MILLIS else TsKind.EPOCH_SECONDS
    val readAs = if (millis) "epoch milliseconds" else "epoch seconds"
    return TsParse.Ok(instant, kind, readAs, canonical = null, overrides = overridesFor(kind, s.length))
}

/** Offer the plausible alternative interpretations, excluding the current one. */
private fun overridesFor(current: TsKind, length: Int): List<TsOverride> {
    val out = mutableListOf<TsOverride>()
    val calendarLength = length in CALENDAR_LENGTHS
    if (current != TsKind.DATE && calendarLength) out += TsOverride(ForcedMode.DATE, "date")
    if (current != TsKind.EPOCH_SECONDS) out += TsOverride(ForcedMode.EPOCH_SECONDS, "epoch seconds")
    if (current != TsKind.EPOCH_MILLIS) out += TsOverride(ForcedMode.EPOCH_MILLIS, "epoch millis")
    return out
}

/** Lenient ISO-8601-ish parsing: offset/Z, space-as-T, local datetime/date, year-month. */
private fun tryParseIsoish(raw: String, zone: ZoneId): Instant? {
    val s = raw.replaceFirst(' ', 'T')
    runCatching { return OffsetDateTime.parse(s).toInstant() }
    runCatching { return Instant.parse(s) }
    runCatching { return LocalDateTime.parse(s).atZone(zone).toInstant() }
    runCatching { return LocalDate.parse(s).atStartOfDay(zone).toInstant() }
    runCatching { return YearMonth.parse(s).atDay(1).atStartOfDay(zone).toInstant() }
    return null
}

internal fun zoneLabel(zone: ZoneId): String =
    if (zone == ZoneOffset.UTC || zone.id == "Z" || zone.id == "UTC") "UTC" else zone.id
