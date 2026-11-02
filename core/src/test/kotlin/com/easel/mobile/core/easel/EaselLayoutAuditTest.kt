package com.easel.mobile.core.easel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EaselLayoutAuditTest {
    private fun frame(height: Int = 100, width: Int = 40, filled: (Int) -> Boolean): Array<IntArray> =
        Array(height) { y -> IntArray(width) { x -> if (filled(y)) (x % 2) * 255 else 128 } }

    @Test fun aFullyFilledFramePasses() {
        val audit = EaselLayoutAudit.analyze(frame { true })
        assertTrue(audit.pass)
        assertEquals(1.0, audit.fill, 0.001)
        assertEquals(1.0, audit.span, 0.001)
        assertEquals(0.0, audit.bottomDead, 0.001)
        assertEquals(0.0, audit.maxGap, 0.001)
        assertTrue(audit.problems.isEmpty())
    }

    @Test fun topHeavyCardsFailForDeadBottomAndSpan() {
        val audit = EaselLayoutAudit.analyze(frame { it < 35 })
        assertFalse(audit.pass)
        assertTrue(audit.bottomDead > EaselLayoutAudit.DEAD_BAND_MAX)
        assertTrue(audit.span < EaselLayoutAudit.SPAN_MIN)
        assertTrue(audit.bands.last() < 0.10)
        assertTrue(audit.problems.any { it.contains("Bottom dead space") })
        assertTrue(audit.problems.any { it.contains("Vertical content span") })
        assertTrue(audit.problems.any { it.contains("Top-heavy") })
    }

    @Test fun aLargeInnerGapFailsEvenWhenBothEndsAreFilled() {
        val audit = EaselLayoutAudit.analyze(frame { it < 35 || it >= 65 })
        assertFalse(audit.pass)
        assertTrue(audit.maxGap > EaselLayoutAudit.DEAD_BAND_MAX)
        assertEquals(0.0, audit.bottomDead, 0.001)
        assertTrue(audit.problems.single().contains("Largest inner gap"))
    }

    @Test fun verticalGradientsHaveNoHorizontalEdgesAndCountAsEmpty() {
        val rows = Array(100) { y -> IntArray(40) { y * 255 / 99 } }
        val audit = EaselLayoutAudit.analyze(rows)
        assertFalse(audit.pass)
        assertEquals(0.0, audit.fill, 0.001)
        assertEquals(0.0, audit.span, 0.001)
        assertEquals(1.0, audit.bottomDead, 0.001)
    }

    @Test fun aShortGapInsideContentIsAllowed() {
        val audit = EaselLayoutAudit.analyze(frame { it !in 50..55 })
        assertTrue(audit.pass)
        assertEquals(0.06, audit.maxGap, 0.001)
    }
}
