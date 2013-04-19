import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.wm.IdeFrame
import com.intellij.openapi.wm.WindowManager
import com.intellij.psi.*
import groovy.time.TimeCategory

import javax.swing.*
import java.text.SimpleDateFormat
import java.util.concurrent.atomic.AtomicReference

import static EventsAnalyzer.*
import static intellijeval.PluginUtil.*

def logEventsIO = new LogEventsIO(pluginPath + "/stats")
def isTrackingVarName = "WhatIWorkOnStats.isTracking"

if (isIdeStartup && !getGlobalVar(isTrackingVarName)) {
	setGlobalVar(isTrackingVarName, true)
	startTrackingWhatIsGoingOn(logEventsIO, isTrackingVarName)
	show("Tracking current file: ON")
}

registerAction("WhatIWorkOnStats", "ctrl shift alt O") { AnActionEvent actionEvent ->
	JBPopupFactory.instance.createActionGroupPopup(
			"Current file statistics",
			new DefaultActionGroup().with{
				add(new AnAction() {
					@Override void actionPerformed(AnActionEvent event) {
						def trackingIsOn = changeGlobalVar(isTrackingVarName, false){ !it }
						if (trackingIsOn) startTrackingWhatIsGoingOn(logEventsIO, isTrackingVarName)
						show("Tracking current file: " + (trackingIsOn ? "ON" : "OFF"))
					}

					@Override void update(AnActionEvent event) {
						def isTracking = getGlobalVar(isTrackingVarName, false)
						event.presentation.text = (isTracking ? "Stop tracking current file" : "Start tracking current file")
					}
				})
				add(new AnAction("Analyze last 30 min history") {
					@Override void actionPerformed(AnActionEvent event) {
						def history = logEventsIO.readHistory(minus30minutesFrom(now()), now())
						show(EventsAnalyzer.asString(aggregateByFile(history)))
						show(EventsAnalyzer.asString(aggregateByElement(history)))
					}
				})
				add(new AnAction("Delete all history") {
					@Override void actionPerformed(AnActionEvent event) {
						logEventsIO.resetHistory()
						show("All history was deleted")
					}
				})
				it
			},
			actionEvent.dataContext,
			JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
			true
	).showCenteredInCurrentWindow(actionEvent.project)
}
show("reloaded")

void startTrackingWhatIsGoingOn(LogEventsIO statsWriter, String isTrackingVarName) {
	new Thread({
		while (getGlobalVar(isTrackingVarName)) {
			AtomicReference logEvent = new AtomicReference<LogEvent>()
			SwingUtilities.invokeAndWait { logEvent.set(createLogEvent(now())) }
			if (logEvent != null) statsWriter.append(logEvent.get().toCsv())
			Thread.sleep(1000)
		}
	} as Runnable).start()
}

Date now() { new Date() }
Date minus30minutesFrom(Date time) {
	use(TimeCategory) { time - 30.minutes }
}

LogEvent createLogEvent(Date now) {
	IdeFrame activeFrame = WindowManager.instance.allProjectFrames.find{it.active}
	// this tracks project frame as inactive during refactoring
	// (e.g. when "Rename class" frame is active)
	if (activeFrame == null) return new LogEvent(now, "", "", "")
	def project = activeFrame.project
	def editor = currentEditorIn(project)
	if (editor == null) return new LogEvent(now, project.name, "", "")

	def elementAtOffset = currentPsiFileIn(project)?.findElementAt(editor.caretModel.offset)
	PsiMethod psiMethod = findParent(elementAtOffset, {it instanceof PsiMethod})
	PsiFile psiFile = findParent(elementAtOffset, {it instanceof PsiFile})
	def currentElement = (psiMethod == null ? psiFile : psiMethod)

	// this doesn't take into account time spent in toolwindows
	// (when the same frame is active but editor doesn't have focus)
	def file = currentFileIn(project)
	// don't try to shorten file name by excluding project because different projects might have same files
	def filePath = (file == null ? "" : file.path)
	new LogEvent(now, project.name, filePath, fullNameOf(currentElement))
}

private static String fullNameOf(PsiElement psiElement) {
	if (psiElement == null || psiElement instanceof PsiFile) ""
	else if (psiElement in PsiAnonymousClass) {
		def parentName = fullNameOf(psiElement.parent)
		def name = "[" + psiElement.baseClassType.className + "]"
		parentName.empty ? name : (parentName + "::" + name)
	} else if (psiElement instanceof PsiMethod || psiElement instanceof PsiClass) {
		def parentName = fullNameOf(psiElement.parent)
		parentName.empty ? psiElement.name : (parentName + "::" + psiElement.name)
	} else {
		fullNameOf(psiElement.parent)
	}
}

private def <T> T findParent(PsiElement element, Closure matches) {
	if (element == null) null
	else if (matches(element)) element as T
	else findParent(element.parent, matches)
}


class EventsAnalyzer {
	static Map<String, Integer> aggregateByFile(List<LogEvent> events) {
		events.groupBy{it.file}.collectEntries{[it.key, it.value.size()]}.sort{-it.value} as Map<String, Integer>
	}

	static Map<String, Integer> aggregateByElement(List<LogEvent> events) {
		events.groupBy{it.element}.collectEntries{[it.key, it.value.size()]}.sort{-it.value} as Map<String, Integer>
	}

	static asString(Map<String, Integer> map) {
		def durationAsString = { Integer seconds ->
			seconds.intdiv(60) + ":" + String.format("%02d", seconds % 60)
		}
		def keyAsString = { it == null ? "[not in editor]" : it }
		map.collectEntries{ [keyAsString(it.key), durationAsString(it.value)] }.entrySet().join("\n")
	}
}

class LogEventsIO {
	private final String statsFilePath

	LogEventsIO(String path) {
		this.statsFilePath = path + "/stats.csv"
	}

	def append(String csvLine) {
		new File(statsFilePath).append(csvLine + "\n")
	}

	def resetHistory() {
		new File(statsFilePath).delete()
	}

	List<LogEvent> readHistory(Date fromTime, Date toTime) {
		new File(statsFilePath).withReader { reader ->
			def result = []
			String line
			while ((line = reader.readLine()) != null) {
				def event = LogEvent.fromCsv(line)
				if (event.time.after(fromTime) && event.time.before(toTime))
					result << event
				if (event.time.after(toTime)) break
			}
			result
		}
	}
}

@groovy.transform.Immutable
final class LogEvent {
	private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("kk:mm:ss dd/MM/yyyy")
	Date time
	String projectName
	String file
	String element

	static LogEvent fromCsv(String csvLine) {
		def (date, projectName, file, element) = csvLine.split(",").toList()
		new LogEvent(TIME_FORMAT.parse(date), projectName, file, element)
	}

	String toCsv() {
		"${TIME_FORMAT.format(time)},$projectName,$file,$element"
	}
}
