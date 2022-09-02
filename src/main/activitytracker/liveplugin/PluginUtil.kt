package activitytracker.liveplugin

import activitytracker.Plugin
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.NotificationType.ERROR
import com.intellij.notification.NotificationType.INFORMATION
import com.intellij.notification.NotificationType.WARNING
import com.intellij.notification.Notifications
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.progress.PerformInBackgroundOption
import com.intellij.openapi.progress.PerformInBackgroundOption.ALWAYS_BACKGROUND
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.wm.WindowManager
import com.intellij.util.text.CharArrayUtil
import org.jetbrains.annotations.NonNls
import java.io.PrintWriter
import java.io.StringWriter
import java.util.concurrent.atomic.AtomicReference
import javax.swing.KeyStroke
import javax.swing.event.HyperlinkEvent

fun <T> invokeOnEDT(callback: () -> T): T? {
    var result: T? = null
    ApplicationManager.getApplication()
        .invokeAndWait({ result = callback() }, ModalityState.any())
    return result
}

fun invokeLaterOnEDT(callback: () -> Unit) {
    ApplicationManager.getApplication().invokeLater { callback() }
}

fun showNotification(message: Any?, onLinkClick: (HyperlinkEvent) -> Unit = {}) {
    invokeLaterOnEDT {
        val messageString = asString(message)
        val title = ""
        val notificationType = INFORMATION
        val groupDisplayId = Plugin.pluginId
        val notification = Notification(groupDisplayId, title, messageString, notificationType) { _, event ->
            onLinkClick(event)
        }
        ApplicationManager.getApplication().messageBus.syncPublisher(Notifications.TOPIC).notify(notification)
    }
}

fun registerAction(
    actionId: String,
    keyStroke: String = "",
    actionGroupId: String? = null,
    displayText: String = actionId,
    disposable: Disposable? = null,
    callback: (AnActionEvent) -> Unit
): AnAction {
    return registerAction(actionId, keyStroke, actionGroupId, displayText, disposable, object : AnAction() {
        override fun actionPerformed(event: AnActionEvent) {
            callback.invoke(event)
        }
    })
}

fun registerAction(
    actionId: String,
    keyStroke: String = "",
    actionGroupId: String? = null,
    displayText: String = actionId,
    disposable: Disposable? = null,
    action: AnAction
): AnAction {

    val actionManager = ActionManager.getInstance()
    val actionGroup = findActionGroup(actionGroupId)

    val alreadyRegistered = (actionManager.getAction(actionId) != null)
    if (alreadyRegistered) {
        actionGroup?.remove(actionManager.getAction(actionId))
        actionManager.unregisterAction(actionId)
    }

    assignKeyStroke(actionId, keyStroke)
    actionManager.registerAction(actionId, action)
    actionGroup?.add(action)
    action.templatePresentation.setText(displayText, true)

    if (disposable != null) {
        newDisposable(disposable) { unregisterAction(actionId) }
    }

    return action
}

fun unregisterAction(actionId: String, actionGroupId: String? = null) {
    val actionManager = ActionManager.getInstance()
    val actionGroup = findActionGroup(actionGroupId)

    val alreadyRegistered = (actionManager.getAction(actionId) != null)
    if (alreadyRegistered) {
        actionGroup?.remove(actionManager.getAction(actionId))
        actionManager.unregisterAction(actionId)
    }
}

private fun findActionGroup(actionGroupId: String?): DefaultActionGroup? {
    actionGroupId ?: return null
    val action = ActionManager.getInstance().getAction(actionGroupId)
    return action as? DefaultActionGroup
}

private fun assignKeyStroke(actionId: String, keyStroke: String, macKeyStroke: String = keyStroke) {
    val keymap = KeymapManager.getInstance().activeKeymap
    if (!SystemInfo.isMac) {
        val shortcut = asKeyboardShortcut(keyStroke) ?: return
        keymap.removeAllActionShortcuts(actionId)
        keymap.addShortcut(actionId, shortcut)
    } else {
        val shortcut = asKeyboardShortcut(macKeyStroke) ?: return
        keymap.removeAllActionShortcuts(actionId)
        keymap.addShortcut(actionId, shortcut)
    }
}

private fun asKeyboardShortcut(keyStroke: String): KeyboardShortcut? {
    if (keyStroke.trim().isEmpty()) return null

    val firstKeystroke: KeyStroke?
    var secondKeystroke: KeyStroke? = null
    if (keyStroke.contains(",")) {
        firstKeystroke = KeyStroke.getKeyStroke(keyStroke.substring(0, keyStroke.indexOf(",")).trim())
        secondKeystroke = KeyStroke.getKeyStroke(keyStroke.substring((keyStroke.indexOf(",") + 1)).trim())
    } else {
        firstKeystroke = KeyStroke.getKeyStroke(keyStroke)
    }
    if (firstKeystroke == null) throw IllegalStateException("Invalid keystroke '$keyStroke'")
    return KeyboardShortcut(firstKeystroke, secondKeystroke)
}


fun <T> runInBackground(
    taskDescription: String = "A task",
    canBeCancelledByUser: Boolean = true,
    backgroundOption: PerformInBackgroundOption = ALWAYS_BACKGROUND,
    task: (ProgressIndicator) -> T,
    whenCancelled: () -> Unit = {},
    whenDone: (T) -> Unit = {}
) {
    invokeOnEDT {
        val result = AtomicReference<T>(null)
        object : Task.Backgroundable(null, taskDescription, canBeCancelledByUser, backgroundOption) {
            override fun run(indicator: ProgressIndicator) {
                result.set(task.invoke(indicator))
            }

            override fun onSuccess() {
                whenDone.invoke(result.get())
            }

            override fun onCancel() {
                whenCancelled.invoke()
            }
        }.queue()
    }
}

fun openInEditor(filePath: String, project: Project) {
    openUrlInEditor("file://$filePath", project)
}

fun openUrlInEditor(fileUrl: String, project: Project): VirtualFile? {
    // note that it has to be refreshAndFindFileByUrl (not just findFileByUrl) otherwise VirtualFile might be null
    val virtualFile = VirtualFileManager.getInstance().refreshAndFindFileByUrl(fileUrl) ?: return null
    FileEditorManager.getInstance(project).openFile(virtualFile, true, true)
    return virtualFile
}

fun registerProjectListener(disposable: Disposable, onEachProject: (Project) -> Unit) {
    registerProjectListener(disposable, object : ProjectManagerListener {
        override fun projectOpened(project: Project) {
            onEachProject(project)
        }
    })
    ProjectManager.getInstance().openProjects.forEach { project ->
        onEachProject(project)
    }
}

fun registerProjectListener(disposable: Disposable, listener: ProjectManagerListener) {
    val connection = ApplicationManager.getApplication().messageBus.connect(disposable)
    connection.subscribe(ProjectManager.TOPIC, listener)
}

fun Project.currentVirtualFile(): VirtualFile? =
    (FileEditorManagerEx.getInstance(this) as FileEditorManagerEx).currentFile

val logger = Logger.getInstance("LivePlugin")

fun log(message: Any?, notificationType: NotificationType = INFORMATION) {
    val s = (message as? Throwable)?.toString() ?: asString(message)
    when (notificationType) {
        INFORMATION -> logger.info(s)
        WARNING     -> logger.warn(s)
        ERROR       -> logger.error(s)
    }
}

fun asString(message: Any?): String = when {
    message?.javaClass?.isArray == true -> (message as Array<*>).contentToString()
    message is Throwable                -> unscrambleThrowable(message)
    else                                -> message.toString()
}

fun unscrambleThrowable(throwable: Throwable): String {
    val writer = StringWriter()
    throwable.printStackTrace(PrintWriter(writer))
    return Unscramble.normalizeText(writer.buffer.toString())
}

private object Unscramble {
    fun normalizeText(@NonNls text: String): String {
        val lines = text
            .replace("(\\S[ \\t\\x0B\\f\\r]+)(at\\s+)".toRegex(), "$1\n$2")
            .split("\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

        var first = true
        var inAuxInfo = false
        val builder = StringBuilder(text.length)
        for (line in lines) {
            if (!inAuxInfo && (line.startsWith("JNI global references") || line.trim { it <= ' ' } == "Heap")) {
                builder.append("\n")
                inAuxInfo = true
            }
            if (inAuxInfo) {
                builder.append(trimSuffix(line)).append("\n")
                continue
            }
            if (!first && mustHaveNewLineBefore(line)) {
                builder.append("\n")
                if (line.startsWith("\"")) builder.append("\n") // Additional line break for thread names
            }
            first = false
            val i = builder.lastIndexOf("\n")
            val lastLine = if (i == -1) builder else builder.subSequence(i + 1, builder.length)
            if (lastLine.toString().matches("\\s*at".toRegex()) && !line.matches("\\s+.*".toRegex())) builder.append(" ") // separate 'at' from file name
            builder.append(trimSuffix(line))
        }
        return builder.toString()
    }

    private fun mustHaveNewLineBefore(line: String): Boolean {
        var s = line
        val nonWs = CharArrayUtil.shiftForward(s, 0, " \t")
        if (nonWs < s.length) {
            s = s.substring(nonWs)
        }
        if (s.startsWith("at")) return true        // Start of the new stack frame entry
        if (s.startsWith("Caused")) return true    // Caused by message
        if (s.startsWith("- locked")) return true  // "Locked a monitor" logging
        if (s.startsWith("- waiting")) return true // "Waiting for monitor" logging
        if (s.startsWith("- parking to wait")) return true
        if (s.startsWith("java.lang.Thread.State")) return true
        return s.startsWith("\"")        // Start of the new thread (thread name)

    }

    private fun trimSuffix(line: String): String {
        var len = line.length
        while (0 < len && line[len - 1] <= ' ') len--
        return if (len < line.length) line.substring(0, len) else line
    }
}

class MapDataContext(private val map: MutableMap<Any, Any?> = HashMap()) : DataContext {
    override fun getData(dataId: String): Any? {
        return map[dataId]
    }

    fun put(key: String, value: Any): MapDataContext {
        map[key] = value
        return this
    }
}


fun updateWidget(widgetId: String) {
    WindowManager.getInstance().allProjectFrames.forEach {
        it.statusBar?.updateWidget(widgetId)
    }
}
