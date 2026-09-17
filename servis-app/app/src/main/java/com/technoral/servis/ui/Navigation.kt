package com.technoral.servis.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember

sealed interface Screen {
    data object Dashboard : Screen
    data object Reports : Screen
    data object Machines : Screen
    data object Customers : Screen
    data object Settings : Screen

    data class ReportDetail(val reportId: String) : Screen
    data object ReportEdit : Screen
    data class MachineDetail(val machineId: String) : Screen
    data class CustomerDetail(val customerId: String) : Screen
    data object MailSettings : Screen
    data object CompanySettings : Screen
    data object Setup : Screen
}

val rootScreens = listOf(
    Screen.Dashboard,
    Screen.Reports,
    Screen.Machines,
    Screen.Customers,
    Screen.Settings,
)

/** Kütüphane bağımlılığı olmadan, geri yığını elle tutan küçük bir yönlendirici. */
class Navigator(start: Screen) {
    val stack = mutableStateListOf(start)

    val current: Screen get() = stack.last()

    fun go(screen: Screen) {
        stack.add(screen)
    }

    /** Alt menüdeki kökler arasında geçiş — yığını sıfırlar. */
    fun switchRoot(screen: Screen) {
        stack.clear()
        stack.add(screen)
    }

    fun back(): Boolean {
        if (stack.size <= 1) return false
        stack.removeAt(stack.lastIndex)
        return true
    }

    fun backTo(screen: Screen) {
        val index = stack.indexOfLast { it == screen }
        if (index >= 0) {
            while (stack.lastIndex > index) stack.removeAt(stack.lastIndex)
        } else {
            switchRoot(screen)
        }
    }

    fun replace(screen: Screen) {
        if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex)
        stack.add(screen)
    }
}

@Composable
fun rememberNavigator(start: Screen): Navigator = remember { Navigator(start) }
