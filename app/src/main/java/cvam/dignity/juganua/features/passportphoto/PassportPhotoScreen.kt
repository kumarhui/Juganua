package cvam.dignity.juganua.features.passportphoto

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.yalantis.ucrop.UCrop
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun PassportPhotoScreen(
    initialUris: List<Uri>? = null,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    /*
     * ---------------------------------------------------------
     * STATE
     * ---------------------------------------------------------
     */

    var selectedRow by remember {
        mutableIntStateOf(0)
    }

    var selectedPhoto by remember {
        mutableStateOf<Bitmap?>(null)
    }

    var foregroundPhoto by remember {
        mutableStateOf<Bitmap?>(null)
    }

    var processedPhoto by remember {
        mutableStateOf<Bitmap?>(null)
    }

    var selectedBackground by remember {
        mutableStateOf(
            PassportBackground.SKY_BLUE
        )
    }

    var isProcessing by remember {
        mutableStateOf(false)
    }

    var gridBitmap by remember {
        mutableStateOf<Bitmap?>(null)
    }

    var generatingGrid by remember {
        mutableStateOf(false)
    }

    var showBackgroundDialog by remember {
        mutableStateOf(false)
    }

    /*
     * This controls whether the A6/result UI is visible.
     *
     * IMPORTANT:
     * Initially false.
     * It becomes true only after crop + ML Kit processing.
     */
    var processingFinished by remember {
        mutableStateOf(false)
    }

    /*
     * ---------------------------------------------------------
     * CROP LAUNCHER
     * ---------------------------------------------------------
     */

    val cropLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->

            if (
                result.resultCode !=
                Activity.RESULT_OK
            ) {
                isProcessing = false
                processingFinished = false
                return@rememberLauncherForActivityResult
            }

            val croppedUri =
                result.data?.let {
                    UCrop.getOutput(it)
                }

            if (croppedUri == null) {
                isProcessing = false
                processingFinished = false

                Toast.makeText(
                    context,
                    "Couldn't crop image",
                    Toast.LENGTH_SHORT
                ).show()

                return@rememberLauncherForActivityResult
            }

            /*
             * -------------------------------------------------
             * CROP FINISHED
             *
             * Now silently run ML Kit.
             *
             * NO intermediate background-removal UI.
             * -------------------------------------------------
             */
            scope.launch {

                isProcessing = true
                processingFinished = false

                val croppedBitmap =
                    PassportPhotoLogic
                        .loadBitmapInternal(
                            context,
                            croppedUri
                        )

                if (croppedBitmap == null) {

                    isProcessing = false

                    Toast.makeText(
                        context,
                        "Couldn't load cropped image",
                        Toast.LENGTH_SHORT
                    ).show()

                    return@launch
                }

                selectedPhoto =
                    croppedBitmap

                selectedBackground =
                    PassportBackground.SKY_BLUE

                /*
                 * Run ML Kit silently.
                 */
                val foreground =
                    PassportPhotoLogic
                        .removeBackground(
                            croppedBitmap
                        )

                if (foreground == null) {

                    isProcessing = false
                    processingFinished = false

                    Toast.makeText(
                        context,
                        "Background removal failed",
                        Toast.LENGTH_SHORT
                    ).show()

                    return@launch
                }

                foregroundPhoto =
                    foreground

                /*
                 * Default background = Sky Blue.
                 */
                val resultBitmap =
                    PassportPhotoLogic
                        .createBackgroundResult(
                            foreground,
                            PassportBackground.SKY_BLUE
                        )

                processedPhoto =
                    resultBitmap

                isProcessing = false

                /*
                 * ONLY NOW reveal:
                 *
                 * - processed photo
                 * - edit background
                 * - slider
                 * - A6 result
                 */
                processingFinished = true
            }
        }

    /*
     * ---------------------------------------------------------
     * GALLERY PICKER
     * ---------------------------------------------------------
     */

    val pickerLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri ->

            if (uri == null) {
                return@rememberLauncherForActivityResult
            }

            val destination =
                Uri.fromFile(
                    File(
                        context.cacheDir,
                        "passport_crop_${System.currentTimeMillis()}.png"
                    )
                )

            /*
             * Crop UI is handled by uCrop.
             */
            cropLauncher.launch(
                UCrop
                    .of(
                        uri,
                        destination
                    )
                    .withAspectRatio(
                        30f,
                        40f
                    )
                    .getIntent(
                        context
                    )
            )
        }

    /*
     * ---------------------------------------------------------
     * INTENT INPUT
     * ---------------------------------------------------------
     *
     * If another app sends an image directly to Passport Photo,
     * start the same crop → silent background-removal flow.
     */
    LaunchedEffect(initialUris) {

        val uri =
            initialUris
                ?.firstOrNull()
                ?: return@LaunchedEffect

        val destination =
            Uri.fromFile(
                File(
                    context.cacheDir,
                    "passport_intent_${System.currentTimeMillis()}.png"
                )
            )

        cropLauncher.launch(
            UCrop
                .of(
                    uri,
                    destination
                )
                .withAspectRatio(
                    30f,
                    40f
                )
                .getIntent(
                    context
                )
        )
    }

    /*
     * ---------------------------------------------------------
     * LIVE A6 PREVIEW
     * ---------------------------------------------------------
     *
     * Only runs after processingFinished.
     *
     * selectedRow is directly connected to this effect,
     * therefore moving slider 1 → 3 updates the preview.
     */
    LaunchedEffect(
        selectedRow,
        processedPhoto,
        processingFinished
    ) {

        if (
            !processingFinished ||
            processedPhoto == null
        ) {
            gridBitmap = null
            return@LaunchedEffect
        }

        val bitmap =
            processedPhoto
                ?: return@LaunchedEffect

        generatingGrid = true

        gridBitmap =
            PassportPhotoLogic
                .createMultiPhotoGrid(
                    rows =
                        mapOf(
                            selectedRow to bitmap
                        ),
                    paperSize =
                        PhotoPaperSize.A6
                )

        generatingGrid = false
    }

    /*
     * ---------------------------------------------------------
     * BACK HANDLER
     * ---------------------------------------------------------
     */

    BackHandler {

        if (showBackgroundDialog) {

            showBackgroundDialog = false

        } else if (processingFinished) {

            /*
             * Go back to the simple Select Photo state.
             */
            processingFinished = false
            processedPhoto = null
            foregroundPhoto = null
            gridBitmap = null

        } else {

            onBack()
        }
    }

    /*
     * ---------------------------------------------------------
     * MAIN CONTENT
     *
     * NO SCAFFOLD.
     * NO TOP APP BAR.
     *
     * MainActivity supplies the normal Juganua top bar,
     * just like Boga Scanner / WhatsApp Checker.
     * ---------------------------------------------------------
     */

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(
                horizontal = 16.dp,
                vertical = 8.dp
            ),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

            /*
             * =================================================
             * INITIAL SCREEN
             * =================================================
             *
             * ONLY SELECT PHOTO.
             */
            if (!processingFinished) {

                if (isProcessing) {

                    Card(
                        modifier =
                            Modifier.fillMaxWidth(),

                        shape =
                            RoundedCornerShape(24.dp)
                    ) {

                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),

                            horizontalAlignment =
                                Alignment.CenterHorizontally,

                            verticalArrangement =
                                Arrangement.spacedBy(
                                    14.dp
                                )
                        ) {

                            CircularProgressIndicator()

                            Text(
                                text =
                                    "Preparing passport photo…",

                                fontWeight =
                                    FontWeight.Bold
                            )

                            Text(
                                text =
                                    "Removing background automatically",

                                style =
                                    MaterialTheme
                                        .typography
                                        .bodySmall
                            )
                        }
                    }

                } else {

                    Card(
                        modifier =
                            Modifier.fillMaxWidth(),

                        shape =
                            RoundedCornerShape(28.dp)
                    ) {

                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(28.dp),

                            horizontalAlignment =
                                Alignment.CenterHorizontally,

                            verticalArrangement =
                                Arrangement.spacedBy(
                                    14.dp
                                )
                        ) {

                            Text(
                                text =
                                    "PASSPORT PHOTO",

                                fontSize =
                                    22.sp,

                                fontWeight =
                                    FontWeight.ExtraBold
                            )

                            Text(
                                text =
                                    "Select a photo to create your passport photo sheet.",

                                style =
                                    MaterialTheme
                                        .typography
                                        .bodyMedium
                            )

                            Spacer(
                                Modifier.height(12.dp)
                            )

                            Button(
                                onClick = {
                                    pickerLauncher.launch(
                                        "image/*"
                                    )
                                },

                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .height(58.dp),

                                shape =
                                    RoundedCornerShape(
                                        18.dp
                                    )
                            ) {

                                Text(
                                    text =
                                        "SELECT PHOTO",

                                    fontWeight =
                                        FontWeight.Black
                                )
                            }
                        }
                    }
                }

                return@Column
            }

            /*
             * =================================================
             * PROCESSED PHOTO
             * =================================================
             */

            Text(
                text =
                    "PHOTO READY",

                fontSize =
                    12.sp,

                fontWeight =
                    FontWeight.ExtraBold,

                color =
                    MaterialTheme
                        .colorScheme
                        .primary
            )

            Card(
                modifier =
                    Modifier.fillMaxWidth(),

                shape =
                    RoundedCornerShape(24.dp)
            ) {

                Column(
                    modifier =
                        Modifier.padding(14.dp),

                    horizontalAlignment =
                        Alignment.CenterHorizontally
                ) {

                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(384.dp)
                                .clip(RoundedCornerShape(22.dp))
                                .background(
                                    backgroundBrush(
                                        selectedBackground
                                    )
                                )
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.outlineVariant,
                                    RoundedCornerShape(22.dp)
                                ),

                        contentAlignment =
                            Alignment.Center
                    ) {

                        processedPhoto?.let { bitmap ->

                            Image(
                                bitmap =
                                    bitmap
                                        .asImageBitmap(),

                                contentDescription =
                                    "Processed passport photo",

                                modifier =
                                    Modifier
                                        .fillMaxHeight()
                                        .aspectRatio(30f / 40f)
                                        .clip(RoundedCornerShape(16.dp)),

                                // Never crop or stretch the processed result.
                                // The visible card uses the exact 30:40 passport ratio.
                                contentScale =
                                    ContentScale.Fit
                            )
                        }

                        /*
                         * Edit background icon.
                         */
                        IconButton(
                            onClick = {
                                showBackgroundDialog = true
                            },

                            modifier =
                                Modifier
                                    .align(
                                        Alignment.TopEnd
                                    )
                                    .padding(8.dp)
                                    .background(
                                        MaterialTheme
                                            .colorScheme
                                            .surface.copy(
                                                alpha = 0.92f
                                            ),
                                        RoundedCornerShape(
                                            14.dp
                                        )
                                    )
                        ) {

                            Icon(
                                imageVector =
                                    Icons.Default.Edit,

                                contentDescription =
                                    "Edit background"
                            )
                        }
                    }

                    Spacer(
                        Modifier.height(10.dp)
                    )

                    Text(
                        text =
                            "Background: ${selectedBackground.title}",

                        style =
                            MaterialTheme
                                .typography
                                .bodySmall
                    )

                    /*
                     * -------------------------------------------------
                     * PHOTO ACTIONS
                     * -------------------------------------------------
                     */
                    Row(
                        modifier =
                            Modifier.fillMaxWidth(),

                        horizontalArrangement =
                            Arrangement.Center
                    ) {

                        IconButton(
                            onClick = {

                                processedPhoto?.let {
                                        bitmap ->

                                    val uri =
                                        PassportPhotoLogic
                                            .saveBitmapToGallery(
                                                context,
                                                bitmap,
                                                "Passport_Photo_${System.currentTimeMillis()}.png"
                                            )

                                    Toast.makeText(
                                        context,

                                        if (uri != null)
                                            "Saved to Gallery"
                                        else
                                            "Couldn't save image",

                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        ) {

                            Icon(
                                imageVector =
                                    Icons.Default.Save,

                                contentDescription =
                                    "Save photo"
                            )
                        }

                        IconButton(
                            onClick = {

                                processedPhoto?.let {
                                        bitmap ->

                                    shareDirectToWhatsApp(
                                        context,
                                        bitmap
                                    )
                                }
                            }
                        ) {

                            Icon(
                                imageVector =
                                    Icons.Default.Share,

                                contentDescription =
                                    "Share to WhatsApp"
                            )
                        }

                        IconButton(
                            onClick = {

                                processedPhoto?.let {
                                        bitmap ->

                                    sendToNocoPrint(
                                        context,
                                        bitmap
                                    )
                                }
                            }
                        ) {

                            Icon(
                                imageVector =
                                    Icons.Default.Print,

                                contentDescription =
                                    "Print"
                            )
                        }
                    }
                }
            }

            /*
             * =================================================
             * PHOTO ROW SLIDER
             * =================================================
             *
             * It is impossible to see this before processing.
             */
            Card(
                modifier =
                    Modifier.fillMaxWidth(),

                shape =
                    RoundedCornerShape(22.dp)
            ) {

                Column(
                    modifier =
                        Modifier.padding(18.dp),

                    verticalArrangement =
                        Arrangement.spacedBy(8.dp)
                ) {

                    Row(
                        modifier =
                            Modifier.fillMaxWidth(),

                        horizontalArrangement =
                            Arrangement.SpaceBetween,

                        verticalAlignment =
                            Alignment.CenterVertically
                    ) {

                        Column {

                            Text(
                                text =
                                    "PHOTO ROW",

                                fontWeight =
                                    FontWeight.ExtraBold,

                                fontSize =
                                    12.sp
                            )

                            Text(
                                text =
                                    "Place six copies in this row",

                                style =
                                    MaterialTheme
                                        .typography
                                        .bodySmall
                            )
                        }

                        Text(
                            text =
                                "Row ${selectedRow + 1}",

                            fontWeight =
                                FontWeight.Bold
                        )
                    }

                    Slider(

                        value =
                            selectedRow.toFloat(),

                        onValueChange = {
                                value ->

                            selectedRow =
                                value
                                    .toInt()
                                    .coerceIn(
                                        0,
                                        5
                                    )
                        },

                        valueRange =
                            0f..5f,

                        steps = 4
                    )

                    Row(
                        modifier =
                            Modifier.fillMaxWidth(),

                        horizontalArrangement =
                            Arrangement.SpaceBetween
                    ) {

                        repeat(6) { index ->

                            Text(
                                text =
                                    "${index + 1}",

                                fontWeight =
                                    if (
                                        index ==
                                        selectedRow
                                    )
                                        FontWeight.ExtraBold
                                    else
                                        FontWeight.Normal,

                                color =
                                    if (
                                        index ==
                                        selectedRow
                                    )
                                        MaterialTheme
                                            .colorScheme
                                            .primary
                                    else
                                        MaterialTheme
                                            .colorScheme
                                            .onSurfaceVariant
                            )
                        }
                    }
                }
            }

            /*
             * =================================================
             * A6 RESULT
             * =================================================
             */

            Card(
                modifier =
                    Modifier.fillMaxWidth(),

                shape =
                    RoundedCornerShape(24.dp)
            ) {

                Column(
                    modifier =
                        Modifier.padding(14.dp),

                    horizontalAlignment =
                        Alignment.CenterHorizontally
                ) {

                    Text(
                        text =
                            "A6 RESULT",

                        fontWeight =
                            FontWeight.ExtraBold,

                        fontSize =
                            12.sp
                    )

                    Spacer(
                        Modifier.height(10.dp)
                    )

                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(380.dp),

                        contentAlignment =
                            Alignment.Center
                    ) {

                        if (generatingGrid) {

                            CircularProgressIndicator()

                        } else {

                            gridBitmap?.let {
                                    bitmap ->

                                Image(
                                    bitmap =
                                        bitmap.asImageBitmap(),

                                    contentDescription =
                                        "A6 result",

                                    modifier =
                                        Modifier
                                            .fillMaxSize()
                                            .padding(4.dp),

                                    contentScale =
                                        ContentScale.Fit
                                )
                            }
                        }
                    }

                    Text(
                        text =
                            "Row ${selectedRow + 1} • 6 copies",

                        style =
                            MaterialTheme
                                .typography
                                .bodySmall
                    )

                    Spacer(
                        Modifier.height(8.dp)
                    )

                    /*
                     * A6 action icons.
                     */
                    Row(
                        horizontalArrangement =
                            Arrangement.Center
                    ) {

                        IconButton(
                            onClick = {

                                gridBitmap?.let {
                                        bitmap ->

                                    val uri =
                                        PassportPhotoLogic
                                            .saveBitmapToGallery(
                                                context,
                                                bitmap,
                                                "Passport_A6_${System.currentTimeMillis()}.png"
                                            )

                                    Toast.makeText(
                                        context,

                                        if (uri != null)
                                            "Saved to Gallery"
                                        else
                                            "Couldn't save sheet",

                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        ) {

                            Icon(
                                imageVector =
                                    Icons.Default.Save,

                                contentDescription =
                                    "Save A6"
                            )
                        }

                        IconButton(
                            onClick = {

                                gridBitmap?.let {
                                        bitmap ->

                                    sendToNocoPrint(
                                        context,
                                        bitmap
                                    )
                                }
                            }
                        ) {

                            Icon(
                                imageVector =
                                    Icons.Default.Print,

                                contentDescription =
                                    "Print A6"
                            )
                        }

                        IconButton(
                            onClick = {

                                gridBitmap?.let {
                                        bitmap ->

                                    shareDirectToWhatsApp(
                                        context,
                                        bitmap
                                    )
                                }
                            }
                        ) {

                            Icon(
                                imageVector =
                                    Icons.Default.Share,

                                contentDescription =
                                    "Share A6 to WhatsApp"
                            )
                        }
                    }
                }
            }

            /*
             * Select another photo.
             */
            OutlinedButton(
                onClick = {

                    /*
                     * Reset result state.
                     */
                    processingFinished = false
                    selectedPhoto = null
                    foregroundPhoto = null
                    processedPhoto = null
                    gridBitmap = null

                    pickerLauncher.launch(
                        "image/*"
                    )
                },

                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(52.dp)
            ) {

                Text(
                    text =
                        "SELECT ANOTHER PHOTO",

                    fontWeight =
                        FontWeight.Bold
                )
            }

            Spacer(
                Modifier.height(12.dp)
            )
        }


    /*
     * =========================================================
     * EDIT BACKGROUND DIALOG
     * =========================================================
     */

    if (showBackgroundDialog) {

        BackgroundEditDialog(

            selected =
                selectedBackground,

            onSelected = {
                    background ->

                selectedBackground =
                    background

                val foreground =
                    foregroundPhoto

                if (foreground != null) {

                    processedPhoto =
                        PassportPhotoLogic
                            .createBackgroundResult(
                                foreground,
                                background
                            )
                }
            },

            onDismiss = {
                showBackgroundDialog = false
            }
        )
    }
}

/*
 * =============================================================
 * BACKGROUND DIALOG
 * =============================================================
 */

@Composable
private fun BackgroundEditDialog(
    selected: PassportBackground,
    onSelected: (PassportBackground) -> Unit,
    onDismiss: () -> Unit
) {

    AlertDialog(

        onDismissRequest =
            onDismiss,

        title = {

            Text(
                text =
                    "Edit Background",

                fontWeight =
                    FontWeight.ExtraBold
            )
        },

        text = {

            Column(
                verticalArrangement =
                    Arrangement.spacedBy(
                        14.dp
                    )
            ) {

                Text(
                    text =
                        "Choose a background"
                )

                PassportBackground.entries
                    .forEach { option ->

                        val brush =
                            backgroundBrush(
                                option
                            )

                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clip(
                                        RoundedCornerShape(
                                            14.dp
                                        )
                                    )
                                    .background(
                                        if (
                                            option ==
                                            selected
                                        )
                                            MaterialTheme
                                                .colorScheme
                                                .primaryContainer
                                        else
                                            MaterialTheme
                                                .colorScheme
                                                .surfaceVariant
                                    )
                                    .padding(
                                        10.dp
                                    ),

                            verticalAlignment =
                                Alignment.CenterVertically
                        ) {

                            Box(
                                modifier =
                                    Modifier
                                        .size(42.dp)
                                        .clip(
                                            RoundedCornerShape(
                                                12.dp
                                            )
                                        )
                                        .background(
                                            brush
                                        )
                            )

                            Spacer(
                                Modifier.size(
                                    12.dp
                                )
                            )

                            Text(
                                text =
                                    option.title,

                                modifier =
                                    Modifier
                                        .weight(1f),

                                fontWeight =
                                    if (
                                        option ==
                                        selected
                                    )
                                        FontWeight.Bold
                                    else
                                        FontWeight.Normal
                            )

                            TextButton(
                                onClick = {
                                    onSelected(
                                        option
                                    )
                                }
                            ) {

                                Text(
                                    text =
                                        if (
                                            option ==
                                            selected
                                        )
                                            "SELECTED"
                                        else
                                            "USE"
                                )
                            }
                        }
                    }
            }
        },

        confirmButton = {

            TextButton(
                onClick =
                    onDismiss
            ) {

                Text("DONE")
            }
        }
    )
}

/*
 * =============================================================
 * BACKGROUND BRUSH
 * =============================================================
 */

private fun backgroundBrush(
    background: PassportBackground
): Brush {

    return if (
        background.endColor != null
    ) {

        Brush.linearGradient(
            listOf(
                Color(background.startColor),
                Color(background.endColor)
            )
        )

    } else {

        Brush.linearGradient(
            listOf(
                Color(background.startColor),
                Color(background.startColor)
            )
        )
    }
}

/*
 * =============================================================
 * DIRECT WHATSAPP SHARE
 * =============================================================
 */

private fun shareDirectToWhatsApp(
    context: Context,
    bitmap: Bitmap
) {

    val uri =
        PassportPhotoLogic
            .saveBitmapToGallery(
                context,
                bitmap,
                "Passport_WhatsApp_${System.currentTimeMillis()}.png"
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
            Intent.ACTION_SEND
        ).apply {

            type =
                "image/png"

            putExtra(
                Intent.EXTRA_STREAM,
                uri
            )

            setPackage(
                "com.whatsapp"
            )

            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }

    try {

        context.startActivity(
            intent
        )

    } catch (_: Exception) {

        Toast.makeText(
            context,
            "WhatsApp is not installed",
            Toast.LENGTH_SHORT
        ).show()
    }
}

/*
 * =============================================================
 * PRINT TO NOCO PRINT
 * =============================================================
 *
 * Requested package:
 *
 * com.noco.print
 *
 * Uses ACTION_SEND so the image is passed to the print app.
 */
private fun sendToNocoPrint(
    context: Context,
    bitmap: Bitmap
) {

    val uri =
        PassportPhotoLogic
            .saveBitmapToGallery(
                context,
                bitmap,
                "Passport_Print_${System.currentTimeMillis()}.png"
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
            Intent.ACTION_SEND
        ).apply {

            type =
                "image/png"

            putExtra(
                Intent.EXTRA_STREAM,
                uri
            )

            setPackage(
                "com.noco.print"
            )

            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }

    try {

        context.startActivity(
            intent
        )

    } catch (_: Exception) {

        /*
         * Some versions of the app may use the
         * alternate package name.
         */
        val fallback =
            Intent(
                Intent.ACTION_SEND
            ).apply {

                type =
                    "image/png"

                putExtra(
                    Intent.EXTRA_STREAM,
                    uri
                )

                setPackage(
                    "com.nokoprint"
                )

                addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }

        try {

            context.startActivity(
                fallback
            )

        } catch (_: Exception) {

            Toast.makeText(
                context,
                "Noco Print app is not installed",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}