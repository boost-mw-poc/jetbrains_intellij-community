// "Configure arguments for the feature: when guards" "true"
// LANGUAGE_VERSION: 2.1
// APPLY_QUICKFIX: false
// DISABLE_K2_ERRORS

fun test(a: Any) {
    when (a) {
        is Int if<caret> a > 5 -> {}
        else -> {}
    }
}