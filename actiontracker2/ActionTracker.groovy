package actiontracker2
import com.intellij.ide.IdeEventQueue
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.ex.AnActionListener
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.openapi.wm.ex.WindowManagerEx
import com.intellij.psi.*
import com.intellij.util.Alarm

import java.awt.*
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.time.LocalDateTime

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
			// note that in order to triggered when IDE dialog window is opened (e.g. override or project settings),
			// alarm must be invoked on pooled thread and with invokeOnEDT() method
			scheduleTask(new Alarm(Alarm.ThreadToUse.POOLED_THREAD, parentDisposable), trackIdeStateFrequencyMs) {
				invokeOnEDT {
					trackerLog.append(captureIdeState("IdeState"))
				}
			}
		}
	}

	private void startIdeEventListeners(Disposable parentDisposable) {
		ActionManager.instance.addAnActionListener(new AnActionListener() {
			@Override void afterActionPerformed(AnAction anAction, DataContext dataContext, AnActionEvent anActionEvent) {
				def actionId = ActionManager.instance.getId(anAction)
				if (actionId != null) { // can be null on 'ctrl+o' action (class com.intellij.openapi.ui.impl.DialogWrapperPeerImpl$AnCancelAction)
					trackerLog.append(captureIdeState(actionId))
				}
			}
			@Override void beforeActionPerformed(AnAction anAction, DataContext dataContext, AnActionEvent anActionEvent) {}
			@Override void beforeEditorTyping(char c, DataContext dataContext) {}
		}, parentDisposable)

		IdeEventQueue.instance.addPostprocessor(new IdeEventQueue.EventDispatcher() {
			@Override boolean dispatch(AWTEvent awtEvent) {
				if (awtEvent instanceof MouseEvent && awtEvent.ID == MouseEvent.MOUSE_CLICKED) {
					trackerLog.append(captureIdeState("MouseEvent", "" + awtEvent.button + ":" + awtEvent.modifiers))

//				} else if (awtEvent instanceof MouseWheelEvent && awtEvent.getID() == MouseEvent.MOUSE_WHEEL) {
//					trackerLog.append(createLogEvent("MouseWheelEvent:" + awtEvent.scrollAmount + ":" + awtEvent.wheelRotation))

				} else if (awtEvent instanceof KeyEvent && awtEvent.ID == KeyEvent.KEY_PRESSED) {
					trackerLog.append(captureIdeState("KeyEvent", "" + (awtEvent.keyChar as int) + ":" + (awtEvent.keyCode as int) + ":" + awtEvent.modifiers))
				}
				false
			}
		}, parentDisposable)
	}

	private static TrackerEvent captureIdeState(String eventType, String eventData = "", LocalDateTime time = LocalDateTime.now()) {
		try {
			def window = WindowManagerEx.instanceEx.mostRecentFocusedWindow
			def ideHasFocus = window != null && window.active
			if (!ideHasFocus) return new TrackerEvent(time, eventType, "", "", "", -1, -1, eventData)

			// use "lastFocusedFrame" to determine project when some dialog is open (e.g. "override" or "project settings")
			def project = IdeFocusManager.findInstance().lastFocusedFrame?.project
			if (project == null) return new TrackerEvent(time, eventType, "", "", "", -1, -1, eventData)
			def editor = currentEditorIn(project)
			if (editor == null) return new TrackerEvent(time, eventType, project.name, "", "", -1, -1, eventData)

			def elementAtOffset = currentPsiFileIn(project)?.findElementAt(editor.caretModel.offset)
			PsiMethod psiMethod = findParent(elementAtOffset, { it instanceof PsiMethod })
			PsiFile psiFile = findParent(elementAtOffset, { it instanceof PsiFile })
			def currentElement = (psiMethod == null ? psiFile : psiMethod)

			// TODO com.intellij.openapi.wm.ToolWindowManager.getActiveToolWindowId
			// this doesn't take into account time spent in toolwindows
			// (when the same frame is active but editor doesn't have focus)
			def file = currentFileIn(project)
			// don't try to shorten file name by excluding project because different projects might have same files
			def filePath = (file == null ? "" : file.path)
			def line = editor.caretModel.logicalPosition.line
			def column = editor.caretModel.logicalPosition.column
			new TrackerEvent(time, eventType, project.name, filePath, fullNameOf(currentElement), line, column, eventData)
		} catch (Exception e) {
			log(e, NotificationType.ERROR) // TODO
			null
		}
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
