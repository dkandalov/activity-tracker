package activitytracker

import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.apache.commons.csv.CSVPrinter
import org.joda.time.DateTime
import org.junit.Test

class TrackerEventTest {
	@Test void "convert event object into csv line"() {
		def dateTime = DateTime.parse("2016-03-03T01:02:03.000")
		def event = new TrackerEvent(dateTime, "user", "IdeState", "Active", "banners", "Editor", "/path/to/file", "", 1938, 57)

		def s = formatAsTrackerEvent(event)
		assert s == "2016-03-03T01:02:03.000Z,user,IdeState,Active,banners,Editor,/path/to/file,,1938,57\r\n"
	}

	@Test void "convert csv line to event object"() {
		def event = parseAsTrackerEvent("2016-03-03T01:02:03.000Z,user,IdeState,Active,banners,Editor,/path/to/file,,1938,57")

		def dateTime = DateTime.parse("2016-03-03T01:02:03.000")
		assert event == new TrackerEvent(dateTime, "user", "IdeState", "Active", "banners", "Editor", "/path/to/file", "", 1938, 57)
	}

	@Test void "convert csv line with some millis to event object"() {
		def event = parseAsTrackerEvent("2016-03-03T01:02:03.12Z,user,IdeState,Active,banners,Editor,/path/to/file,,1938,57")

		def dateTime = DateTime.parse("2016-03-03T01:02:03.012Z")
		assert event == new TrackerEvent(dateTime, "user", "IdeState", "Active", "banners", "Editor", "/path/to/file", "", 1938, 57)
	}

	@Test void "convert csv line with no millis to event object"() {
		def event = parseAsTrackerEvent("2016-03-03T01:02:03Z,user,IdeState,Active,banners,Editor,/path/to/file,,1938,57")

		def dateTime = DateTime.parse("2016-03-03T01:02:03.000Z")
		assert event == new TrackerEvent(dateTime, "user", "IdeState", "Active", "banners", "Editor", "/path/to/file", "", 1938, 57)
	}

	@Test void "convert csv line with no timezone (old log format) to event object"() {
		def event = parseAsTrackerEvent("2016-03-03T01:02:03.123,user,IdeState,Active,banners,Editor,/path/to/file,,1938,57")

		def dateTime = DateTime.parse("2016-03-03T01:02:03.123Z")
		assert event == new TrackerEvent(dateTime, "user", "IdeState", "Active", "banners", "Editor", "/path/to/file", "", 1938, 57)
	}

	private static String formatAsTrackerEvent(TrackerEvent event) {
		def stringBuilder = new StringBuilder()
		event.toCsv(new CSVPrinter(stringBuilder, CSVFormat.RFC4180))
		stringBuilder.toString()
	}

	private static TrackerEvent parseAsTrackerEvent(String csvLine) {
		def parser = new CSVParser(new StringReader(csvLine), CSVFormat.RFC4180)
		def record = parser.getRecords().first()
		def event = TrackerEvent.fromCsv(record)
		event
	}
}
