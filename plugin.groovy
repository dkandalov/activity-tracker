import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.wm.IdeFrame
import com.intellij.openapi.wm.WindowManager
import com.intellij.psi.*
import groovy.time.TimeCategory

import javax.swing.*
import java.util.concurrent.atomic.AtomicReference

import static EventsAnalyzer.aggregateByElement
import static EventsAnalyzer.aggregateByFile
import static liveplugin.PluginUtil.*

def statsLog = new StatsLog(pluginPath + "/stats")
def isTrackingVarName = "WhatIWorkOnStats.isTracking"

if (isIdeStartup && !getGlobalVar(isTrackingVarName)) {
	setGlobalVar(isTrackingVarName, true)
	startTrackingWhatIsGoingOn(statsLog, isTrackingVarName)
	show("Tracking current file: ON")
}

registerAction("WhatIWorkOnStats", "ctrl shift alt O") { AnActionEvent actionEvent ->
    def trackCurrentFile = new AnAction() {
        @Override
        void actionPerformed(AnActionEvent event) {
            def trackingIsOn = changeGlobalVar(isTrackingVarName, false) { !it }
            if (trackingIsOn) startTrackingWhatIsGoingOn(statsLog, isTrackingVarName)
            show("Tracking current file: " + (trackingIsOn ? "ON" : "OFF"))
        }

        @Override
        void update(AnActionEvent event) {
            def isTracking = getGlobalVar(isTrackingVarName, false)
            event.presentation.text = (isTracking ? "Stop tracking current file" : "Start tracking current file")
        }
    }
    def statistics = new AnAction("Last 30 min stats") {
        @Override void actionPerformed(AnActionEvent event) {
            def history = statsLog.readHistory(minus30minutesFrom(now()), now())
            show(EventsAnalyzer.asString(aggregateByFile(history)))
            show(EventsAnalyzer.asString(aggregateByElement(history)))
        }
    }
    def deleteAllHistory = new AnAction("Delete all history") {
        @Override void actionPerformed(AnActionEvent event) {
            statsLog.resetHistory()
            show("All history was deleted")
        }
    }

    JBPopupFactory.instance.createActionGroupPopup(
			"Current file statistics",
			new DefaultActionGroup().with {
                add(trackCurrentFile)
                add(statistics)
                add(deleteAllHistory)
				it
			},
			actionEvent.dataContext,
			JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
			true
	).showCenteredInCurrentWindow(actionEvent.project)
}
show("reloaded")

void startTrackingWhatIsGoingOn(StatsLog statsLog, String isTrackingVarName) {
	new Thread({
		while (getGlobalVar(isTrackingVarName)) {
			AtomicReference logEvent = new AtomicReference<LogEvent>()
			SwingUtilities.invokeAndWait {
				logEvent.set(createLogEvent(now()))
			}
			if (logEvent != null) statsLog.append(logEvent.get().toCsv())
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


class StatsLog {
	private final String statsFilePath

	StatsLog(String path) {
        def pathFile = new File(path)
        if (!pathFile.exists()) pathFile.mkdir()
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

