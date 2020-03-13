package activitytracker

import activitytracker.TrackerEvent.Type.Duration
import activitytracker.TrackerEvent.Type.IdeState
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
import com.intellij.openapi.compiler.CompilationStatusListener
import com.intellij.openapi.compiler.CompileContext
import com.intellij.openapi.compiler.CompilerTopics
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.impl.EditorComponentImpl
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.WindowManagerEx
import com.intellij.openapi.wm.impl.IdeFrameImpl
import com.intellij.psi.*
import com.intellij.tasks.TaskManager
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
    private val trackerLog: TrackerLog,
    private val parentDisposable: Disposable,
    private val logTrackerCallDuration: Boolean = false
) {
    private var trackingDisposable: Disposable? = null
    private val trackerCallDurations: MutableList<Long> = mutableListOf()
    private var hasPsiClasses: Boolean? = null
    private var hasTaskManager: Boolean? = null


    fun startTracking(config: Config) {
        if (trackingDisposable != null) return
        trackingDisposable = newDisposable(parentDisposable)

        if (config.pollIdeState) {
            startPollingIdeState(trackerLog, trackingDisposable!!, config.pollIdeStateMs)
        }
        if (config.trackIdeActions) {
            startActionListener(trackerLog, trackingDisposable!!)
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

    private fun startAWTEventListener(trackerLog: TrackerLog, parentDisposable: Disposable, trackKeyboard: Boolean,
                                      trackMouse: Boolean, mouseMoveEventsThresholdMs: Long) {
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
        registerVcsListener(parentDisposable, object : VcsActions.Listener {
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

        if (haveCompilation()) {
            registerCompilationListener(parentDisposable, object : CompilationStatusListener {
                override fun compilationFinished(aborted: Boolean, errors: Int, warnings: Int, compileContext: CompileContext) {
                    invokeOnEDT { trackerLog.append(captureIdeState(TrackerEvent.Type.CompilationFinished, errors.toString())) }
                }
            })
        }
    }

    private fun registerCompilationListener(disposable: Disposable, listener: CompilationStatusListener) {
        registerProjectListener(disposable) { project ->
            registerCompilationListener(disposable, project, listener)
        }
    }

    private fun registerCompilationListener(disposable: Disposable, project: Project, listener: CompilationStatusListener) {
        project.messageBus
            .connect(newDisposable(disposable, project))
            .subscribe(CompilerTopics.COMPILATION_STATUS, listener)
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
                val file = currentFileIn(project)
                filePath = file?.path ?: ""
                line = editor.caretModel.logicalPosition.line
                column = editor.caretModel.logicalPosition.column

                // Non-java IDEs might not have PsiMethod class.
                if (hasPsiClasses(project)) {
                    val elementAtOffset = currentPsiFileIn(project)?.findElementAt(editor.caretModel.offset)
                    val psiMethod = findPsiParent<PsiMethod>(elementAtOffset) { it is PsiMethod }
                    val psiFile = findPsiParent<PsiFile>(elementAtOffset) { it is PsiFile }
                    val currentElement = psiMethod ?: psiFile
                    psiPath = psiPathOf(currentElement)
                }
            }

            val task = if (hasTaskManager(project)) {
                TaskManager.getManager(project)?.activeTask?.presentableName
                    ?: ChangeListManager.getInstance(project).defaultChangeList.name
            } else {
                ChangeListManager.getInstance(project).defaultChangeList.name
            }

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

    private fun hasTaskManager(project: Project): Boolean {
        if (hasTaskManager == null && !DumbService.getInstance(project).isDumb) {
            hasTaskManager = isOnClasspath("com.intellij.tasks.TaskManager")
        }
        return hasTaskManager ?: false
    }

    private fun hasPsiClasses(project: Project): Boolean {
        if (hasPsiClasses == null && !DumbService.getInstance(project).isDumb) {
            hasPsiClasses = isOnClasspath("com.intellij.psi.PsiMethod")
        }
        return hasPsiClasses ?: false
    }

    private fun haveCompilation() = isOnClasspath("com.intellij.openapi.compiler.CompilationStatusListener")

    private fun isOnClasspath(className: String) =
        ActivityTracker::class.java.classLoader.getResource(className.replace(".", "/") + ".class") != null

    private fun psiPathOf(psiElement: PsiElement?): String =
        when (psiElement) {
            null, is PsiFile          -> ""
            is PsiAnonymousClass      -> {
                val parentName = psiPathOf(psiElement.parent)
                val name = "[${psiElement.baseClassType.className}]"
                if (parentName.isEmpty()) name else "$parentName::$name"
            }
            is PsiMethod, is PsiClass -> {
                val parentName = psiPathOf(psiElement.parent)
                val name = (psiElement as PsiNamedElement).name ?: ""
                if (parentName.isEmpty()) name else "$parentName::$name"
            }
            else                      -> psiPathOf(psiElement.parent)
        }

    @Suppress("UNCHECKED_CAST")
    private fun <T> findPsiParent(element: PsiElement?, matches: (PsiElement) -> Boolean): T? = when {
        element == null  -> null
        matches(element) -> element as T?
        else             -> findPsiParent(element.parent, matches)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> findParentComponent(component: Component?, matches: (Component) -> Boolean): T? = when {
        component == null  -> null
        matches(component) -> component as T?
        else               -> findParentComponent(component.parent, matches)
    }

    private fun currentEditorIn(project: Project): Editor? =
        (FileEditorManagerEx.getInstance(project) as FileEditorManagerEx).selectedTextEditor

    private fun currentFileIn(project: Project): VirtualFile? =
        (FileEditorManagerEx.getInstance(project) as FileEditorManagerEx).currentFile

    private fun currentPsiFileIn(project: Project): PsiFile? {
        return psiFile(currentFileIn(project), project)
    }

    private fun psiFile(file: VirtualFile?, project: Project): PsiFile? {
        file ?: return null
        return PsiManager.getInstance(project).findFile(file)
    }

    data class Config(
        val pollIdeState: Boolean,
        val pollIdeStateMs: Long,
        val trackIdeActions: Boolean,
        val trackKeyboard: Boolean,
        val trackMouse: Boolean,
        val mouseMoveEventsThresholdMs: Long
    )
}
