package dadgames.sudoku

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import dadgames.ui.DadTheme
import dadgames.ui.GamePalette

class MainActivity : ComponentActivity() {
    private val vm: GameViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Light paper background: dark status and nav bar icons.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        setContent {
            DadTheme(GamePalette.Paper) {
                BackHandler(enabled = vm.screen != Screen.HOME || vm.choosingDifficulty) {
                    when {
                        vm.choosingDifficulty -> vm.choosingDifficulty = false
                        vm.screen == Screen.GAME -> vm.leaveGame()
                        else -> vm.screen = Screen.HOME
                    }
                }
                Box(Modifier.fillMaxSize()) {
                    Crossfade(vm.screen, animationSpec = tween(200), label = "screen") { s ->
                        when (s) {
                            Screen.HOME -> HomeScreen(vm)
                            Screen.GAME -> GameScreen(vm)
                            Screen.DAILY -> DailyScreen(vm)
                            Screen.STATS -> StatsScreen(vm)
                            Screen.SETTINGS -> SettingsScreen(vm)
                            Screen.HELP -> HelpScreen(vm)
                        }
                    }
                    vm.busy?.let { BusyOverlay(it) }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        vm.setForeground(true)
    }

    override fun onStop() {
        vm.setForeground(false)
        super.onStop()
    }
}
