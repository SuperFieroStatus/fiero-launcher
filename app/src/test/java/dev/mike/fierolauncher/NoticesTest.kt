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

    @Test fun summaryOnlyWithoutMembers() {
        assertEquals(
            listOf("msg1"),
            keys(listOf(n("sum", summary = true, group = "g"), n("msg1", group = "g"))),
        )
        assertEquals(listOf("missed"), keys(listOf(n("missed", summary = true, group = "MissedCall"))))
    }
}
