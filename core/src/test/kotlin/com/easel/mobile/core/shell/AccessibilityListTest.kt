package com.easel.mobile.core.shell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessibilityListTest {
    private val easel = PermissionCommands.accessibilityService("com.easel.mobile")
    private val easelLong = "com.easel.mobile/com.easel.mobile.a11y.EaselAccessibilityService"
    private val other = "com.example.reader/.ReaderService"
    private val other2 = "org.sample.keys/org.sample.keys.KeyService"

    @Test
    fun emptyOrUnsetValues() {
        listOf(null, "", "   ", "null", "null\n", ":", " : ").forEach { raw ->
            assertEquals("raw=$raw", emptyList<String>(), AccessibilityList.entries(raw))
            assertFalse(AccessibilityList.contains(raw, easel))
            assertEquals(easel, AccessibilityList.with(raw, easel))
            assertEquals("", AccessibilityList.without(raw, easel))
        }
    }

    @Test
    fun appendKeepsOtherServicesInOrder() {
        assertEquals("$other:$easel", AccessibilityList.with(other, easel))
        assertEquals("$other:$other2:$easel", AccessibilityList.with("$other:$other2\n", easel))
    }

    @Test
    fun alreadyPresentIsNotDuplicated() {
        assertEquals("$other:$easel", AccessibilityList.with("$other:$easel", easel))
        // 系统里存的是完整类名写法,也算已经有了
        assertEquals("$easelLong:$other", AccessibilityList.with("$easelLong:$other", easel))
        assertTrue(AccessibilityList.contains(easelLong, easel))
    }

    @Test
    fun removeKeepsOthersAndHandlesBothSpellings() {
        assertEquals("$other:$other2", AccessibilityList.without("$other:$easel:$other2", easel))
        assertEquals(other, AccessibilityList.without("$easelLong:$other", easel))
        assertEquals(other, AccessibilityList.without(other, easel))
        assertEquals("", AccessibilityList.without(easel, easel))
    }

    @Test
    fun strayColonsAndSpacesAreCleanedUp() {
        assertEquals("$other:$other2:$easel", AccessibilityList.with(":$other:: $other2 :", easel))
        assertEquals("$other:$other2", AccessibilityList.without("$other::$easel:$other2:", easel))
    }

    @Test
    fun differentServiceOfSamePackageIsNotConfused() {
        val sibling = "com.easel.mobile/.a11y.OtherService"
        assertFalse(AccessibilityList.contains(sibling, easel))
        assertEquals(sibling, AccessibilityList.without("$sibling:$easel", easel))
    }
}
