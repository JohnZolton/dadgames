package dadgames.solitaire

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

class MainActivity : ComponentActivity() {
    private val vm: GameViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent {
            val palette = backgrounds.getOrElse(vm.settings.background) { backgrounds[0] }.palette
            DadTheme(palette) {
                BackHandler(enabled = vm.screen != Screen.HOME || vm.confirmLeave != null) {
                    if (vm.confirmLeave != null) vm.confirmLeaveNo() else vm.back()
                }
                Crossfade(vm.screen, animationSpec = tween(200), label = "screen") { s ->
                    when (s) {
                        Screen.HOME -> HomeScreen(vm)
                        Screen.GAME -> GameScreen(vm)
                        Screen.SETTINGS -> SettingsScreen(vm)
                        Screen.STATS -> StatsScreen(vm)
                        Screen.DAILY -> DailyScreen(vm)
                    }
                }
                if (vm.confirmLeave != null) LeaveConfirm(vm)
            }
        }
    }

    // The clock only runs while the game is on screen. [timer pauses in the background]
    override fun onStart() { super.onStart(); vm.onForeground(true) }
    override fun onStop() { vm.onForeground(false); super.onStop() }
}
