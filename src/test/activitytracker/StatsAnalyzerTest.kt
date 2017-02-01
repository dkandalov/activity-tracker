package activitytracker

import activitytracker.TrackerEvent.Companion.parseDateTime
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.joda.time.DateTime
import org.junit.Test

class StatsAnalyzerTest {
    @Test fun `count amount of seconds spent in editor per file`() {
        val event = TrackerEvent(DateTime(0), "", "IdeState", "", "", "Editor", "", "", 0, 0, "")
        val eventSequence = sequenceOf(
                event.copy(time = parseDateTime("2016-03-03T01:02:03.000"), file = "1.txt"),
                event.copy(time = parseDateTime("2016-03-03T01:02:05.000"), file = "1.txt"),
                event.copy(time = parseDateTime("2016-03-03T01:02:06.000"), file = "2.txt")
        ).constrainOnce()

        val stats = analyze(eventSequence)

        assertThat(stats.secondsInEditorByFile, equalTo(listOf(
                Pair("1.txt", 2),
                Pair("2.txt", 1),
                Pair("Total", 3)
        )))
    }
}