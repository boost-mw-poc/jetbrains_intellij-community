// SHOULD_FAIL_WITH: Package 'default' already contains property f1
// IGNORE_K2
object Test {
    val <caret>f1 = 1
}

val f1 = 1
