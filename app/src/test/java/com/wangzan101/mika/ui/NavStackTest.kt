package com.wangzan101.mika.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavStackTest {
    @Test fun backReturnsToWhereYouCameFrom() {
        val nav = NavStack()
        assertEquals(Route.Easel, nav.current)
        assertFalse(nav.canGoBack)
        assertFalse(nav.back())

        nav.push(Route.Settings)
        nav.push(Route.Memory)
        assertEquals(listOf(Route.Easel, Route.Settings, Route.Memory), nav.stack)
        assertTrue(nav.back())
        assertEquals(Route.Settings, nav.current)
        nav.back()
        assertEquals(Route.Easel, nav.current)
    }

    @Test fun samePageIsNotPushedTwice() {
        val nav = NavStack()
        nav.push(Route.PhoneSetup)
        nav.push(Route.PhoneSetup)
        assertEquals(listOf(Route.Easel, Route.PhoneSetup), nav.stack)
    }

    @Test fun easelIsTheRootForChatAndReset() {
        val nav = NavStack(listOf(Route.Settings, Route.Memory))
        assertEquals(listOf(Route.Easel, Route.Settings, Route.Memory), nav.stack)
        nav.openChat()
        assertEquals(listOf(Route.Easel, Route.Chat), nav.stack)
        assertEquals(Route.Chat, nav.current)
        nav.push(Route.Plans)
        nav.reset()
        assertEquals(listOf(Route.Easel), nav.stack)
    }

    @Test fun pushingEaselResetsToTheRoot() {
        val nav = NavStack()
        nav.openChat()
        nav.push(Route.Settings)
        nav.push(Route.Easel)
        assertEquals(listOf(Route.Easel), nav.stack)
        assertFalse(nav.canGoBack)
    }

    @Test fun restoredStacksAlwaysStartWithEaselAndAvoidDuplicates() {
        assertEquals(
            listOf(Route.Easel, Route.Chat, Route.Run),
            NavStack(listOf(Route.Chat, Route.Chat, Route.Run)).stack,
        )
        assertEquals(listOf(Route.Easel), NavStack(emptyList()).stack)
        assertEquals(
            listOf(Route.Easel, Route.Chat, Route.Settings),
            NavStack(listOf(Route.Chat, Route.Easel, Route.Chat, Route.Settings)).stack,
        )
    }

    @Test fun backFromAPageOpenedInTheDrawerReopensTheDrawerAtChat() {
        val nav = NavStack()
        nav.openChat()
        nav.pushFromDrawer(Route.Settings)
        nav.push(Route.Memory)
        nav.back()
        assertEquals(Route.Settings, nav.current)
        assertFalse(nav.reopenDrawer)
        nav.back()
        assertEquals(Route.Chat, nav.current)
        assertTrue(nav.reopenDrawer)
        nav.drawerReopened()
        assertFalse(nav.reopenDrawer)
    }

    @Test fun pagesNotOpenedFromTheDrawerReturnToChatThenEasel() {
        val nav = NavStack()
        nav.openChat()
        nav.push(Route.Settings)
        nav.back()
        assertEquals(Route.Chat, nav.current)
        assertFalse(nav.reopenDrawer)
        nav.back()
        assertEquals(Route.Easel, nav.current)
    }

    @Test fun openingAConversationClearsDrawerReturnState() {
        val nav = NavStack()
        nav.openChat()
        nav.pushFromDrawer(Route.Plans)
        nav.reset()
        assertFalse(nav.reopenDrawer)
        nav.push(Route.Settings)
        nav.back()
        assertFalse(nav.reopenDrawer)
    }
}
