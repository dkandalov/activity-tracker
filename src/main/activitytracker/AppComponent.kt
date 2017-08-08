package activitytracker

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.ApplicationComponent
import com.intellij.openapi.diagnostic.Logger
import groovy.lang.Binding
import java.lang.reflect.Method

class AppComponent : ApplicationComponent {

    override fun initComponent() {
        try {

            val aClass = Class.forName("plugin")
            val method = findMethod("run", aClass) ?: throw IllegalStateException("Couldn't find 'plugin' class")

            val constructor = aClass.getDeclaredConstructor(Binding::class.java)
            method.invoke(constructor.newInstance(createBinding()))

        } catch (e: Exception) {
            handleException(e)
        }
    }

    override fun disposeComponent() {}

    override fun getComponentName(): String = this.javaClass.name

    companion object {
        private val pluginId = "Activity Tracker"
        private val logger = Logger.getInstance(pluginId)

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
    }
}
