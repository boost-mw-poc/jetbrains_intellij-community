// PROBLEM: Variable 'foo' is assigned to itself

class Test {
    var foo = 1
}

fun Any.test() {
    if (this is Test) {
        this.foo = <caret>this.foo
    }
}
