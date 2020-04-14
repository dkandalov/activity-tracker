package activitytracker.tracking

import activitytracker.TrackerLog
import activitytracker.tracking.TrackerEvent.Type.Duration
import activitytracker.tracking.TrackerEvent.Type.IdeState
import activitytracker.liveplugin.*
import activitytracker.liveplugin.VcsActions.Companion.registerVcsListener
import com.intellij.concurrency.JobScheduler
import com.intellij.ide.IdeEventQueue
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.ex.AnActionListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.impl.EditorComponentImpl
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.WindowManagerEx
import com.intellij.openapi.wm.impl.IdeFrameImpl
import com.intellij.util.SystemProperties
import org.joda.time.DateTime
import java.awt.AWTEvent
import java.awt.Component
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import java.lang.System.currentTimeMillis
import java.util.concurrent.TimeUnit.MILLISECONDS
import javax.swing.JDialog

class ActivityTracker(
    private val compilationTracker: CompilationTracker,
    private val psiPathProvider: PsiPathProvider,
    private val taskNameProvider: TaskNameProvider,
    private val trackerLog: TrackerLog,
    private val parentDisposable: Disposable,
    private val logTrackerCallDuration: Boolean = false
) {
    private var trackingDisposable: Disposable? = null
    private val trackerCallDurations: MutableList<Long> = mutableListOf()

    fun startTracking(config: Config) {
        if (trackingDisposable != null) return
        trackingDisposable = newDisposable(parentDisposable)

        if (config.pollIdeState) {
            startPollingIdeState(trackerLog, trackingDisposable!!, config.pollIdeStateMs)
        }
        if (config.trackIdeActions) {
            startActionListener(trackerLog, trackingDisposable!!)
            compilationTracker.startActionListener(trackingDisposable!!) { eventType, originalEventData ->
                invokeOnEDT { trackerLog.append(captureIdeState(eventType, originalEventData)) }
            }
        }
        if (config.trackKeyboard || config.trackMouse) {
            startAWTEventListener(trackerLog, trackingDisposable!!, config.trackKeyboard, config.trackMouse, config.mouseMoveEventsThresholdMs)
        }
    }

    fun stopTracking() {
        if (trackingDisposable != null) {
            Disposer.dispose(trackingDisposable!!)
            trackingDisposable = null
        }
    }

    private fun startPollingIdeState(trackerLog: TrackerLog, trackingDisposable: Disposable, frequencyMs: Long) {
        val runnable = Runnable {
            // It has to be invokeOnEDT() method so that it's still triggered when IDE dialog window is opened (e.g. override or project settings).
            invokeOnEDT {
                trackerLog.append(captureIdeState(IdeState, ""))
                trackerLog.append(trackerCallDurationsEvent())
            }
        }

        val nextSecondStartMs = 1000 - (currentTimeMillis() % 1000)
        val future = JobScheduler.getScheduler().scheduleWithFixedDelay(runnable, nextSecondStartMs, frequencyMs, MILLISECONDS)
        trackingDisposable.whenDisposed {
            future.cancel(true)
        }
    }

    private fun trackerCallDurationsEvent(): TrackerEvent? {
        if (!logTrackerCallDuration || trackerCallDurations.size < 10) return null

        val time = DateTime.now()
        val userName = SystemProperties.getUserName()
        val durations = trackerCallDurations.joinToString(",")
        trackerCallDurations.clear()
        return TrackerEvent(time, userName, Duration, durations, "", "", "", "", -1, -1, "")
    }

    private fun startAWTEventListener(
        trackerLog: TrackerLog, parentDisposable: Disposable, trackKeyboard: Boolean,
        trackMouse: Boolean, mouseMoveEventsThresholdMs: Long
    ) {
        var lastMouseMoveTimestamp = 0L
        IdeEventQueue.getInstance().addPostprocessor(IdeEventQueue.EventDispatcher { awtEvent: AWTEvent ->
            if (trackMouse && awtEvent is MouseEvent && awtEvent.id == MouseEvent.MOUSE_CLICKED) {
                val eventData = "click:" + awtEvent.button + ":" + awtEvent.clickCount + ":" + awtEvent.modifiers
                trackerLog.append(captureIdeState(TrackerEvent.Type.MouseEvent, eventData))
            }
            if (trackMouse && awtEvent is MouseEvent && awtEvent.id == MouseEvent.MOUSE_MOVED) {
                val now = currentTimeMillis()
                if (now - lastMouseMoveTimestamp > mouseMoveEventsThresholdMs) {
                    trackerLog.append(captureIdeState(TrackerEvent.Type.MouseEvent, "move:" + awtEvent.x + ":" + awtEvent.y + ":" + awtEvent.modifiers))
                    lastMouseMoveTimestamp = now
                }
            }
            if (trackMouse && awtEvent is MouseWheelEvent && awtEvent.id == MouseEvent.MOUSE_WHEEL) {
                val now = currentTimeMillis()
                if (now - lastMouseMoveTimestamp > mouseMoveEventsThresholdMs) {
                    trackerLog.append(captureIdeState(TrackerEvent.Type.MouseEvent, "wheel:" + awtEvent.wheelRotation + ":" + awtEvent.modifiers))
                    lastMouseMoveTimestamp = now
                }
            }
            if (trackKeyboard && awtEvent is KeyEvent && awtEvent.id == KeyEvent.KEY_PRESSED) {
                trackerLog.append(captureIdeState(TrackerEvent.Type.KeyEvent, "" + (awtEvent.keyChar.toInt()) + ":" + awtEvent.keyCode + ":" + awtEvent.modifiers))
            }
            false
        }, parentDisposable)
    }

    private fun startActionListener(trackerLog: TrackerLog, parentDisposable: Disposable) {
        val actionListener = object: AnActionListener {
            override fun beforeActionPerformed(anAction: AnAction, dataContext: DataContext, event: AnActionEvent) {
                // Track action in "before" callback because otherwise timestamp of the action can be wrong
                // (e.g. commit action shows dialog and finishes only after the dialog is closed).
                // Action id can be null e.g. on 'ctrl+o' action (class com.intellij.openapi.ui.impl.DialogWrapperPeerImpl$AnCancelAction).
                val actionId = ActionManager.getInstance().getId(anAction) ?: return
                trackerLog.append(captureIdeState(TrackerEvent.Type.Action, actionId))
            }
        }
        ApplicationManager.getApplication()
            .messageBus.connect(parentDisposable)
            .subscribe(AnActionListener.TOPIC, actionListener)

        // Use custom listener for VCS because listening to normal IDE actions
        // doesn't notify about actual commits but only about opening commit dialog (see VcsActions source code for details).
        registerVcsListener(parentDisposable, object: VcsActions.Listener {
            override fun onVcsCommit() {
                invokeOnEDT { trackerLog.append(captureIdeState(TrackerEvent.Type.VcsAction, "Commit")) }
            }

            override fun onVcsUpdate() {
                invokeOnEDT { trackerLog.append(captureIdeState(TrackerEvent.Type.VcsAction, "Update")) }
            }

            override fun onVcsPush() {
                invokeOnEDT { trackerLog.append(captureIdeState(TrackerEvent.Type.VcsAction, "Push")) }
            }
        })
    }

    private fun captureIdeState(eventType: TrackerEvent.Type, originalEventData: String): TrackerEvent? {
        val start = currentTimeMillis()
        try {
            var eventData = originalEventData
            if (eventType == IdeState) {
                eventData = "Inactive"
            }
            val time = DateTime.now()
            val userName = SystemProperties.getUserName()

            val ideFocusManager = IdeFocusManager.getGlobalInstance()
            val focusOwner = ideFocusManager.focusOwner

            // this might also work: ApplicationManager.application.isActive(), ApplicationActivationListener
            val window = WindowManagerEx.getInstanceEx().mostRecentFocusedWindow
                ?: return TrackerEvent.ideNotInFocus(time, userName, eventType, eventData)

            var ideHasFocus = window.isActive
            if (!ideHasFocus) {
                @Suppress("UnstableApiUsage")
                val ideFrame = findParentComponent<IdeFrameImpl?>(focusOwner) { it is IdeFrameImpl }
                ideHasFocus = ideFrame != null && ideFrame.isActive
            }
            if (!ideHasFocus) return TrackerEvent.ideNotInFocus(time, userName, eventType, eventData)

            // use "lastFocusedFrame" to be able to obtain project in cases when some dialog is open (e.g. "override" or "project settings")
            val project = ideFocusManager.lastFocusedFrame?.project
            if (eventType == IdeState && project?.isDefault != false) {
                eventData = "NoProject"
            }
            if (project == null || project.isDefault) return TrackerEvent.ideNotInFocus(time, userName, eventType, eventData)

            if (eventType == IdeState) {
                eventData = "Active"
            }

            // Check for JDialog before EditorComponentImpl because dialog can belong to editor.
            val focusOwnerId = when {
                findParentComponent<JDialog>(focusOwner) { it is JDialog } != null                         -> "Dialog"
                findParentComponent<EditorComponentImpl>(focusOwner) { it is EditorComponentImpl } != null -> "Editor"
                else                                                                                       -> {
                    val toolWindowId = ToolWindowManager.getInstance(project).activeToolWindowId
                    toolWindowId ?: "Popup"
                }
            }

            var filePath = ""
            var psiPath = ""
            var line = -1
            var column = -1
            val editor = currentEditorIn(project)
            if (editor != null) {
                // Keep full file name because projects and libraries might have files with the same names/partial paths.
                val file = project.currentVirtualFile()
                filePath = file?.path ?: ""
                line = editor.caretModel.logicalPosition.line
                column = editor.caretModel.logicalPosition.column
                psiPath = psiPathProvider.psiPath(project, editor) ?: ""
            }

            val task = taskNameProvider.taskName(project)

            return TrackerEvent(time, userName, eventType, eventData, project.name, focusOwnerId, filePath, psiPath, line, column, task)

        } catch (e: Exception) {
            log(e, NotificationType.ERROR)
            return null
        } finally {
            if (logTrackerCallDuration) {
                trackerCallDurations.add(currentTimeMillis() - start)
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> findParentComponent(component: Component?, matches: (Component) -> Boolean): T? =
        when {
            component == null  -> null
            matches(component) -> component as T?
            else               -> findParentComponent(component.parent, matches)
        }

    private fun currentEditorIn(project: Project): Editor? =
        (FileEditorManagerEx.getInstance(project) as FileEditorManagerEx).selectedTextEditor

    data class Config(
        val pollIdeState: Boolean,
        val pollIdeStateMs: Long,
        val trackIdeActions: Boolean,
        val trackKeyboard: Boolean,
        val trackMouse: Boolean,
        val mouseMoveEventsThresholdMs: Long
    )
}
