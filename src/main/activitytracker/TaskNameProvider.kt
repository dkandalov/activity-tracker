package activitytracker

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.tasks.TaskManager

interface TaskNameProvider {
    fun taskName(project: Project): String

    companion object {
        var instance = object: TaskNameProvider {
            override fun taskName(project: Project) = ChangeListManager.getInstance(project).defaultChangeList.name
        }
    }
}

class InitTaskNameProviderViaTaskManager {
    init {
        TaskNameProvider.instance = object: TaskNameProvider {
            override fun taskName(project: Project) =
                TaskManager.getManager(project)?.activeTask?.presentableName
                    ?: ChangeListManager.getInstance(project).defaultChangeList.name
        }
    }
}