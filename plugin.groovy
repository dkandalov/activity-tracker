import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.AnActionListener
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

def statsLog = new TrackerLog(pluginPath + "/stats")
def isTrackingVarName = "WhatIWorkOnStats.isTracking"

if (isIdeStartup && !getGlobalVar(isTrackingVarName)) {
	setGlobalVar(isTrackingVarName, true)
	startTracking(statsLog, isTrackingVarName)
	show("Action tracking: ON")
}
if (!isIdeStartup) show("Reloaded action tracker 2")


registerAction("WhatIWorkOnStats", "ctrl shift alt O") { AnActionEvent actionEvent ->
    def trackCurrentFile = new AnAction() {
        @Override void actionPerformed(AnActionEvent event) {
            def trackingIsOn = changeGlobalVar(isTrackingVarName, false) { !it }
            if (trackingIsOn) {
	            startTracking(statsLog, isTrackingVarName)
            }
            show("Action tracking: " + (trackingIsOn ? "ON" : "OFF"))
        }

        @Override void update(AnActionEvent event) {
            def isTracking = getGlobalVar(isTrackingVarName, false)
            event.presentation.text = (isTracking ? "Stop tracking" : "Start tracking")
        }
    }
    def statistics = new AnAction("Last 30 min stats") {
        @Override void actionPerformed(AnActionEvent event) {
            def history = statsLog.readHistory(minus30minutesFrom(now()), now())
	        if (history.empty) show("There is no recorded history to analyze")
	        else {
		        show(EventsAnalyzer.asString(aggregateByFile(history)))
		        show(EventsAnalyzer.asString(aggregateByElement(history)))
	        }
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

void startTracking(TrackerLog statsLog, String isTrackingVarName) {
	if (false) {
		ActionManager.instance.addAnActionListener(new AnActionListener() {
			@Override
			void afterActionPerformed(AnAction anAction, DataContext dataContext, AnActionEvent anActionEvent) {
				def actionId = ActionManager.instance.getId(anAction)
				statsLog.append(createLogEvent(actionId))
			}

			@Override
			void beforeActionPerformed(AnAction anAction, DataContext dataContext, AnActionEvent anActionEvent) {}

			@Override
			void beforeEditorTyping(char c, DataContext dataContext) {}
		})
	}
	new Thread({
		while (getGlobalVar(isTrackingVarName)) {
			AtomicReference logEvent = new AtomicReference<TrackerEvent>()
			SwingUtilities.invokeAndWait {
				logEvent.set(createLogEvent())
			}
			if (logEvent != null) statsLog.append(logEvent.get())
			Thread.sleep(1000)
		}
	} as Runnable).start()
}

Date now() { new Date() }
Date minus30minutesFrom(Date time) {
	use(TimeCategory) { time - 30.minutes }
}

TrackerEvent createLogEvent(String actionId = "", Date time = now()) {
	IdeFrame activeFrame = WindowManager.instance.allProjectFrames.find{it.active}
	// this tracks project frame as inactive during refactoring
	// (e.g. when "Rename class" frame is active)
	if (activeFrame == null) return new TrackerEvent(time, "", "", "", actionId)
	def project = activeFrame.project
	def editor = currentEditorIn(project)
	if (editor == null) return new TrackerEvent(time, project.name, "", "", actionId)

	def elementAtOffset = currentPsiFileIn(project)?.findElementAt(editor.caretModel.offset)
	PsiMethod psiMethod = findParent(elementAtOffset, {it instanceof PsiMethod})
	PsiFile psiFile = findParent(elementAtOffset, {it instanceof PsiFile})
	def currentElement = (psiMethod == null ? psiFile : psiMethod)

	// this doesn't take into account time spent in toolwindows
	// (when the same frame is active but editor doesn't have focus)
	def file = currentFileIn(project)
	// don't try to shorten file name by excluding project because different projects might have same files
	def filePath = (file == null ? "" : file.path)
	new TrackerEvent(time, project.name, filePath, fullNameOf(currentElement), actionId)
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

