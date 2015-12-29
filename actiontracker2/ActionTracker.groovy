package actiontracker2
import com.intellij.ide.IdeEventQueue
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.ex.AnActionListener
import com.intellij.openapi.editor.impl.EditorComponentImpl
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.WindowManagerEx
import com.intellij.openapi.wm.impl.IdeFrameImpl
import com.intellij.psi.*
import com.intellij.util.Alarm

import javax.swing.*
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
			// TODO need better scheduling implementation then below because it slowly drifts into the future
			// note that in order to triggered when IDE dialog window is opened (e.g. override or project settings),
			// alarm must be invoked on pooled thread and with invokeOnEDT() method
			scheduleTask(new Alarm(Alarm.ThreadToUse.POOLED_THREAD, parentDisposable), trackIdeStateFrequencyMs) {
				invokeOnEDT {
					trackerLog.append(captureIdeState("IdeState", ""))
				}
			}
		}
	}

	private void startIdeEventListeners(Disposable parentDisposable) {
		def actionManager = ActionManager.instance
		actionManager.addAnActionListener(new AnActionListener() {
			@Override void beforeActionPerformed(AnAction anAction, DataContext dataContext, AnActionEvent anActionEvent) {
				// track action in "before" callback because otherwise timing of action can be wrong
				// (e.g. commit action shows dialog and finishes only after the dialog is closed)
				def actionId = actionManager.getId(anAction)
				if (actionId == null) return // can be null on 'ctrl+o' action (class com.intellij.openapi.ui.impl.DialogWrapperPeerImpl$AnCancelAction)
				trackerLog.append(captureIdeState("Action", actionId))
			}
			@Override void afterActionPerformed(AnAction anAction, DataContext dataContext, AnActionEvent anActionEvent) {}
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

	private static TrackerEvent captureIdeState(String eventType, String eventData, LocalDateTime time = LocalDateTime.now()) {
		try {
			def ideFocusManager = IdeFocusManager.globalInstance
			def focusOwner = ideFocusManager.focusOwner

			def window = WindowManagerEx.instanceEx.mostRecentFocusedWindow
			if (window == null) return TrackerEvent.ideNotInFocus(time, eventType, eventData)

			def ideHasFocus = window.active
			if (!ideHasFocus) {
				IdeFrameImpl ideFrame = findParentComponent(focusOwner){ it instanceof IdeFrameImpl }
				ideHasFocus = ideFrame != null && ideFrame.active
			}
			if (!ideHasFocus) return TrackerEvent.ideNotInFocus(time, eventType, eventData)

			// use "lastFocusedFrame" to be able to obtain project in cases when some dialog is open (e.g. "override" or "project settings")
			def project = ideFocusManager.lastFocusedFrame?.project
			if (project == null) return TrackerEvent.ideNotInFocus(time, eventType, eventData)

			def focusOwnerId
			// check for JDialog before EditorComponentImpl because dialog can belong to editor
			if (findParentComponent(focusOwner){ it instanceof JDialog } != null) {
				focusOwnerId = "Dialog"
			} else if (findParentComponent(focusOwner){ it instanceof EditorComponentImpl } != null) {
				focusOwnerId = "Editor"
			} else {
				focusOwnerId = ToolWindowManager.getInstance(project).activeToolWindowId
				if (focusOwnerId == null) {
					focusOwnerId = "Popup"
				}
			}

			def filePath = ""
			def psiPath = ""
			def line = -1
			def column = -1
			def editor = currentEditorIn(project)
			if (editor != null) {
				def elementAtOffset = currentPsiFileIn(project)?.findElementAt(editor.caretModel.offset)
				PsiMethod psiMethod = findPsiParent(elementAtOffset, { it instanceof PsiMethod })
				PsiFile psiFile = findPsiParent(elementAtOffset, { it instanceof PsiFile })
				def currentElement = (psiMethod == null ? psiFile : psiMethod)

				// keep full file name because projects and libraries might have files with the same names/partial paths
				def file = currentFileIn(project)
				filePath = (file == null ? "" : file.path)
				psiPath = pasPathOf(currentElement)
				line = editor.caretModel.logicalPosition.line
				column = editor.caretModel.logicalPosition.column
			}

			new TrackerEvent(time, eventType, eventData, project.name, focusOwnerId, filePath, psiPath, line, column)

		} catch (Exception e) {
			log(e, NotificationType.ERROR)
			null
		}
	}

	private static String pasPathOf(PsiElement psiElement) {
		if (psiElement == null || psiElement instanceof PsiFile) ""
		else if (psiElement in PsiAnonymousClass) {
			def parentName = pasPathOf(psiElement.parent)
			def name = "[" + psiElement.baseClassType.className + "]"
			parentName.empty ? name : (parentName + "::" + name)
		} else if (psiElement instanceof PsiMethod || psiElement instanceof PsiClass) {
			def parentName = pasPathOf(psiElement.parent)
			parentName.empty ? psiElement.name : (parentName + "::" + psiElement.name)
		} else {
			pasPathOf(psiElement.parent)
		}
	}

	private static <T> T findPsiParent(PsiElement element, Closure matches) {
		if (element == null) null
		else if (matches(element)) element as T
		else findPsiParent(element.parent, matches)
	}

	private static <T> T findParentComponent(Component component, Closure matches) {
		if (component == null) null
		else if (matches(component)) component as T
		else findParentComponent(component.parent, matches)
	}
}
