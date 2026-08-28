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
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.print.PrintHelper

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

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->

        if (uri != null) {
            selectedBackground = BackgroundStyle.SKY_BLUE

            state = BackgroundRemovalState.LoadingImage(
                uri = uri
            )
        }
    }

    fun openGallery() {
        picker.launch("image/*")
    }

    /*
     * Handle an image passed into this tool from another
     * part of Juganua.
     */
    LaunchedEffect(initialUri) {

        if (
            initialUri != null &&
            state is BackgroundRemovalState.Empty
        ) {
            state = BackgroundRemovalState.LoadingImage(
                uri = initialUri
            )
        }
    }

    /*
     * Load image and start ML Kit processing.
     */
    LaunchedEffect(state) {

        val loading =
            state as? BackgroundRemovalState.LoadingImage
                ?: return@LaunchedEffect

        val bitmap = kotlinx.coroutines.withContext(
            kotlinx.coroutines.Dispatchers.IO
        ) {
            BackgroundRemoverLogic.loadBitmap(
                context = context,
                uri = loading.uri
            )
        }

        if (bitmap == null) {

            state = BackgroundRemovalState.Error(
                message = "Couldn't open this image."
            )

            return@LaunchedEffect
        }

        /*
         * Show original image while ML Kit is processing.
         */
        state = BackgroundRemovalState.Processing(
            original = bitmap
        )

        BackgroundRemoverLogic.removeBackground(

            bitmap = bitmap,

            onSuccess = { foreground ->

                val result =
                    BackgroundRemoverLogic.createResultBitmap(
                        foreground = foreground,
                        style = selectedBackground
                    )

                state = BackgroundRemovalState.Ready(
                    original = bitmap,
                    foreground = foreground,
                    result = result,
                    background = selectedBackground
                )
            },

            onFailure = { exception ->

                state = BackgroundRemovalState.Error(
                    message =
                        exception.message
                            ?: "Couldn't remove the background.",
                    original = bitmap
                )
            }
        )
    }

    /*
     * IMPORTANT:
     *
     * No Scaffold / TopAppBar here.
     *
     * MainActivity already provides Juganua's global
     * top bar and back button.
     */
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(
                rememberScrollState()
            )
            .padding(16.dp),

        verticalArrangement =
            Arrangement.spacedBy(18.dp)
    ) {

        when (val current = state) {

            /*
             * ------------------------------------------------
             * EMPTY
             * ------------------------------------------------
             */
            BackgroundRemovalState.Empty -> {

                BackgroundPreview(
                    bitmap = null
                )

                Button(
                    onClick = {
                        openGallery()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {

                    Icon(
                        imageVector =
                            Icons.Default.AddPhotoAlternate,
                        contentDescription = null
                    )

                    Spacer(
                        modifier = Modifier.width(6.dp)
                    )

                    Text(
                        text = "Choose from Gallery"
                    )
                }
            }

            /*
             * ------------------------------------------------
             * LOADING IMAGE
             * ------------------------------------------------
             */
            is BackgroundRemovalState.LoadingImage -> {

                val previewBitmap =
                    BackgroundRemoverLogic.loadBitmap(
                        context = context,
                        uri = current.uri
                    )

                BackgroundPreview(
                    bitmap = previewBitmap
                )
            }

            /*
             * ------------------------------------------------
             * ML KIT PROCESSING
             * ------------------------------------------------
             */
            is BackgroundRemovalState.Processing -> {

                Box(
                    modifier = Modifier.fillMaxWidth()
                ) {

                    BackgroundPreview(
                        bitmap = current.original
                    )

                    ProcessingOverlay()
                }
            }

            /*
             * ------------------------------------------------
             * READY
             * ------------------------------------------------
             */
            is BackgroundRemovalState.Ready -> {

                /*
                 * Final image preview.
                 */
                BackgroundPreview(
                    bitmap = current.result
                )

                /*
                 * Five background choices.
                 */
                BackgroundSelector(
                    selected = selectedBackground,

                    onSelected = { newStyle ->

                        selectedBackground = newStyle

                        /*
                         * IMPORTANT:
                         *
                         * Do NOT run ML Kit again.
                         *
                         * We already have the foreground bitmap,
                         * so only the background is regenerated.
                         */
                        val newResult =
                            BackgroundRemoverLogic
                                .createResultBitmap(
                                    foreground =
                                        current.foreground,
                                    style =
                                        newStyle
                                )

                        state = current.copy(
                            result = newResult,
                            background = newStyle
                        )
                    }
                )

                /*
                 * Save / Print / Open / More
                 */
                BackgroundActionBar(

                    enabled = true,

                    onSave = {

                        val uri =
                            BackgroundRemoverLogic
                                .saveBitmap(
                                    context = context,
                                    bitmap = current.result
                                )

                        Toast.makeText(
                            context,

                            if (uri != null) {
                                "Image saved to Gallery"
                            } else {
                                "Couldn't save image"
                            },

                            Toast.LENGTH_SHORT
                        ).show()
                    },

                    onPrint = {

                        printBitmap(
                            context = context,
                            bitmap = current.result
                        )
                    },

                    onShare = {

                        shareBitmap(
                            context = context,
                            bitmap = current.result
                        )
                    },

                    onAddImage = {

                        /*
                         * Select another image without
                         * returning to the dashboard.
                         */
                        openGallery()
                    }
                )
                Button(
                    onClick = {
                        sendToIntentHub(
                            context = context,
                            bitmap = current.result
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {

                    Text(
                        text = "Send to Intent Hub"
                    )
                }
            }

            /*
             * ------------------------------------------------
             * ERROR
             * ------------------------------------------------
             */
            is BackgroundRemovalState.Error -> {

                current.original?.let { bitmap ->

                    BackgroundPreview(
                        bitmap = bitmap
                    )
                }

                Text(
                    text = current.message,
                    color =
                        MaterialTheme.colorScheme.error
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

/*
 * ------------------------------------------------------------
 * PRINT
 * ------------------------------------------------------------
 */
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

/*
 * ------------------------------------------------------------
 * OPEN IN OTHER APPS
 * ------------------------------------------------------------
 */
private fun shareBitmap(
    context: Context,
    bitmap: Bitmap
) {

    val uri =
        BackgroundRemoverLogic
            .createShareUri(
                context = context,
                bitmap = bitmap
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

private fun sendToIntentHub(
    context: Context,
    bitmap: Bitmap
) {
    val uri =
        BackgroundRemoverLogic.createShareUri(
            context = context,
            bitmap = bitmap
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
        Intent(
            context,
            cvam.dignity.juganua.common.IntentReceiveActivity::class.java
        ).apply {

            action = Intent.ACTION_SEND

            type = "image/jpeg"

            putExtra(
                Intent.EXTRA_STREAM,
                uri
            )

            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )

            addFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TOP
            )
        }

    try {

        context.startActivity(intent)

    } catch (_: Exception) {

        Toast.makeText(
            context,
            "Unable to open Intent Hub",
            Toast.LENGTH_SHORT
        ).show()
    }
}