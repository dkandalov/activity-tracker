package activitytracker

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import java.util.concurrent.ConcurrentHashMap

class GlobalVar<T>(val id: String, value: T? = null, disposable: Disposable? = null) : Disposable {
    init {
        if (value != null) {
            setGlobalVar(id, value)
        }
        if (disposable != null) {
            Disposer.register(disposable, this)
        }
    }

    fun get(): T? {
        return getGlobalVar(id)
    }

    fun set(callback: (T?) -> T) {
        changeGlobalVar(id, callback = callback)
    }

    override fun dispose() {
        removeGlobalVar<Any>(id)
    }

    companion object {
        val globalVarsKey: Key<ConcurrentHashMap<String, Any>> = Key.create("LivePlugin-GlobalVarsKey")

        private fun <T> getGlobalVar(varName: String, initialValue: T? = null): T? {
            return changeGlobalVar(varName, initialValue, {it})
        }

        private fun <T> setGlobalVar(varName: String, varValue: T): T? {
            return changeGlobalVar(varName){ varValue }
        }

        private fun <T> changeGlobalVar(varName: String, initialValue: T? = null, callback: (T?) -> T): T? {
            val application = ApplicationManager.getApplication()
            var keysByVarName = application.getUserData(Companion.globalVarsKey)
            if (keysByVarName == null) {
                keysByVarName = ConcurrentHashMap<String, Any>()
                application.putUserData(Companion.globalVarsKey, keysByVarName)
            }
            @Suppress("UNCHECKED_CAST")
            val varValue = keysByVarName[varName] as T

            val prevValue = varValue ?: initialValue
            val newValue = callback(prevValue)

            keysByVarName.put(varName, newValue as Any)

            return newValue
        }

        private fun <T> removeGlobalVar(varName: String): T? {
            val application = ApplicationManager.getApplication()
            var keysByVarName = application.getUserData(Companion.globalVarsKey)
            if (keysByVarName == null) {
                keysByVarName = ConcurrentHashMap<String, Any>()
                application.putUserData(Companion.globalVarsKey, keysByVarName)
            }
            @Suppress("UNCHECKED_CAST")
            val varValue = keysByVarName[varName] as T

            keysByVarName.remove(varName)

            return varValue
        }

    }
}