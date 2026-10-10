package euchre.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween

class MainActivity : ComponentActivity() {
    private val vm: GameViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent {
            EuchreTheme {
                BackHandler(enabled = vm.screen != Screen.HOME) {
                    if (vm.screen == Screen.GAME) vm.leaveGame() else vm.screen = Screen.HOME
                }
                Crossfade(vm.screen, animationSpec = tween(220), label = "screen") { s ->
                    when (s) {
                        Screen.HOME -> HomeScreen(vm)
                        Screen.GAME -> GameScreen(vm)
                        Screen.SETTINGS -> SettingsScreen(vm)
                        Screen.HELP -> HelpScreen(vm)
                    }
                }
            }
        }
    }
}
