package dev.mike.fierolauncher

import org.junit.Assert.assertEquals
import org.junit.Test

class NoticesTest {

    private fun n(
        key: String,
        time: Long = 0,
        clearable: Boolean = true,
        transport: Boolean = false,
        tag: String? = null,
        title: String = key,
        text: String = "",
        summary: Boolean = false,
        group: String? = null,
    ) = Notice(key, "pkg", tag, title, text, time, clearable, transport, summary, group)

    private fun keys(list: List<Notice>) = Notices.visible(list).map { it.key }

    @Test fun dropsMediaControls() =
        assertEquals(listOf("call"), keys(listOf(n("spotify", transport = true), n("call"))))

    @Test fun dropsOwnOverlayNotice() = assertEquals(
        listOf("call"),
        keys(listOf(n("overlay", tag = "com.android.server.wm.AlertWindowNotification - dev.mike.fierolauncher"), n("call"))),
    )

    @Test fun dropsEmpty() = assertEquals(listOf("a"), keys(listOf(n("a"), n("blank", title = "", text = " "))))

    @Test fun newestFirstClearableBeforeOngoing() = assertEquals(
        listOf("new", "old", "usb"),
        keys(listOf(n("old", time = 1), n("usb", time = 9, clearable = false), n("new", time = 5))),
    )

    @Test fun importantFirst() {
        val text = Notice("sms", "com.android.mms", null, "Mom", "hi", 1, true, false, false, null)
        val email = Notice("mail", "com.android.email", null, "Bill", "due", 9, true, false, false, null, quiet = true)
        assertEquals(listOf("sms", "mail"), Notices.visible(listOf(email, text)).map { it.key })
    }

    @Test fun whatGetsQuieted() {
        // kept: texts, calls, voicemail, alarms, Fiero's own apps
        assertEquals(false, Notices.shouldQuiet("com.android.mms", transport = false, clearable = true))
        assertEquals(false, Notices.shouldQuiet("com.android.dialer", transport = false, clearable = true))
        assertEquals(false, Notices.shouldQuiet("com.android.phone", transport = false, clearable = false))
        assertEquals(false, Notices.shouldQuiet("com.android.deskclock", transport = false, clearable = false))
        assertEquals(false, Notices.shouldQuiet("dev.mike.fieronav", transport = false, clearable = false))
        // quieted: media players, Android's own (Wi-Fi offers, USB, overlay), other apps' clearable notices
        assertEquals(true, Notices.shouldQuiet("com.spotify.music", transport = true, clearable = false))
        assertEquals(true, Notices.shouldQuiet("android", transport = false, clearable = false))
        assertEquals(true, Notices.shouldQuiet("com.android.systemui", transport = false, clearable = true))
        assertEquals(true, Notices.shouldQuiet("com.android.email", transport = false, clearable = true))
        // other apps' ongoing notices (foreground services) are left alone
        assertEquals(false, Notices.shouldQuiet("org.fdroid.fdroid", transport = false, clearable = false))
    }

    @Test fun summaryOnlyWithoutMembers() {
        assertEquals(
            listOf("msg1"),
            keys(listOf(n("sum", summary = true, group = "g"), n("msg1", group = "g"))),
        )
        assertEquals(listOf("missed"), keys(listOf(n("missed", summary = true, group = "MissedCall"))))
    }
}
