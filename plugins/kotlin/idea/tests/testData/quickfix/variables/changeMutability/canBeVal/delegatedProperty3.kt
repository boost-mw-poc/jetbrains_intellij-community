// "Change to 'val'" "false"
// ACTION: Create extension functions 'Delegate.getValue', 'Delegate.setValue'
// ACTION: Create member functions 'Delegate.getValue', 'Delegate.setValue'
// ACTION: Introduce import alias
// ERROR: Type 'Delegate' has no method 'getValue(Nothing?, KProperty<*>)' and thus it cannot serve as a delegate
// ERROR: Type 'Delegate' has no method 'setValue(Nothing?, KProperty<*>, String)' and thus it cannot serve as a delegate for var (read-write property)
// K2_AFTER_ERROR: Type 'Delegate' has no method 'getValue(Nothing?, KMutableProperty0<*>)', so it cannot serve as a delegate.
// K2_AFTER_ERROR: Type 'Delegate' has no method 'setValue(Nothing?, KMutableProperty0<*>, String)', so it cannot serve as a delegate for var (read-write property).
import kotlin.reflect.KProperty

fun test() {
    var foo: String by <caret>Delegate()
}

class Delegate
