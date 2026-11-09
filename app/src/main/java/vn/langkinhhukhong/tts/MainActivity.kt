package vn.langkinhhukhong.tts

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import vn.langkinhhukhong.tts.ui.CapCutTtsEditorScreen
import vn.langkinhhukhong.tts.ui.EditorViewModel

class MainActivity : ComponentActivity() {

    private val editorViewModel: EditorViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme()
            ) {
                CapCutTtsEditorScreen(viewModel = editorViewModel)
            }
        }
    }
}
