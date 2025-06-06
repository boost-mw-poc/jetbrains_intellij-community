// "Make block type suspend" "true"
// WITH_STDLIB

import kotlin.coroutines.suspendCoroutine
import kotlin.coroutines.startCoroutine

suspend fun <T> suspending(block: () -> T): T = suspendCoroutine { block.<caret>startCoroutine(it) }

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.AddSuspendModifierFix
// IGNORE_K2