package actiontracker2
import com.intellij.ide.IdeEventQueue
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.ex.AnActionListener
import com.intellij.openapi.wm.IdeFrame
import com.intellij.openapi.wm.WindowManager
import com.intellij.psi.*
import com.intellij.util.Alarm
import groovy.time.TimeCategory

import java.awt.*
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent

import static liveplugin.PluginUtil.*
import static liveplugin.implementation.Misc.scheduleTask

class ActionTracker {
	private final TrackerLog trackerLog

	ActionTracker(TrackerLog trackerLog) {
		this.trackerLog = trackerLog
	}

	void startTracking(Disposable parentDisposable, int trackIdeStateFrequencyMs = 1000) {
		startIdeEventListeners(parentDisposable)

		if (trackIdeStateFrequencyMs > 0) {
			scheduleTask(new Alarm(Alarm.ThreadToUse.SWING_THREAD, parentDisposable), trackIdeStateFrequencyMs) {
				trackerLog.append(captureIdeState())
			}
		}
	}

	private void startIdeEventListeners(Disposable parentDisposable) {
		ActionManager.instance.addAnActionListener(new AnActionListener() {
			@Override void afterActionPerformed(AnAction anAction, DataContext dataContext, AnActionEvent anActionEvent) {
				def actionId = ActionManager.instance.getId(anAction)
				trackerLog.append(captureIdeState(actionId))
			}
			@Override void beforeActionPerformed(AnAction anAction, DataContext dataContext, AnActionEvent anActionEvent) {}
			@Override void beforeEditorTyping(char c, DataContext dataContext) {}
		}, parentDisposable)

		IdeEventQueue.instance.addPostprocessor(new IdeEventQueue.EventDispatcher() {
			@Override boolean dispatch(AWTEvent awtEvent) {
				if (awtEvent instanceof MouseEvent && awtEvent.ID == MouseEvent.MOUSE_CLICKED) {
					trackerLog.append(captureIdeState("MouseEvent:" + awtEvent.button + ":" + awtEvent.modifiers))

//				} else if (awtEvent instanceof MouseWheelEvent && awtEvent.getID() == MouseEvent.MOUSE_WHEEL) {
//					trackerLog.append(createLogEvent("MouseWheelEvent:" + awtEvent.scrollAmount + ":" + awtEvent.wheelRotation))

				} else if (awtEvent instanceof KeyEvent && awtEvent.ID == KeyEvent.KEY_PRESSED) {
					trackerLog.append(captureIdeState("KeyEvent:" + (awtEvent.keyChar as int) + ":" + (awtEvent.keyCode as int) + ":" + awtEvent.modifiers))
				}
				false
			}
		}, parentDisposable)
	}

	private static TrackerEvent captureIdeState(String actionId = "", Date time = now()) {
		IdeFrame activeFrame = WindowManager.instance.allProjectFrames.find { it.active }
		// this tracks project frame as inactive during refactoring
		// (e.g. when "Rename class" frame is active)
		def project = activeFrame?.project
		if (project == null) return new TrackerEvent(time, "", "", "", actionId)
		def editor = currentEditorIn(project)
		if (editor == null) return new TrackerEvent(time, project.name, "", "", actionId)

		def elementAtOffset = currentPsiFileIn(project)?.findElementAt(editor.caretModel.offset)
		PsiMethod psiMethod = findParent(elementAtOffset, { it instanceof PsiMethod })
		PsiFile psiFile = findParent(elementAtOffset, { it instanceof PsiFile })
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
