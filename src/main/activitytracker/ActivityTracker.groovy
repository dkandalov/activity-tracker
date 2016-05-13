package activitytracker

import com.intellij.concurrency.JobScheduler
import com.intellij.ide.IdeEventQueue
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.ex.AnActionListener
import com.intellij.openapi.editor.impl.EditorComponentImpl
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.WindowManagerEx
import com.intellij.openapi.wm.impl.IdeFrameImpl
import com.intellij.psi.*
import com.intellij.util.SystemProperties
import liveplugin.implementation.VcsActions
import org.joda.time.DateTime

import javax.swing.*
import java.awt.*
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent

import static java.util.concurrent.TimeUnit.MILLISECONDS
import static liveplugin.PluginUtil.*
import static liveplugin.implementation.Misc.newDisposable

class ActivityTracker {
	private final TrackerLog trackerLog
	private final Disposable parentDisposable
	private Disposable trackingDisposable


	ActivityTracker(TrackerLog trackerLog, Disposable parentDisposable) {
		this.parentDisposable = parentDisposable
		this.trackerLog = trackerLog
	}

	void startTracking(Config config) {
		if (trackingDisposable != null) return
		trackingDisposable = newDisposable([parentDisposable])

		if (config.pollIdeState) {
			startPollingIdeState(trackerLog, trackingDisposable, config.pollIdeStateMs)
		}
		if (config.trackIdeActions) {
			startActionListener(trackerLog, trackingDisposable)
		}
		if (config.trackKeyboard || config.trackMouse) {
			startAWTEventListener(trackerLog, trackingDisposable, config.trackKeyboard, config.trackMouse, config.mouseMoveEventsThresholdMs)
		}
	}

	void stopTracking() {
		if (trackingDisposable != null) {
			Disposer.dispose(trackingDisposable)
			trackingDisposable = null
		}
	}

	private static startPollingIdeState(TrackerLog trackerLog, Disposable trackingDisposable, long frequencyMs) {
		def runnable = {
			// it has to be invokeOnEDT() method so that it's triggered when IDE dialog window is opened (e.g. override or project settings),
			invokeOnEDT {
				trackerLog.append(captureIdeState("IdeState", ""))
			}
		} as Runnable
		def nextSecondStartMs = 1000 - (System.currentTimeMillis() % 1000)
		def future = JobScheduler.scheduler.scheduleWithFixedDelay(runnable, nextSecondStartMs, frequencyMs, MILLISECONDS)
		newDisposable(trackingDisposable) {
			future.cancel(true)
		}
	}

	private static startAWTEventListener(TrackerLog trackerLog, Disposable parentDisposable, boolean trackKeyboard,
	                                     boolean trackMouse, long mouseMoveEventsThresholdMs) {
		long lastMouseMoveTimestamp = 0

		IdeEventQueue.instance.addPostprocessor(new IdeEventQueue.EventDispatcher() {
			@Override boolean dispatch(AWTEvent awtEvent) {
				if (trackMouse && awtEvent instanceof MouseEvent && awtEvent.ID == MouseEvent.MOUSE_CLICKED) {
					def eventData = "click:" + awtEvent.button + ":" + awtEvent.clickCount + ":" + awtEvent.modifiers
					trackerLog.append(captureIdeState("MouseEvent", eventData))
				}
				if (trackMouse && awtEvent instanceof MouseEvent && awtEvent.ID == MouseEvent.MOUSE_MOVED) {
					long now = System.currentTimeMillis()
					if (now - lastMouseMoveTimestamp > mouseMoveEventsThresholdMs) {
						trackerLog.append(captureIdeState("MouseEvent", "move:" + awtEvent.x + ":" + awtEvent.y + ":" + awtEvent.modifiers))
						lastMouseMoveTimestamp = now
					}
				}
				if (trackMouse && awtEvent instanceof MouseWheelEvent && awtEvent.ID == MouseEvent.MOUSE_WHEEL) {
					long now = System.currentTimeMillis()
					if (now - lastMouseMoveTimestamp > mouseMoveEventsThresholdMs) {
						trackerLog.append(captureIdeState("MouseEvent", "wheel:" + awtEvent.wheelRotation + ":" + awtEvent.modifiers))
						lastMouseMoveTimestamp = now
					}
				}
				if (trackKeyboard && awtEvent instanceof KeyEvent && awtEvent.ID == KeyEvent.KEY_PRESSED) {
					trackerLog.append(captureIdeState("KeyEvent", "" + (awtEvent.keyChar as int) + ":" + (awtEvent.keyCode as int) + ":" + awtEvent.modifiers))
				}
				false
			}
		}, parentDisposable)
	}

	private static void startActionListener(TrackerLog trackerLog, Disposable parentDisposable) {
		def actionManager = ActionManager.instance
		actionManager.addAnActionListener(new AnActionListener() {
			@Override void beforeActionPerformed(AnAction anAction, DataContext dataContext, AnActionEvent anActionEvent) {
				// track action in "before" callback because otherwise timing of action can be wrong
				// (e.g. commit action shows dialog and finishes only after the dialog is closed)
				def actionId = actionManager.getId(anAction)
				if (actionId == null) return
				// can be null e.g. on 'ctrl+o' action (class com.intellij.openapi.ui.impl.DialogWrapperPeerImpl$AnCancelAction)
				trackerLog.append(captureIdeState("Action", actionId))
			}
			@Override void afterActionPerformed(AnAction anAction, DataContext dataContext, AnActionEvent anActionEvent) {}
			@Override void beforeEditorTyping(char c, DataContext dataContext) {}
		}, parentDisposable)

		// use custom listener for VCS because listening to normal IDE actions
		// doesn't notify about actual commits but only about opening commit dialog
		VcsActions.registerVcsListener(parentDisposable, new VcsActions.Listener() {
			@Override void onVcsCommit() {
				invokeOnEDT {
					trackerLog.append(captureIdeState("VcsAction", "Commit"))
				}
			}
			@Override void onVcsUpdate() {
				invokeOnEDT {
					trackerLog.append(captureIdeState("VcsAction", "Update"))
				}
			}
			@Override void onVcsPush() {
				invokeOnEDT {
					trackerLog.append(captureIdeState("VcsAction", "Push"))
				}
			}
		})
	}

	private static TrackerEvent captureIdeState(String eventType, String eventData) {
		try {
			if (eventType == "IdeState") {
				eventData = "Inactive"
			}
			def time = DateTime.now()
			def userName = SystemProperties.userName

			def ideFocusManager = IdeFocusManager.globalInstance
			def focusOwner = ideFocusManager.focusOwner

			// this might also work: ApplicationManager.application.isActive()
			def window = WindowManagerEx.instanceEx.mostRecentFocusedWindow
			if (window == null) return TrackerEvent.ideNotInFocus(time, userName, eventType, eventData)

			def ideHasFocus = window.active
			if (!ideHasFocus) {
				IdeFrameImpl ideFrame = findParentComponent(focusOwner) { it instanceof IdeFrameImpl }
				ideHasFocus = ideFrame != null && ideFrame.active
			}
			if (!ideHasFocus) return TrackerEvent.ideNotInFocus(time, userName, eventType, eventData)

			// use "lastFocusedFrame" to be able to obtain project in cases when some dialog is open (e.g. "override" or "project settings")
			def project = ideFocusManager.lastFocusedFrame?.project
			if (eventType == "IdeState" && project?.default) {
				eventData = "NoProject"
			}
			if (project == null || project.default) return TrackerEvent.ideNotInFocus(time, userName, eventType, eventData)

			if (eventType == "IdeState") {
				eventData = "Active"
			}

			def focusOwnerId
			// check for JDialog before EditorComponentImpl because dialog can belong to editor
			if (findParentComponent(focusOwner) { it instanceof JDialog } != null) {
				focusOwnerId = "Dialog"
			} else if (findParentComponent(focusOwner) { it instanceof EditorComponentImpl } != null) {
				focusOwnerId = "Editor"
			} else {
				focusOwnerId = ToolWindowManager.getInstance(project)?.activeToolWindowId
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
				// keep full file name because projects and libraries might have files with the same names/partial paths
				def file = currentFileIn(project)
				filePath = (file == null ? "" : file.path)
				line = editor.caretModel.logicalPosition.line
				column = editor.caretModel.logicalPosition.column

				// non-java based IDEs might not have PsiMethod class
				if (!DumbService.getInstance(project).dumb && isOnClasspath("com.intellij.psi.PsiMethod")) {
					def elementAtOffset = currentPsiFileIn(project)?.findElementAt(editor.caretModel.offset)
					PsiMethod psiMethod = findPsiParent(elementAtOffset, { it instanceof PsiMethod })
					PsiFile psiFile = findPsiParent(elementAtOffset, { it instanceof PsiFile })
					def currentElement = (psiMethod == null ? psiFile : psiMethod)
					psiPath = psiPathOf(currentElement)
				}
			}

			new TrackerEvent(time, userName, eventType, eventData, project.name, focusOwnerId, filePath, psiPath, line, column)

		} catch (Exception e) {
			log(e, NotificationType.ERROR)
			null
		}
	}

	private static String psiPathOf(PsiElement psiElement) {
		if (psiElement == null || psiElement instanceof PsiFile) ""
		else if (psiElement in PsiAnonymousClass) {
			def parentName = psiPathOf(psiElement.parent)
			def name = "[" + psiElement.baseClassType.className + "]"
			parentName.empty ? name : (parentName + "::" + name)
		} else if (psiElement instanceof PsiMethod || psiElement instanceof PsiClass) {
			def parentName = psiPathOf(psiElement.parent)
			parentName.empty ? psiElement.name : (parentName + "::" + psiElement.name)
		} else {
			psiPathOf(psiElement.parent)
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

	private static boolean isOnClasspath(String className) {
		ActivityTracker.class.classLoader.getResource(className.replace(".", "/") + ".class") != null
	}

	static final class Config {
		boolean pollIdeState
		long pollIdeStateMs
		boolean trackIdeActions
		boolean trackKeyboard
		boolean trackMouse
		long mouseMoveEventsThresholdMs

		Config(boolean pollIdeState, long pollIdeStateMs, boolean trackIdeActions, boolean trackKeyboard, boolean trackMouse, long mouseMoveEventsThresholdMs) {
			this.pollIdeState = pollIdeState
			this.pollIdeStateMs = pollIdeStateMs
			this.trackIdeActions = trackIdeActions
			this.trackKeyboard = trackKeyboard
			this.trackMouse = trackMouse
			this.mouseMoveEventsThresholdMs = mouseMoveEventsThresholdMs
		}

		boolean equals(o) {
			if (this.is(o)) return true
			if (getClass() != o.class) return false

			Config config = (Config) o

			if (mouseMoveEventsThresholdMs != config.mouseMoveEventsThresholdMs) return false
			if (pollIdeState != config.pollIdeState) return false
			if (pollIdeStateMs != config.pollIdeStateMs) return false
			if (trackIdeActions != config.trackIdeActions) return false
			if (trackKeyboard != config.trackKeyboard) return false
			if (trackMouse != config.trackMouse) return false

			return true
		}

		int hashCode() {
			int result
			result = (pollIdeState ? 1 : 0)
			result = 31 * result + (int) (pollIdeStateMs ^ (pollIdeStateMs >>> 32))
			result = 31 * result + (trackIdeActions ? 1 : 0)
			result = 31 * result + (trackKeyboard ? 1 : 0)
			result = 31 * result + (trackMouse ? 1 : 0)
			result = 31 * result + (int) (mouseMoveEventsThresholdMs ^ (mouseMoveEventsThresholdMs >>> 32))
			return result
		}
	}
}
