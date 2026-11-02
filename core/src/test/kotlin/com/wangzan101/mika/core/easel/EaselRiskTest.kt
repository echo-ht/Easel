package com.wangzan101.mika.core.easel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EaselRiskTest {
    @Test fun highFindingsBlockAndEvidenceIsMasked() {
        val report = EaselRiskScanner.scan("token sk-abcdefghijklmnopqrstuvwx and http://10.0.0.2:8080", "xhs")
        assertEquals(RiskLevel.HIGH, report.level)
        assertEquals(2, report.blockCount)
        assertTrue(report.findings.map { it.code }.containsAll(listOf("secret", "private_network")))
        assertFalse(report.findings.joinToString { it.evidence }.contains("sk-abcdefghijklmnopqrstuvwx"))
        assertFalse(report.findings.joinToString { it.evidence }.contains("10.0.0.2"))
    }

    @Test fun mediumClaimsWarnWithoutBlocking() {
        val report = EaselRiskScanner.scan("全网第一，百分百有效，包治百病。", "douyin")
        assertEquals(RiskLevel.MEDIUM, report.level)
        assertEquals(0, report.blockCount)
        assertTrue(report.warnCount > 0)
    }

    @Test fun cleanTextIsLow() {
        val report = EaselRiskScanner.scan("今天做了三张卡片，记录一次真实的制作过程。")
        assertEquals(RiskLevel.LOW, report.level)
        assertEquals(0, report.findings.size)
    }
}
