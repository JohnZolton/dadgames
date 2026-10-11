package dadgames.blocks

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import dadgames.ui.DadTheme
import dadgames.ui.GamePalette

class MainActivity : ComponentActivity() {
    private val vm: GameViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        // Relaunched after Android killed us mid-game: go straight back to the board.
        if (savedInstanceState == null) vm.restoreScreen()
        setContent {
            DadTheme(GamePalette.Wood) {
                LaunchedEffect(vm.screen) { vm.rememberScreen() }
                BackHandler(enabled = vm.screen != Screen.HOME) {
                    when (vm.screen) {
                        Screen.GAME -> vm.leaveGame()
                        Screen.SETTINGS, Screen.HELP -> vm.screen = vm.returnTo
                        else -> vm.screen = Screen.HOME
                    }
                }
                Crossfade(vm.screen, animationSpec = tween(200), label = "screen") { s ->
                    when (s) {
                        Screen.HOME -> HomeScreen(vm)
                        Screen.GAME -> GameScreen(vm)
                        Screen.DAILY -> DailyScreen(vm)
                        Screen.SETTINGS -> SettingsScreen(vm)
                        Screen.HELP -> HelpScreen(vm)
                        Screen.STATS -> StatsScreen(vm)
                    }
                }
            }
        }
    }
}
