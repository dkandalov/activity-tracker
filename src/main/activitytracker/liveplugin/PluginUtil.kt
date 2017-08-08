package activitytracker.liveplugin

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.WindowManager

fun <T> invokeOnEDT(callback: () -> T): T? {
    var result: T? = null
    ApplicationManager.getApplication()
        .invokeAndWait({ result = callback() }, ModalityState.any())
    return result
}

fun invokeLaterOnEDT(callback: () -> Unit) {
    ApplicationManager.getApplication().invokeLater { callback() }
}

fun registerProjectListener(onEachProject: (Project) -> Unit) {
    val listener = object: ProjectManagerListener {
        override fun projectOpened(project: Project) {
            onEachProject(project)
        }
        override fun canCloseProject(project: Project?) = true
        override fun projectClosing(project: Project?) {}
        override fun projectClosed(project: Project?) {}
    }
    val connection = ApplicationManager.getApplication().messageBus.connect()
    connection.subscribe(ProjectManager.TOPIC, listener)

    ProjectManager.getInstance().openProjects.forEach {
        onEachProject(it)
    }
}

fun registerWidget(widgetId: String, project: Project, disposable: Disposable,
                   anchor: String = "before Position", presentation: StatusBarWidget.WidgetPresentation) {
    val frame = WindowManager.getInstance()
        .allProjectFrames
        .find { it.project == project }
        ?: return

    val widget = object: StatusBarWidget {
        override fun ID() = widgetId
        override fun getPresentation(type: StatusBarWidget.PlatformType) = presentation
        override fun install(statusBar: StatusBar) {}
        override fun dispose() {}
    }
    frame.statusBar.addWidget(widget, anchor, disposable)
    frame.statusBar.updateWidget(widgetId)
}
