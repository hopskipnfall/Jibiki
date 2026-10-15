package dev.jibiki.app

import androidx.compose.runtime.mutableStateListOf

sealed interface Screen {
    data object Search : Screen
    data class Kanji(val character: String) : Screen
    data object Dictionaries : Screen
    data object Profiles : Screen
    data object Settings : Screen
    data object Licenses : Screen
    data object Onboarding : Screen
}

class Navigator {
    val stack = mutableStateListOf<Screen>(Screen.Search)

    val current: Screen get() = stack.last()
    val canGoBack: Boolean get() = stack.size > 1

    fun push(screen: Screen) {
        if (stack.last() != screen) stack.add(screen)
    }

    fun pop(): Boolean {
        if (stack.size <= 1) return false
        stack.removeAt(stack.lastIndex)
        return true
    }

    fun resetTo(screen: Screen) {
        stack.clear()
        stack.add(screen)
    }
}
