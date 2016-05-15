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
import java.net.URISyntaxException

class AppComponent : ApplicationComponent {

    override fun initComponent() {
        val onClasspath = checkThatGroovyIsOnClasspath()
        if (!onClasspath) return

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

    override fun getComponentName(): String {
        return this.javaClass.name
    }

    companion object {
        private val pluginId = "Activity Tracker"
        private val PLUGIN_LIBS_PATH = PathManager.getPluginsPath() + "/activity-tracker-plugin/lib/"
        private val LOG = Logger.getInstance(pluginId)

        private fun handleException(e: Exception) {
            LOG.error("Error during initialization", e)
        }

        @Throws(URISyntaxException::class)
        private fun createBinding(): Binding {
            val binding = Binding()
            binding.setVariable("event", null)
            binding.setVariable("project", null)
            binding.setVariable("isIdeStartup", true)
            binding.setVariable("pluginPath", PathManager.getJarPathForClass(AppComponent::class.java))
            binding.setVariable("pluginDisposable", ApplicationManager.getApplication())
            return binding
        }

        private fun findMethod(methodName: String, aClass: Class<*>): Method? {
            for (method in aClass.declaredMethods) {
                if (method.name == methodName) return method
            }
            return null
        }

        private fun checkThatGroovyIsOnClasspath(): Boolean {
            if (isGroovyOnClasspath) return true

            val listener = NotificationListener { notification, event ->
                val downloaded = downloadFile("http://repo1.maven.org/maven2/org/codehaus/groovy/groovy-all/2.3.9/", "groovy-all-2.3.9.jar", PLUGIN_LIBS_PATH)
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
                    listener).notify(null)

            return false
        }

        private val isGroovyOnClasspath: Boolean
            get() = isOnClasspath("org.codehaus.groovy.runtime.DefaultGroovyMethods")
    }
}
