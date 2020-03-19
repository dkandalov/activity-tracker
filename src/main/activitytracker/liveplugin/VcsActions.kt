package activitytracker.liveplugin

import com.intellij.notification.Notification
import com.intellij.notification.Notifications
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.VcsKey
import com.intellij.openapi.vcs.checkin.CheckinHandler
import com.intellij.openapi.vcs.checkin.VcsCheckinHandlerFactory
import com.intellij.openapi.vcs.impl.CheckinHandlersManager
import com.intellij.openapi.vcs.impl.CheckinHandlersManagerImpl
import com.intellij.openapi.vcs.update.UpdatedFilesListener
import com.intellij.util.containers.MultiMap
import com.intellij.util.messages.MessageBusConnection

class VcsActions(private val project: Project, private val listener: Listener) {
    private val busConnection: MessageBusConnection = project.messageBus.connect()

    private val updatedListener: UpdatedFilesListener = UpdatedFilesListener { listener.onVcsUpdate() }

    // see git4idea.push.GitPushResultNotification#create
    // see org.zmlx.hg4idea.push.HgPusher#push
    private val pushListener: Notifications = object : Notifications {
        override fun notify(notification: Notification) {
            if (!isVcsNotification(notification)) return

            if (matchTitleOf(notification, "Push successful")) {
                listener.onVcsPush()
            } else if (matchTitleOf(notification, "Push failed", "Push partially failed", "Push rejected", "Push partially rejected")) {
                listener.onVcsPushFailed()
            }
        }
    }

    fun start(): VcsActions {
        // using bus to listen to vcs updates because normal listener calls it twice
        // (see also https://gist.github.com/dkandalov/8840509)
        busConnection.subscribe(UpdatedFilesListener.UPDATED_FILES, updatedListener)
        busConnection.subscribe(Notifications.TOPIC, pushListener)
        checkinHandlers { vcsFactories ->
            for (key in vcsFactories.keySet()) {
                vcsFactories.putValue(key, DelegatingCheckinHandlerFactory(project, key))
            }
        }
        return this
    }

    fun stop(): VcsActions {
        busConnection.disconnect()
        checkinHandlers { vcsFactories ->
            vcsFactories.entrySet().forEach { entry ->
                entry.value.removeIf { it is DelegatingCheckinHandlerFactory && it.project == project }
            }
        }
        return this
    }

    private fun checkinHandlers(f: (MultiMap<VcsKey, VcsCheckinHandlerFactory>) -> Unit) {
        val checkinHandlersManager = CheckinHandlersManager.getInstance() as CheckinHandlersManagerImpl
        accessField(checkinHandlersManager, listOf("a", "b", "myVcsMap", "vcsFactories")) { multiMap: MultiMap<VcsKey, VcsCheckinHandlerFactory> ->
            f(multiMap)
        }
    }

    private fun isVcsNotification(notification: Notification) =
        notification.groupId == "Vcs Messages" ||
        notification.groupId == "Vcs Important Messages" ||
        notification.groupId == "Vcs Minor Notifications" ||
        notification.groupId == "Vcs Silent Notifications"

    private fun matchTitleOf(notification: Notification, vararg expectedTitles: String): Boolean {
        return expectedTitles.any { notification.title.startsWith(it) }
    }

    /**
     * Listener callbacks can be called from any thread.
     */
    interface Listener {
        fun onVcsCommit() {}
        fun onVcsCommitFailed() {}
        fun onVcsUpdate() {}
        fun onVcsPush() {}
        fun onVcsPushFailed() {}
    }

    private inner class DelegatingCheckinHandlerFactory(val project: Project, key: VcsKey): VcsCheckinHandlerFactory(key) {
        override fun createVcsHandler(panel: CheckinProjectPanel): CheckinHandler {
            return object : CheckinHandler() {
                override fun checkinSuccessful() {
                    if (panel.project == project) listener.onVcsCommit()
                }

                override fun checkinFailed(exception: List<VcsException>) {
                    if (panel.project == project) listener.onVcsCommitFailed()
                }
            }
        }
    }

    companion object {
        fun registerVcsListener(disposable: Disposable, listener: Listener) {
            registerProjectListener(disposable) { project ->
                registerVcsListener(newDisposable(project, disposable), project, listener)
            }
        }

        fun registerVcsListener(disposable: Disposable, project: Project, listener: Listener) {
            val vcsActions = VcsActions(project, listener)
            newDisposable(project, disposable) {
                vcsActions.stop()
            }
            vcsActions.start()
        }
    }
}