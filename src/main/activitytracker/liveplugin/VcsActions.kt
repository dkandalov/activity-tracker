package activitytracker.liveplugin

import com.intellij.notification.Notification
import com.intellij.notification.Notifications
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.CheckinProjectPanel
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.CommitContext
import com.intellij.openapi.vcs.checkin.CheckinHandler
import com.intellij.openapi.vcs.checkin.CheckinHandlerFactory
import com.intellij.openapi.vcs.impl.CheckinHandlersManager
import com.intellij.openapi.vcs.impl.CheckinHandlersManagerImpl
import com.intellij.openapi.vcs.update.UpdatedFilesListener
import com.intellij.util.messages.MessageBusConnection
import java.util.*

class VcsActions(project: Project, listener: Listener) {
    private val busConnection: MessageBusConnection = project.messageBus.connect()

    private val updatedListener: UpdatedFilesListener = UpdatedFilesListener { listener.onVcsUpdate() }

    private val checkinListener: CheckinHandlerFactory = object : CheckinHandlerFactory() {
        override fun createHandler(panel: CheckinProjectPanel, commitContext: CommitContext): CheckinHandler {
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

    fun registerVcsListener(id: String, project: Project, listener: Listener) {
        registerVcsListener(registerDisposable(id), project, listener)
    }

    fun unregisterVcsListener(id: String) {
        unregisterDisposable(id)
    }

    fun start(): VcsActions {
        // using bus to listen to vcs updates because normal listener calls it twice
        // (see also https://gist.github.com/dkandalov/8840509)
        busConnection.subscribe(UpdatedFilesListener.UPDATED_FILES, updatedListener)
        busConnection.subscribe(Notifications.TOPIC, pushListener)
        checkinHandlers().add(0, checkinListener)
        return this
    }

    fun stop(): VcsActions {
        busConnection.disconnect()
        checkinHandlers().remove(checkinListener)
        return this
    }

    private fun checkinHandlers(): ArrayList<CheckinHandlerFactory> {
        val checkinHandlersManager = CheckinHandlersManager.getInstance() as CheckinHandlersManagerImpl
        return accessField(checkinHandlersManager, listOf("myRegisteredBeforeCheckinHandlers", "a", "b"), java.util.List::class.java)
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