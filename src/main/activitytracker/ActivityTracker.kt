package activitytracker

import activitytracker.liveplugin.invokeOnEDT
import activitytracker.liveplugin.newDisposable
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
import groovy.lang.MetaClass
import liveplugin.PluginUtil
import liveplugin.PluginUtil.*
import liveplugin.implementation.Misc.newDisposable
import liveplugin.implementation.VcsActions
import org.codehaus.groovy.runtime.InvokerHelper
import org.joda.time.DateTime
import java.awt.AWTEvent
import java.awt.Component
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import java.util.concurrent.TimeUnit.MILLISECONDS
import javax.swing.JDialog

class ActivityTracker(val trackerLog: TrackerLog, val parentDisposable: Disposable) {
    private var trackingDisposable: Disposable? = null
    private val captureStateDurations: MutableList<Long> = mutableListOf()

    fun startTracking(config: Config) {
        if (trackingDisposable != null) return
        trackingDisposable = newDisposable(parentDisposable)

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

    fun stopTracking() {
        if (trackingDisposable != null) {
            Disposer.dispose(trackingDisposable!!)
            trackingDisposable = null
        }
    }

    private fun startPollingIdeState(trackerLog: TrackerLog, trackingDisposable: Disposable?, frequencyMs: Long) {
        val runnable = Runnable() {
            // it has to be invokeOnEDT() method so that it's triggered when IDE dialog window is opened (e.g. override or project settings),
            invokeOnEDT {
                trackerLog.append(captureIdeState("IdeState", ""))
                if (captureStateDurations.size > 10) {
                    val time = DateTime.now()
                    val userName = SystemProperties.getUserName()
                    val durations = captureStateDurations.joinToString(",")
                    val trackerEvent = TrackerEvent(time, userName, "Duration", durations, "", "", "", "", -1, -1) // TODO remove
                    trackerLog.append(trackerEvent)
                    captureStateDurations.clear()
                }
            }
        }

        val nextSecondStartMs = 1000 - (System.currentTimeMillis() % 1000)
        val future = JobScheduler.getScheduler().scheduleWithFixedDelay(runnable, nextSecondStartMs, frequencyMs, MILLISECONDS)
        newDisposable(trackingDisposable!!) {
            future.cancel(true)
        }
    }

    private fun startAWTEventListener(trackerLog: TrackerLog, parentDisposable: Disposable?, trackKeyboard: Boolean,
                                      trackMouse: Boolean, mouseMoveEventsThresholdMs: Long) {
        var lastMouseMoveTimestamp = 0L
        IdeEventQueue.getInstance().addPostprocessor(IdeEventQueue.EventDispatcher { awtEvent: AWTEvent ->
            if (trackMouse && awtEvent is MouseEvent && awtEvent.id == MouseEvent.MOUSE_CLICKED) {
                val eventData = "click:" + awtEvent.button + ":" + awtEvent.clickCount + ":" + awtEvent.modifiers
                trackerLog.append(captureIdeState("MouseEvent", eventData))
            }
            if (trackMouse && awtEvent is MouseEvent && awtEvent.id == MouseEvent.MOUSE_MOVED) {
                val now = System.currentTimeMillis()
                if (now - lastMouseMoveTimestamp > mouseMoveEventsThresholdMs) {
                    trackerLog.append(captureIdeState("MouseEvent", "move:" + awtEvent.x + ":" + awtEvent.y + ":" + awtEvent.modifiers))
                    lastMouseMoveTimestamp = now
                }
            }
            if (trackMouse && awtEvent is MouseWheelEvent && awtEvent.id == MouseEvent.MOUSE_WHEEL) {
                val now = System.currentTimeMillis()
                if (now - lastMouseMoveTimestamp > mouseMoveEventsThresholdMs) {
                    trackerLog.append(captureIdeState("MouseEvent", "wheel:" + awtEvent.wheelRotation + ":" + awtEvent.modifiers))
                    lastMouseMoveTimestamp = now
                }
            }
            if (trackKeyboard && awtEvent is KeyEvent && awtEvent.id == KeyEvent.KEY_PRESSED) {
                trackerLog.append(captureIdeState("KeyEvent", "" + (awtEvent.keyChar.toInt()) + ":" + (awtEvent.keyCode.toInt()) + ":" + awtEvent.modifiers))
            }
            false
        }, parentDisposable)
    }

    private fun startActionListener(trackerLog: TrackerLog, parentDisposable: Disposable?) {
        val actionManager = ActionManager.getInstance()
        actionManager.addAnActionListener(object : AnActionListener {
            override fun beforeActionPerformed(anAction: AnAction?, dataContext: DataContext?, event: AnActionEvent?) {
                if (anAction == null) return
                // track action in "before" callback because otherwise timing of action can be wrong
                // (e.g. commit action shows dialog and finishes only after the dialog is closed)
                val actionId = actionManager.getId(anAction) ?: return
                // can be null e.g. on 'ctrl+o' action (class com.intellij.openapi.ui.impl.DialogWrapperPeerImpl$AnCancelAction)
                trackerLog.append(captureIdeState("Action", actionId))
            }
            override fun beforeEditorTyping(c: Char, dataContext: DataContext?) { }
            override fun afterActionPerformed(action: AnAction?, dataContext: DataContext?, event: AnActionEvent?) { }
        }, parentDisposable)

        // use custom listener for VCS because listening to normal IDE actions
        // doesn't notify about actual commits but only about opening commit dialog
        VcsActions.registerVcsListener(parentDisposable, object : VcsActions.Listener() {
            override fun onVcsCommit() {
                invokeOnEDT { trackerLog.append(captureIdeState("VcsAction", "Commit")) }
            }
            override fun onVcsUpdate() {
                invokeOnEDT { trackerLog.append(captureIdeState("VcsAction", "Update")) }
            }
            override fun onVcsPush() {
                invokeOnEDT { trackerLog.append(captureIdeState("VcsAction", "Push")) }
            }

            // TODO not sure why these methods have to be implemented (don't need to do it in Scala, Java)
            // The metaClass logic below is based on GroovyObjectSupport
            private var metaClass: MetaClass? = null
            override fun getMetaClass(): MetaClass? {
                if (metaClass == null) {
                    metaClass = InvokerHelper.getMetaClass(javaClass)
                }
                return metaClass
            }
            override fun setMetaClass(metaClass: MetaClass?) {
                this.metaClass = metaClass
            }
            override fun setProperty(propertyName: String?, newValue: Any?) {}
            override fun getProperty(propertyName: String?): Any? {
                return null
            }
            override fun invokeMethod(name: String?, args: Any?): Any? {
                return null
            }
        })
    }

    private fun captureIdeState(eventType: String, originalEventData: String): TrackerEvent? {
        val start = System.currentTimeMillis()
        try {
            var eventData = originalEventData
            if (eventType == "IdeState") {
                eventData = "Inactive"
            }
            val time = DateTime.now()
            val userName = SystemProperties.getUserName()

            val ideFocusManager = IdeFocusManager.getGlobalInstance()
            val focusOwner = ideFocusManager.focusOwner

            // this might also work: ApplicationManager.application.isActive()
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
            if (eventType == "IdeState" && project?.isDefault ?: true) {
                eventData = "NoProject"
            }
            if (project == null || project.isDefault) return TrackerEvent.ideNotInFocus(time, userName, eventType, eventData)

            if (eventType == "IdeState") {
                eventData = "Active"
            }

            val focusOwnerId: String
            // check for JDialog before EditorComponentImpl because dialog can belong to editor
            if (findParentComponent<JDialog>(focusOwner) { it is JDialog } != null) {
                focusOwnerId = "Dialog"
            } else if (findParentComponent<EditorComponentImpl>(focusOwner) { it is EditorComponentImpl } != null) {
                focusOwnerId = "Editor"
            } else {
                val toolWindowId = ToolWindowManager.getInstance(project)?.activeToolWindowId
                focusOwnerId = toolWindowId ?: "Popup"
            }

            var filePath = ""
            var psiPath = ""
            var line = -1
            var column = -1
            val editor = currentEditorIn(project)
            if (editor != null) {
                // keep full file name because projects and libraries might have files with the same names/partial paths
                val file = currentFileIn(project)
                filePath = file?.path ?: ""
                line = editor.caretModel.logicalPosition.line
                column = editor.caretModel.logicalPosition.column

                // non-java based IDEs might not have PsiMethod class
                // TODO refactor to do this check only once
                if (!DumbService.getInstance(project).isDumb && isOnClasspath("com.intellij.psi.PsiMethod")) {
                    val elementAtOffset = currentPsiFileIn(project)?.findElementAt(editor.caretModel.offset)
                    val psiMethod = findPsiParent<PsiMethod>(elementAtOffset, { it is PsiMethod })
                    val psiFile = findPsiParent<PsiFile>(elementAtOffset, { it is PsiFile })
                    val currentElement = psiMethod ?: psiFile
                    psiPath = psiPathOf(currentElement)
                }
            }

            return TrackerEvent(time, userName, eventType, eventData, project.name, focusOwnerId, filePath, psiPath, line, column)

        } catch (e: Exception) {
            PluginUtil.log(e, NotificationType.ERROR)
            return null
        } finally {
            captureStateDurations.add(System.currentTimeMillis() - start)
        }
    }

    private fun isOnClasspath(className: String): Boolean {
        return ActivityTracker::class.java.classLoader.getResource(className.replace(".", "/") + ".class") != null
    }

    private fun psiPathOf(psiElement: PsiElement?): String {
        if (psiElement == null || psiElement is PsiFile) return ""
        else if (psiElement is PsiAnonymousClass) {
            val parentName = psiPathOf(psiElement.parent)
            val name = "[" + psiElement.baseClassType.className + "]"
            return if (parentName.isEmpty()) name else (parentName + "::" + name)
        } else if (psiElement is PsiMethod || psiElement is PsiClass) {
            val parentName = psiPathOf(psiElement.parent)
            val name = (psiElement as PsiNamedElement).name ?: ""
            return if (parentName.isEmpty()) name else "$parentName::$name"
        } else {
            return psiPathOf(psiElement.parent)
        }
    }

    private fun <T> findPsiParent(element: PsiElement?, matches: (PsiElement) -> Boolean): T? {
        @Suppress("UNCHECKED_CAST")
        return if (element == null) null
        else if (matches(element)) element as T?
        else findPsiParent(element.parent, matches)
    }

    private fun <T> findParentComponent(component: Component?, matches: (Component) -> Boolean): T? {
        @Suppress("UNCHECKED_CAST")
        return if (component == null) null
        else if (matches(component)) component as T?
        else findParentComponent(component.parent, matches)
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
