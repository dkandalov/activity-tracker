package activitytracker.liveplugin

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
        val keysByVarName = initKeysByVarName(globalVarsKey)

        private fun <T> getGlobalVar(varName: String, initialValue: T? = null): T? {
            return changeGlobalVar(varName, initialValue, { it })
        }

        private fun <T> setGlobalVar(varName: String, varValue: T?): T? {
            // explicit null parameter to make compiler not crash
            return changeGlobalVar(varName, null as T?) { it -> varValue }
        }

        private fun <T> changeGlobalVar(varName: String, initialValue: T? = null, callback: (T?) -> T?): T? {
            @Suppress("UNCHECKED_CAST")
            val varValue = keysByVarName[varName] as T

            val prevValue = varValue ?: initialValue
            val newValue = callback(prevValue)

            if (newValue == null) {
                keysByVarName.remove(varName)
            } else {
                keysByVarName.put(varName, newValue as Any)
            }

            return newValue
        }

        private fun <T> removeGlobalVar(varName: String): T? {
            @Suppress("UNCHECKED_CAST")
            val varValue = keysByVarName[varName] as T

            keysByVarName.remove(varName)

            return varValue
        }

        private fun initKeysByVarName(globalVarsKey: Key<ConcurrentHashMap<String, Any>>): ConcurrentHashMap<String, Any> {
            val application = ApplicationManager.getApplication()
            var keysByVarName = application.getUserData(globalVarsKey)
            if (keysByVarName == null) {
                keysByVarName = ConcurrentHashMap<String, Any>()
                application.putUserData(globalVarsKey, keysByVarName)
            }
            return keysByVarName
        }

    }
}