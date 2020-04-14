package activitytracker

import activitytracker.TrackerEvent.Type.IdeState
import com.intellij.openapi.util.io.FileUtil
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test

class TrackerLogTests {
    @Test fun `read log events from file`() {
        val tempFile = FileUtil.createTempFile("event-log", "")
        tempFile.writeText("""
            |2016-08-17T13:20:40.113+01:00,user,IdeState,Active,activity-tracker,Editor,/path/to/plugin.groovy,,12,34
            |2016-08-17T13:20:41.120+01:00,user,IdeState,Active,activity-tracker,Popup,/path/to/plugin.groovy,,56,78
        """.trimMargin().trim())

        val events = TrackerLog(tempFile.absolutePath).readEvents { _, e -> e.printStackTrace() }

        assertThat(events.toList(), equalTo(listOf(
            TrackerEvent(TrackerEvent.parseDateTime("2016-08-17T13:20:40.113+01:00"), "user", IdeState, "Active", "activity-tracker", "Editor", "/path/to/plugin.groovy", "", 12, 34, ""),
            TrackerEvent(TrackerEvent.parseDateTime("2016-08-17T13:20:41.120+01:00"), "user", IdeState, "Active", "activity-tracker", "Popup", "/path/to/plugin.groovy", "", 56, 78, "")
        )))
    }
}