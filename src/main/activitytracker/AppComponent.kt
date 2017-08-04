package activitytracker

import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationListener
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.ApplicationComponent
import com.intellij.openapi.diagnostic.Logger
import groovy.lang.Binding
import liveplugin.IDEUtil.*
import java.lang.reflect.Method

class AppComponent : ApplicationComponent {

    override fun initComponent() {
        if (!checkThatGroovyIsOnClasspath()) return

        try {

            val aClass = Class.forName("plugin")
            val method = findMethod("run", aClass) ?: throw IllegalStateException("Couldn't find 'plugin' class")

            val constructor = aClass.getDeclaredConstructor(Binding::class.java)
            method.invoke(constructor.newInstance(createBinding()))

        } catch (e: Exception) {
            handleException(e)
        }
    }

    override fun disposeComponent() {
    }

    override fun getComponentName(): String = this.javaClass.name

    companion object {
        private val pluginId = "Activity Tracker"
        private val pluginLibsPath = PathManager.getPluginsPath() + "/activity-tracker-plugin/lib/"
        private val logger = Logger.getInstance(pluginId)
        private val isGroovyOnClasspath = isOnClasspath("org.codehaus.groovy.runtime.DefaultGroovyMethods")

        private fun handleException(e: Exception) {
            logger.error("Error during initialization", e)
        }

        private fun createBinding() = Binding().apply {
            setVariable("event", null)
            setVariable("project", null)
            setVariable("isIdeStartup", true)
            setVariable("pluginPath", PathManager.getJarPathForClass(AppComponent::class.java))
            setVariable("pluginDisposable", ApplicationManager.getApplication())
        }

        private fun findMethod(methodName: String, aClass: Class<*>): Method? =
            aClass.declaredMethods.firstOrNull { it.name == methodName }

        private fun checkThatGroovyIsOnClasspath(): Boolean {
            if (isGroovyOnClasspath) return true

            val listener = NotificationListener { notification, event ->
                val downloaded = downloadFile("http://repo1.maven.org/maven2/org/codehaus/groovy/groovy-all/2.3.9/", "groovy-all-2.3.9.jar", pluginLibsPath)
                if (downloaded) {
                    notification.expire()
                    askIfUserWantsToRestartIde("For Groovy libraries to be loaded IDE restart is required. Restart now?")
                } else {
                    NotificationGroup.balloonGroup(pluginId).createNotification("Failed to download Groovy libraries", NotificationType.WARNING)
                }
            }
            NotificationGroup.balloonGroup(pluginId).createNotification(
                    pluginId + " plugin didn't find Groovy libraries on classpath",
                    "Without it plugin won't work. <a href=\"\">Download Groovy libraries</a> (~6Mb)",
                    NotificationType.ERROR,
                    listener
            ).notify(null)

            return false
        }
    }
}
