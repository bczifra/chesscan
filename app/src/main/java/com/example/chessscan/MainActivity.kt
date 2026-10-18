package com.example.chessscan

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { Surface(Modifier.fillMaxSize()) { App() } } }
    }
}

private enum class Screen { HOME, REVIEW, PREVIEW }

@Composable
private fun App() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var movesText by rememberSaveable { mutableStateOf("") }
    var white by rememberSaveable { mutableStateOf("") }
    var black by rememberSaveable { mutableStateOf("") }
    var result by rememberSaveable { mutableStateOf("") }
    var corrections by remember { mutableStateOf(listOf<MoveParser.Correction>()) }
    var png by remember { mutableStateOf<Bitmap?>(null) }

    val photoUri = remember {
        val dir = File(context.cacheDir, "images").apply { mkdirs() }
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(dir, "scan.jpg"))
    }

    fun process(uri: Uri) {
        scope.launch {
            busy = true; error = null
            try {
                val raw = Ocr.recognize(context, uri)
                if (raw.isBlank()) {
                    error = "No text found. Try a sharper, well-lit photo."
                } else {
                    val r = MoveParser.autoCorrect(MoveParser.tokenize(raw))
                    movesText = r.text
                    corrections = r.corrections
                    screen = Screen.REVIEW
                }
            } catch (e: Exception) {
                error = "OCR failed: ${e.message}"
            } finally {
                busy = false
            }
        }
    }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) process(photoUri)
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) process(uri)
    }

    BackHandler(enabled = screen != Screen.HOME) {
        screen = if (screen == Screen.PREVIEW) Screen.REVIEW else Screen.HOME
    }

    when (screen) {
        Screen.HOME -> Column(
            Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("ChessScan", style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(8.dp))
            Text("Photograph a scoresheet, check the moves, share a PNG.")
            Spacer(Modifier.height(32.dp))
            Button(onClick = { takePicture.launch(photoUri) }, enabled = !busy) { Text("Take photo") }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = { pickImage.launch("image/*") }, enabled = !busy) {
                Text("Choose from gallery")
            }
            Spacer(Modifier.height(24.dp))
            if (busy) CircularProgressIndicator()
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }

        Screen.REVIEW -> {
            val v = remember(movesText) { MoveParser.validate(movesText) }
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                Text("Review moves", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(white, { white = it }, label = { Text("White") },
                        singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(black, { black = it }, label = { Text("Black") },
                        singleLine = true, modifier = Modifier.weight(1f))
                }
                OutlinedTextField(result, { result = it }, label = { Text("Result (e.g. 1-0)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                OutlinedTextField(
                    movesText, { movesText = it }, label = { Text("Moves") },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp), minLines = 6
                )
                Spacer(Modifier.height(8.dp))
                when {
                    v.badPly != null -> {
                        val who = if (v.badPly % 2 == 0) "White" else "Black"
                        Text(
                            "Move ${v.badPly / 2 + 1} ($who): '${v.badToken}' isn't legal here. Fix it above.",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    v.sans.isEmpty() -> Text("Enter moves to continue.")
                    else -> Text("All ${v.sans.size} half-moves are legal.")
                }
                if (corrections.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp)) {
                            Text("Auto-corrected, please double-check:", style = MaterialTheme.typography.titleSmall)
                            corrections.forEach { Text(it.label) }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        val r = MoveParser.autoCorrect(MoveParser.tokenize(movesText))
                        movesText = r.text
                        corrections = r.corrections
                    }) { Text("Auto-correct") }
                    Button(
                        enabled = v.badPly == null && v.sans.isNotEmpty(),
                        onClick = {
                            png = BoardRenderer.render(v, white, black, result)
                            screen = Screen.PREVIEW
                        }
                    ) { Text("Create PNG") }
                }
            }
        }

        Screen.PREVIEW -> Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
        ) {
            png?.let {
                Image(it.asImageBitmap(), contentDescription = "Game image",
                    modifier = Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth)
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { sharePng(context, it) }) { Text("Share PNG") }
                    OutlinedButton(onClick = { screen = Screen.REVIEW }) { Text("Back to edit") }
                }
            }
        }
    }
}

private fun sharePng(context: Context, bmp: Bitmap) {
    val dir = File(context.cacheDir, "shared").apply { mkdirs() }
    val file = File(dir, "chess_game.png")
    FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, "Share game PNG"))
}
