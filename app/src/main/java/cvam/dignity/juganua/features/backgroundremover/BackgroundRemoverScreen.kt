package cvam.dignity.juganua.features.backgroundremover

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.print.PrintHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackgroundRemoverScreen(
    initialUri: Uri? = null,
    onBack: () -> Unit
) {

    val context = LocalContext.current

    var state by remember {
        mutableStateOf<BackgroundRemovalState>(
            BackgroundRemovalState.Empty
        )
    }

    var selectedBackground by remember {
        mutableStateOf(
            BackgroundStyle.SKY_BLUE
        )
    }

    val picker =
        rememberLauncherForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri ->

            if (uri != null) {

                selectedBackground =
                    BackgroundStyle.SKY_BLUE

                state =
                    BackgroundRemovalState.LoadingImage(
                        uri
                    )
            }
        }

    fun openGallery() {
        picker.launch("image/*")
    }

    LaunchedEffect(initialUri) {

        if (
            initialUri != null &&
            state is BackgroundRemovalState.Empty
        ) {

            state =
                BackgroundRemovalState.LoadingImage(
                    initialUri
                )
        }
    }

    LaunchedEffect(state) {

        val loading =
            state as? BackgroundRemovalState.LoadingImage
                ?: return@LaunchedEffect

        val bitmap =
            kotlinx.coroutines.withContext(
                kotlinx.coroutines.Dispatchers.IO
            ) {
                BackgroundRemoverLogic.loadBitmap(
                    context,
                    loading.uri
                )
            }

        if (bitmap == null) {

            state =
                BackgroundRemovalState.Error(
                    message =
                        "Couldn't open this image."
                )

            return@LaunchedEffect
        }

        state =
            BackgroundRemovalState.Processing(
                original = bitmap
            )

        BackgroundRemoverLogic.removeBackground(

            bitmap = bitmap,

            onSuccess = { foreground ->

                val result =
                    BackgroundRemoverLogic
                        .createResultBitmap(
                            foreground,
                            selectedBackground
                        )

                state =
                    BackgroundRemovalState.Ready(
                        original = bitmap,
                        foreground = foreground,
                        result = result,
                        background =
                            selectedBackground
                    )
            },

            onFailure = { exception ->

                state =
                    BackgroundRemovalState.Error(
                        message =
                            exception.message
                                ?: "Couldn't remove the background.",
                        original = bitmap
                    )
            }
        )
    }

    Scaffold(

        topBar = {

            TopAppBar(

                title = {
                    Text("Background Remover")
                },

                navigationIcon = {

                    IconButton(
                        onClick = onBack
                    ) {

                        Icon(
                            imageVector =
                                Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription =
                                "Back"
                        )
                    }
                },

                actions = {

                    if (
                        state is BackgroundRemovalState.Ready
                    ) {

                        IconButton(
                            onClick = {
                                openGallery()
                            }
                        ) {

                            Icon(
                                imageVector =
                                    Icons.Default.AddPhotoAlternate,
                                contentDescription =
                                    "Process another image"
                            )
                        }
                    }
                }
            )
        }

    ) { padding ->

        Column(

            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(
                    rememberScrollState()
                )
                .padding(16.dp),

            verticalArrangement =
                Arrangement.spacedBy(18.dp)
        ) {

            when (val current = state) {

                BackgroundRemovalState.Empty -> {

                    BackgroundPreview(
                        bitmap = null
                    )

                    Button(
                        onClick = {
                            openGallery()
                        },
                        modifier =
                            Modifier.fillMaxWidth()
                    ) {

                        Icon(
                            imageVector =
                                Icons.Default.AddPhotoAlternate,
                            contentDescription = null
                        )

                        Spacer(
                            modifier =
                                Modifier.width(6.dp)
                        )

                        Text("Choose from Gallery")
                    }
                }

                is BackgroundRemovalState.LoadingImage -> {

                    val previewBitmap =
                        BackgroundRemoverLogic
                            .loadBitmap(
                                context,
                                current.uri
                            )

                    BackgroundPreview(
                        bitmap = previewBitmap
                    )
                }

                is BackgroundRemovalState.Processing -> {

                    Box(
                        modifier =
                            Modifier.fillMaxWidth()
                    ) {

                        BackgroundPreview(
                            bitmap = current.original
                        )

                        ProcessingOverlay()
                    }
                }

                is BackgroundRemovalState.Ready -> {

                    BackgroundPreview(
                        bitmap = current.result
                    )

                    BackgroundSelector(

                        selected =
                            selectedBackground,

                        onSelected = { newStyle ->

                            selectedBackground =
                                newStyle

                            val newResult =
                                BackgroundRemoverLogic
                                    .createResultBitmap(
                                        current.foreground,
                                        newStyle
                                    )

                            state =
                                current.copy(
                                    result = newResult,
                                    background = newStyle
                                )
                        }
                    )

                    BackgroundActionBar(

                        enabled = true,

                        onSave = {

                            val uri =
                                BackgroundRemoverLogic
                                    .saveBitmap(
                                        context,
                                        current.result
                                    )

                            Toast.makeText(
                                context,
                                if (uri != null)
                                    "Image saved to Gallery"
                                else
                                    "Couldn't save image",
                                Toast.LENGTH_SHORT
                            ).show()
                        },

                        onPrint = {

                            printBitmap(
                                context,
                                current.result
                            )
                        },

                        onShare = {

                            shareBitmap(
                                context,
                                current.result
                            )
                        },

                        onAddImage = {
                            openGallery()
                        }
                    )
                }

                is BackgroundRemovalState.Error -> {

                    current.original?.let {

                        BackgroundPreview(
                            bitmap = it
                        )
                    }

                    Text(
                        text = current.message,
                        color =
                            MaterialTheme
                                .colorScheme
                                .error
                    )

                    Row(
                        modifier =
                            Modifier.fillMaxWidth(),
                        horizontalArrangement =
                            Arrangement.spacedBy(12.dp)
                    ) {

                        Button(
                            onClick = {
                                openGallery()
                            },
                            modifier =
                                Modifier.weight(1f)
                        ) {
                            Text("Choose Another")
                        }

                        Button(
                            onClick = {
                                openGallery()
                            },
                            modifier =
                                Modifier.weight(1f)
                        ) {
                            Text("Retry")
                        }
                    }
                }
            }
        }
    }
}

private fun printBitmap(
    context: Context,
    bitmap: Bitmap
) {

    try {

        val helper =
            PrintHelper(context)

        helper.scaleMode =
            PrintHelper.SCALE_MODE_FIT

        helper.colorMode =
            PrintHelper.COLOR_MODE_COLOR

        helper.printBitmap(
            "Juganua Background Remover",
            bitmap
        )

    } catch (_: Exception) {

        Toast.makeText(
            context,
            "Unable to open print service",
            Toast.LENGTH_SHORT
        ).show()
    }
}

private fun shareBitmap(
    context: Context,
    bitmap: Bitmap
) {

    val uri =
        BackgroundRemoverLogic
            .createShareUri(
                context,
                bitmap
            )

    if (uri == null) {

        Toast.makeText(
            context,
            "Couldn't prepare image",
            Toast.LENGTH_SHORT
        ).show()

        return
    }

    val intent =
        Intent(Intent.ACTION_SEND).apply {

            type = "image/jpeg"

            putExtra(
                Intent.EXTRA_STREAM,
                uri
            )

            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }

    try {

        context.startActivity(
            Intent.createChooser(
                intent,
                "Open image with"
            )
        )

    } catch (_: Exception) {

        Toast.makeText(
            context,
            "No compatible app found",
            Toast.LENGTH_SHORT
        ).show()
    }
}