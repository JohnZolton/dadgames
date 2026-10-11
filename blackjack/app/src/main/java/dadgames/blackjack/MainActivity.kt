package dadgames.blackjack

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import dadgames.ui.DadTheme
import dadgames.ui.GamePalette

class MainActivity : ComponentActivity() {
    private val vm: GameViewModel by viewModels()

    override fun onStart() {
        super.onStart()
        vm.resumeLoop()
    }

    /** Backgrounded: pause dealer play and auto-deal until he's back. [BEST_PRACTICES 12z] */
    override fun onStop() {
        vm.pause()
        super.onStop()
    }

    /** singleTop (manifest): debug hooks also reach a running app. */
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        debugIntent(intent)
    }

    /** Debuggable builds only: `--es stack 8,6,8,10` stacks the shoe so screenshots can show splits etc. */
    private fun debugIntent(i: android.content.Intent?) {
        if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE == 0) return
        if (i?.hasExtra("balance") == true) vm.debugBalance(i.getLongExtra("balance", 0))
        val s = i?.getStringExtra("stack") ?: return
        vm.debugStack(s.split(",").mapNotNull { it.trim().toIntOrNull() })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        debugIntent(intent)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent {
            DadTheme(GamePalette.Felt) {
                BackHandler(enabled = vm.screen != Screen.HOME) {
                    when (vm.screen) {
                        Screen.TABLE -> vm.leaveTable()
                        Screen.TABLES -> if (vm.midHand) vm.openTable() else vm.screen = Screen.HOME
                        else -> vm.screen = Screen.HOME
                    }
                }
                Crossfade(vm.screen, animationSpec = tween(220), label = "screen") { s ->
                    when (s) {
                        Screen.HOME -> HomeScreen(vm)
                        Screen.TABLE -> TableScreen(vm)
                        Screen.TABLES -> TablesScreen(vm)
                        Screen.SETTINGS -> SettingsScreen(vm)
                        Screen.STATS -> StatsScreen(vm)
                        Screen.HELP -> HelpScreen(vm)
                    }
                }
            }
        }
    }
}
