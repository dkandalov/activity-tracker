package activitytracker

import activitytracker.TrackerEvent.Companion.parseDateTime
import activitytracker.TrackerEvent.Companion.toTrackerEvent
import activitytracker.TrackerEvent.Type.IdeState
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.apache.commons.csv.CSVPrinter
import org.hamcrest.CoreMatchers.equalTo
import org.junit.Assert.assertThat
import org.junit.Test
import java.io.StringReader

class TrackerEventTests {

    @Test fun `convert event object into csv line`() {
        val dateTime = parseDateTime("2016-03-03T01:02:03.000")
        val event = TrackerEvent(dateTime, "user", IdeState, "Active", "banners", "Editor", "/path/to/file", "", 1938, 57, "Default")
        val expectedCsv = "2016-03-03T01:02:03.000Z,user,IdeState,Active,banners,Editor,/path/to/file,,1938,57,Default\r\n"

        assertThat(event.toCsvLine(), equalTo(expectedCsv))
    }

    @Test fun `convert csv line to event object`() {
        val parsedEvent = "2016-03-03T01:02:03.000Z,user,IdeState,Active,banners,Editor,/path/to/file,,1938,57,Default".parse()
        val dateTime = parseDateTime("2016-03-03T01:02:03.000")
        val event = TrackerEvent(dateTime, "user", IdeState, "Active", "banners", "Editor", "/path/to/file", "", 1938, 57, "Default")

        assertThat(parsedEvent, equalTo(event))
    }

    @Test fun `convert csv line with two-digit millis to event object (0-1-3 beta format)`() {
        val parsedEvent = "2016-03-03T01:02:03.12+00:00,user,IdeState,Active,banners,Editor,/path/to/file,,1938,57".parse()
        val dateTime = parseDateTime("2016-03-03T01:02:03.012+00:00")
        val event = TrackerEvent(dateTime, "user", IdeState, "Active", "banners", "Editor", "/path/to/file", "", 1938, 57, "")

        assertThat(parsedEvent, equalTo(event))
    }

    @Test fun `convert csv line without millis to event object (0-1-3 beta format)`() {
        val dateTime = parseDateTime("2016-03-03T01:02:03.000Z")
        val event = TrackerEvent(dateTime, "user", IdeState, "Active", "banners", "Editor", "/path/to/file", "", 1938, 57, "")
        val parsedEvent = "2016-03-03T01:02:03Z,user,IdeState,Active,banners,Editor,/path/to/file,,1938,57".parse()

        assertThat(parsedEvent, equalTo(event))
    }

    @Test fun `convert csv line with no timezone to event object (0-1-2 beta format)`() {
        val dateTime = parseDateTime("2016-03-03T01:02:03.123Z")
        val event = TrackerEvent(dateTime, "user", IdeState, "Active", "banners", "Editor", "/path/to/file", "", 1938, 57, "")
        val parsedEvent = "2016-03-03T01:02:03.123,user,IdeState,Active,banners,Editor,/path/to/file,,1938,57".parse()

        assertThat(parsedEvent, equalTo(event))
    }

    private fun TrackerEvent.toCsvLine() =
        StringBuilder().let {
            toCsv(CSVPrinter(it, CSVFormat.RFC4180))
            it.toString()
        }

    private fun String.parse(): TrackerEvent {
        val parser = CSVParser(StringReader(this), CSVFormat.RFC4180)
        return parser.records.first().toTrackerEvent()
    }
}

