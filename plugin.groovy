import actiontracker2.PluginUI
import actiontracker2.TrackerEvent
import actiontracker2.TrackerLog
import com.intellij.ide.IdeEventQueue
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.ex.AnActionListener
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.IdeFrame
import com.intellij.openapi.wm.WindowManager
import com.intellij.psi.*
import groovy.time.TimeCategory
import liveplugin.implementation.GlobalVar

import javax.swing.*
import java.awt.*
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.util.concurrent.atomic.AtomicReference

import static liveplugin.PluginUtil.*

class ActionTracker {

}

class ActionTrackerPlugin {
	private final TrackerLog trackerLog
	private final GlobalVar<Boolean> isTracking
	private final GlobalVar<Disposable> trackingDisposable
	private final Disposable pluginDisposable
	PluginUI pluginUI

	ActionTrackerPlugin(TrackerLog trackerLog, Disposable pluginDisposable) {
		this.trackerLog = trackerLog
		this.isTracking = newGlobalVar("ActionTrackerII.isTracking", false) as GlobalVar<Boolean>
		this.trackingDisposable = newGlobalVar("ActionTrackerII.disposable") as GlobalVar<Disposable>
		this.pluginDisposable = pluginDisposable
	}

	def onIdeStartup() {
		isTracking.set(true)
		startTracking(trackerLog, trackingDisposable.get())
		pluginUI.update(isTracking.get())
	}

	def toggleTracking() {
		isTracking.set{ !it }
		if (isTracking.get()) {
			def disposable = new Disposable() {
				@Override void dispose() {
					isTracking.set(false)
				}
			}
			Disposer.register(pluginDisposable, disposable)
			trackingDisposable.set(disposable)

			startTracking(trackerLog, disposable)
		} else {
			def disposable = trackingDisposable.get()
			if (disposable != null) {
				Disposer.dispose(disposable)
			}
		}
		pluginUI.update(isTracking.get())
	}

	private void startTracking(TrackerLog trackerLog, Disposable parentDisposable) {
		ActionManager.instance.addAnActionListener(new AnActionListener() {
			@Override void afterActionPerformed(AnAction anAction, DataContext dataContext, AnActionEvent anActionEvent) {
				def actionId = ActionManager.instance.getId(anAction)
				trackerLog.append(createLogEvent(actionId))
			}
			@Override void beforeActionPerformed(AnAction anAction, DataContext dataContext, AnActionEvent anActionEvent) {}
			@Override void beforeEditorTyping(char c, DataContext dataContext) {}
		}, parentDisposable)

		IdeEventQueue.instance.addPostprocessor(new IdeEventQueue.EventDispatcher() {
			@Override boolean dispatch(AWTEvent awtEvent) {
				if (awtEvent instanceof MouseEvent && awtEvent.getID() == MouseEvent.MOUSE_CLICKED) {
					trackerLog.append(createLogEvent("MouseEvent:" + awtEvent.button + ":" + awtEvent.modifiers))

	//			} else if (awtEvent instanceof MouseWheelEvent && awtEvent.getID() == MouseEvent.MOUSE_WHEEL) {
	//				trackerLog.append(createLogEvent("MouseWheelEvent:" + awtEvent.scrollAmount + ":" + awtEvent.wheelRotation))

				} else if (awtEvent instanceof KeyEvent && awtEvent.getID() == KeyEvent.KEY_PRESSED) {
					//show("KeyEvent:" + (awtEvent.keyChar as int) + ":" + awtEvent.modifiers)
					trackerLog.append(createLogEvent("KeyEvent:" + (awtEvent.keyChar as int) + ":" + awtEvent.modifiers))
				}
	//			show(awtEvent)
				false
			}
		}, parentDisposable)

		// consider using com.intellij.openapi.actionSystem.impl.ActionManagerImpl#addTimerListener
		def isDisposed = new AtomicReference<Boolean>(false)
		new Thread({
			while (!isDisposed.get()) {
				AtomicReference logEvent = new AtomicReference<TrackerEvent>()
				SwingUtilities.invokeAndWait {
					logEvent.set(createLogEvent())
				}
				if (logEvent != null) trackerLog.append(logEvent.get())
				Thread.sleep(1000)
			}
		} as Runnable).start()
		Disposer.register(parentDisposable, new Disposable() {
			@Override void dispose() {
				isDisposed.set(true)
			}
		})
	}

	static TrackerEvent createLogEvent(String actionId = "", Date time = now()) {
		IdeFrame activeFrame = WindowManager.instance.allProjectFrames.find{it.active}
		// this tracks project frame as inactive during refactoring
		// (e.g. when "Rename class" frame is active)
		def project = activeFrame?.project
		if (project == null) return new TrackerEvent(time, "", "", "", actionId)
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

	static Date now() { new Date() }
	static Date minus30minutesFrom(Date time) {
		use(TimeCategory) { time - 30.minutes }
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

	private static <T> T findParent(PsiElement element, Closure matches) {
		if (element == null) null
		else if (matches(element)) element as T
		else findParent(element.parent, matches)
	}
}

def trackerLog = new TrackerLog(pluginPath + "/stats")
def plugin = new ActionTrackerPlugin(trackerLog, pluginDisposable)
def pluginUI = new PluginUI(plugin).init(pluginDisposable)

//if (isIdeStartup) {
	plugin.onIdeStartup()
//}
if (!isIdeStartup) show("Reloaded ActionTracker II")


