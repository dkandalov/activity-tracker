package activitytracker.liveplugin

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import java.util.concurrent.atomic.AtomicBoolean

fun Disposable.createChild() = newDisposable(this) {}

fun Disposable.whenDisposed(callback: () -> Any) = newDisposable(this, callback = callback)

fun newDisposable(vararg parents: Disposable, callback: () -> Any = {}): Disposable {
    val isDisposed = AtomicBoolean(false)
    val disposable = Disposable {
        if (!isDisposed.get()) {
            isDisposed.set(true)
            callback()
        }
    }
    parents.forEach { parent ->
        // can't use here "Disposer.register(parent, disposable)"
        // because Disposer only allows one parent to one child registration of Disposable objects
        Disposer.register(parent, Disposable {
            Disposer.dispose(disposable)
        })
    }
    return disposable
}

inline fun <reified T> accessField(anObject: Any, possibleFieldNames: List<String>, f: (T) -> Unit): Boolean {
    for (field in anObject.javaClass.declaredFields) {
        if (possibleFieldNames.contains(field.name) && T::class.java.isAssignableFrom(field.type)) {
            field.isAccessible = true
            try {
                f.invoke(field.get(anObject) as T)
                return true
            } catch (ignored: Exception) {
            }
        }
    }
    return false
}

@Suppress("UNCHECKED_CAST")
fun <T> accessField(o: Any, fieldName: String, fieldClass: Class<*>? = null): T {
    var aClass = o.javaClass as Class<T>
    val allClasses = mutableListOf<Class<T>>()
    while (aClass != Object::class.java) {
        allClasses.add(aClass)
        aClass = aClass.superclass as Class<T>
    }
    val allFields = allClasses.map { it.declaredFields.toList() }.flatten()

    for (field in allFields) {
        if (field.name == fieldName && (fieldClass == null || fieldClass.isAssignableFrom(field.type))) {
            field.isAccessible = true
            return field.get(o) as T
        }
    }
    val className = if (fieldClass == null) "" else " (with class ${fieldClass.canonicalName})"
    throw IllegalStateException("Didn't find field '$fieldName'$className in object $o")
}

fun registerDisposable(id: String): Disposable {
    val disposable = newDisposable()
    Disposer.register(disposable, disposable, id)
    return disposable
}

fun unregisterDisposable(id: String) {
    Disposer.get(id).dispose()
}
