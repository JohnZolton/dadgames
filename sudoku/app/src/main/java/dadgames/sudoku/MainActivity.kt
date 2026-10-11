package dadgames.sudoku

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dadgames.ui.DadTheme
import dadgames.ui.GamePalette
import dadgames.ui.P
import dadgames.ui.menuBackground

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent {
            DadTheme(GamePalette.Paper) {
                Box(Modifier.fillMaxSize().menuBackground().systemBarsPadding(), contentAlignment = Alignment.Center) {
                    Text("Sudoku", style = MaterialTheme.typography.headlineLarge, color = P.text)
                }
            }
        }
    }
}
