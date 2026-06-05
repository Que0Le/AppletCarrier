package com.example.applet_carrier.platform

import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TimestampTest {

    private val utc = ZoneOffset.UTC
    private val ref = Instant.parse("2026-12-01T00:00:00Z") // epoch 1796083200

    private fun ok(p: TsParse): TsParse.Ok {
        assertTrue(p is TsParse.Ok, "expected Ok but was $p")
        return p
    }

    // ---- parser: digit-count heuristic ----

    @Test
    fun date8_parsesAndSnaps() {
        val r = ok(parseTimestamp("20261201", utc))
        assertEquals(TsKind.DATE, r.kind)
        assertEquals("2026-12-01", r.canonical)
        assertEquals(ref, r.instant)
        assertEquals(1796083200L, r.instant.epochSecond)
        // offers epoch overrides
        assertTrue(r.overrides.any { it.mode == ForcedMode.EPOCH_SECONDS })
        assertTrue(r.overrides.any { it.mode == ForcedMode.EPOCH_MILLIS })
    }

    @Test
    fun datetime14_parses() {
        val r = ok(parseTimestamp("20261201120000", utc))
        assertEquals(TsKind.DATE, r.kind)
        assertEquals("2026-12-01T12:00:00", r.canonical)
        assertEquals(Instant.parse("2026-12-01T12:00:00Z"), r.instant)
    }

    @Test
    fun year4_fillsDefaults() {
        val r = ok(parseTimestamp("2026", utc))
        assertEquals("2026", r.canonical)
        assertEquals(Instant.parse("2026-01-01T00:00:00Z"), r.instant)
    }

    @Test
    fun epochSeconds10_parses() {
        val r = ok(parseTimestamp("1796083200", utc))
        assertEquals(TsKind.EPOCH_SECONDS, r.kind)
        assertEquals(ref, r.instant)
    }

    @Test
    fun epochMillis13_parses() {
        val r = ok(parseTimestamp("1796083200000", utc))
        assertEquals(TsKind.EPOCH_MILLIS, r.kind)
        assertEquals(ref, r.instant)
    }

    @Test
    fun forcedEpoch_overridesDateHeuristic() {
        val r = ok(parseTimestamp("20261201", utc, forced = ForcedMode.EPOCH_SECONDS))
        assertEquals(TsKind.EPOCH_SECONDS, r.kind)
        assertEquals(Instant.ofEpochSecond(20261201L), r.instant)
    }

    // ---- parser: iso-ish, keyword, empty, invalid ----

    @Test
    fun empty_isEmpty() = assertTrue(parseTimestamp("", utc) is TsParse.Empty)

    @Test
    fun now_keyword() = assertEquals(TsKind.NOW, ok(parseTimestamp("now", utc)).kind)

    @Test
    fun iso_withZ() {
        assertEquals(ref, ok(parseTimestamp("2026-12-01T00:00:00Z", utc)).instant)
    }

    @Test
    fun iso_spaceInsteadOfT_usesSourceZone() {
        assertEquals(Instant.parse("2026-12-01T12:30:00Z"), ok(parseTimestamp("2026-12-01 12:30", utc)).instant)
    }

    @Test
    fun invalidMonth_isRejectedWithMessage() {
        val p = parseTimestamp("20261301", utc)
        assertTrue(p is TsParse.Invalid && p.message.contains("month 13"), "was $p")
    }

    @Test
    fun garbage_isInvalid() = assertTrue(parseTimestamp("hello", utc) is TsParse.Invalid)

    // ---- formatting ----

    private val zones = listOf(
        ZoneId.of("UTC"), ZoneId.of("Europe/Berlin"),
        ZoneId.of("America/New_York"), ZoneId.of("Asia/Tokyo"),
    )

    private fun groups() = buildFormatGroups(
        instant = ref,
        sourceZone = utc,
        favoriteZones = zones,
        offsetStyles = OffsetStyles.ALL,
        now = Instant.parse("2026-06-01T00:00:00Z"),
    )

    private fun row(title: String, predicate: (FormatRow) -> Boolean): FormatRow {
        val group = groups().first { it.title == title }
        return group.rows.first(predicate)
    }

    @Test
    fun iso_group_hasAllOffsetStyles() {
        val iso = groups().first { it.title == "ISO 8601" }.rows.map { it.value }
        assertTrue("2026-12-01T00:00:00Z" in iso)
        assertTrue("2026-12-01T00:00:00+00:00" in iso)
        assertTrue("2026-12-01T00:00:00+0000" in iso)
    }

    @Test
    fun epoch_group_values() {
        assertEquals("1796083200", row("Epoch") { it.label == "seconds" }.value)
        assertEquals("1796083200000", row("Epoch") { it.label == "milliseconds" }.value)
    }

    @Test
    fun other_group_httpAndSqlAndCalendar() {
        assertEquals("Tue, 01 Dec 2026 00:00:00 GMT", row("Other") { it.label == "RFC 1123 / HTTP" }.value)
        assertEquals("2026-12-01 00:00:00", row("Other") { it.label?.startsWith("SQL") == true }.value)
        assertEquals("Tuesday · ISO week 49 · day 335", row("Other") { it.value.startsWith("Tuesday") }.value)
    }

    @Test
    fun timezones_group_offsets() {
        assertEquals("2026-12-01T01:00:00+01:00", row("Time zones") { it.label == "Europe/Berlin" }.value)
        assertEquals("2026-11-30T19:00:00-05:00", row("Time zones") { it.label == "America/New_York" }.value)
        assertEquals("2026-12-01T09:00:00+09:00", row("Time zones") { it.label == "Asia/Tokyo" }.value)
    }

    @Test
    fun relative_future_months() {
        val text = row("Other") { it.label == "relative" }.value
        assertEquals("in ~6 months", text)
    }
}
