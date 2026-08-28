package cvam.dignity.juganua.features.passportphoto

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun BackgroundRemovalPreview(
    original: Bitmap,
    result: Bitmap?,
    removeBackground: Boolean,
    processing: Boolean,
    background: PassportBackground,
    onToggleBackground: (Boolean) -> Unit,
    onBackgroundSelected: (PassportBackground) -> Unit,
    onSave: () -> Unit,
    onContinue: () -> Unit
) {

    Column(
        modifier =
            Modifier.fillMaxWidth(),

        verticalArrangement =
            Arrangement.spacedBy(16.dp)
    ) {

        Text(
            text = "PHOTO PREVIEW",
            fontWeight =
                FontWeight.ExtraBold,
            fontSize = 13.sp,
            color = Color.Gray
        )

        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(360.dp)
                    .clip(
                        RoundedCornerShape(24.dp)
                    )
                    .background(
                        MaterialTheme
                            .colorScheme
                            .surfaceVariant
                    ),

            contentAlignment =
                Alignment.Center
        ) {

            Image(
                bitmap =
                    (
                            result ?: original
                            ).asImageBitmap(),

                contentDescription =
                    "Passport photo",

                modifier =
                    Modifier
                        .fillMaxHeight()
                        .aspectRatio(0.75f)
                        .clip(
                            RoundedCornerShape(16.dp)
                        ),

                contentScale =
                    ContentScale.Fit
            )

            if (processing) {

                Box(
                    modifier =
                        Modifier.fillMaxSize()
                            .background(
                                Color.Black.copy(
                                    alpha = 0.45f
                                )
                            ),

                    contentAlignment =
                        Alignment.Center
                ) {

                    Column(
                        horizontalAlignment =
                            Alignment.CenterHorizontally
                    ) {

                        CircularProgressIndicator(
                            color =
                                Color.White
                        )

                        Spacer(
                            Modifier.height(12.dp)
                        )

                        Text(
                            text =
                                "Removing background…",

                            color =
                                Color.White,

                            fontWeight =
                                FontWeight.Bold
                        )
                    }
                }
            }
        }

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
                    Arrangement.spacedBy(14.dp)
            ) {

                Row(
                    modifier =
                        Modifier.fillMaxWidth(),

                    verticalAlignment =
                        Alignment.CenterVertically,

                    horizontalArrangement =
                        Arrangement.SpaceBetween
                ) {

                    Column(
                        modifier =
                            Modifier.weight(1f)
                    ) {

                        Text(
                            text =
                                "Remove Background",

                            fontWeight =
                                FontWeight.Bold
                        )

                        Text(
                            text =
                                if (removeBackground)
                                    "ML Kit enabled"
                                else
                                    "Original cropped photo",

                            style =
                                MaterialTheme
                                    .typography
                                    .bodySmall,

                            color =
                                Color.Gray
                        )
                    }

                    Switch(
                        checked =
                            removeBackground,

                        onCheckedChange =
                            onToggleBackground,

                        enabled =
                            !processing
                    )
                }

                if (removeBackground) {

                    Text(
                        text =
                            "Background",

                        fontWeight =
                            FontWeight.Bold
                    )

                    Row(
                        modifier =
                            Modifier.fillMaxWidth(),

                        horizontalArrangement =
                            Arrangement.SpaceBetween
                    ) {

                        PassportBackground.entries
                            .forEach { option ->

                                BackgroundCircle(
                                    option = option,

                                    selected =
                                        option ==
                                                background,

                                    onClick = {
                                        onBackgroundSelected(
                                            option
                                        )
                                    }
                                )
                            }
                    }
                }
            }
        }

        Row(
            modifier =
                Modifier.fillMaxWidth(),

            horizontalArrangement =
                Arrangement.spacedBy(12.dp)
        ) {

            OutlinedButton(
                onClick = onSave,

                enabled =
                    !processing &&
                            result != null,

                modifier =
                    Modifier.size(58.dp),

                contentPadding =
                    PaddingValues(0.dp)
            ) {

                Icon(
                    imageVector =
                        Icons.Default.Save,

                    contentDescription =
                        "Save to Gallery"
                )
            }

            Button(
                onClick = onContinue,

                enabled =
                    !processing &&
                            result != null,

                modifier =
                    Modifier
                        .weight(1f)
                        .height(58.dp),

                shape =
                    RoundedCornerShape(16.dp)
            ) {

                Text(
                    text =
                        "CONTINUE TO A6",

                    fontWeight =
                        FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun BackgroundCircle(
    option: PassportBackground,
    selected: Boolean,
    onClick: () -> Unit
) {

    val brush =
        if (option.endColor != null) {

            Brush.linearGradient(
                listOf(
                    Color(option.startColor),
                    Color(option.endColor)
                )
            )

        } else {

            Brush.linearGradient(
                listOf(
                    Color(option.startColor),
                    Color(option.startColor)
                )
            )
        }

    Column(
        horizontalAlignment =
            Alignment.CenterHorizontally
    ) {

        Box(
            modifier =
                Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(brush)
                    .then(
                        if (selected) {

                            Modifier.border(
                                width = 3.dp,
                                color =
                                    MaterialTheme
                                        .colorScheme
                                        .primary,
                                shape =
                                    CircleShape
                            )

                        } else {

                            Modifier
                        }
                    )
                    .clickable(
                        onClick = onClick
                    )
        )

        Spacer(
            Modifier.height(4.dp)
        )

        Text(
            text =
                option.title,

            fontSize =
                8.sp
        )
    }
}

/**
 * New layout UI.
 *
 * There are NO empty row cards.
 *
 * Slider positions:
 *
 * 1  2  3  4  5  6
 *
 * Position 1 is default.
 */
@Composable
fun PassportRowPositionSelector(
    selectedRow: Int,
    onRowSelected: (Int) -> Unit
) {

    Column(
        modifier =
            Modifier.fillMaxWidth(),

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
                        "PHOTO POSITION",

                    fontWeight =
                        FontWeight.ExtraBold,

                    fontSize =
                        12.sp
                )

                Text(
                    text =
                        "Choose where the 6-photo row goes",

                    style =
                        MaterialTheme
                            .typography
                            .bodySmall,

                    color =
                        MaterialTheme
                            .colorScheme
                            .onSurfaceVariant
                )
            }

            Surface(
                shape =
                    RoundedCornerShape(10.dp),

                color =
                    MaterialTheme
                        .colorScheme
                        .primaryContainer
            ) {

                Text(
                    text =
                        "Row ${selectedRow + 1}",

                    modifier =
                        Modifier.padding(
                            horizontal = 12.dp,
                            vertical = 6.dp
                        ),

                    fontWeight =
                        FontWeight.Bold,

                    fontSize =
                        12.sp
                )
            }
        }

        Slider(
            value =
                selectedRow.toFloat(),

            onValueChange = { value ->

                val row =
                    value
                        .toInt()
                        .coerceIn(0, 5)

                onRowSelected(row)
            },

            valueRange =
                0f..5f,

            steps = 4,

            modifier =
                Modifier.fillMaxWidth()
        )

        Row(
            modifier =
                Modifier.fillMaxWidth(),

            horizontalArrangement =
                Arrangement.SpaceBetween
        ) {

            repeat(6) { index ->

                val selected =
                    index == selectedRow

                Surface(
                    modifier =
                        Modifier
                            .size(32.dp)
                            .clickable {

                                onRowSelected(
                                    index
                                )
                            },

                    shape =
                        CircleShape,

                    color =
                        if (selected)
                            MaterialTheme
                                .colorScheme
                                .primary
                        else
                            MaterialTheme
                                .colorScheme
                                .surfaceVariant
                ) {

                    Box(
                        contentAlignment =
                            Alignment.Center
                    ) {

                        Text(
                            text =
                                "${index + 1}",

                            color =
                                if (selected)
                                    MaterialTheme
                                        .colorScheme
                                        .onPrimary
                                else
                                    MaterialTheme
                                        .colorScheme
                                        .onSurfaceVariant,

                            fontWeight =
                                FontWeight.Bold,

                            fontSize =
                                11.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * Compact visual representation of the A4/A6 sheet.
 *
 * Only the selected row contains the six copies.
 */
@Composable
fun PassportSheetPreview(
    selectedRow: Int,
    bitmap: Bitmap?,
    paperSize: PhotoPaperSize
) {

    Column(
        modifier =
            Modifier.fillMaxWidth(),

        verticalArrangement =
            Arrangement.spacedBy(4.dp)
    ) {

        Text(
            text =
                if (
                    paperSize ==
                    PhotoPaperSize.A6
                )
                    "A6 PREVIEW"
                else
                    "A4 PREVIEW",

            fontWeight =
                FontWeight.ExtraBold,

            fontSize =
                12.sp
        )

        Card(
            modifier =
                Modifier.fillMaxWidth(),

            shape =
                RoundedCornerShape(20.dp)
        ) {

            Column(
                modifier =
                    Modifier.padding(12.dp)
            ) {

                if (bitmap != null) {

                    Image(
                        bitmap =
                            bitmap.asImageBitmap(),

                        contentDescription =
                            "A6 result",

                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(360.dp),

                        contentScale =
                            ContentScale.Fit
                    )

                } else {

                    Text(
                        text =
                            "No preview available",

                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(30.dp),

                        color =
                            Color.Gray
                    )
                }
            }
        }

        Text(
            text =
                "Photo row: ${selectedRow + 1}  •  6 copies",

            style =
                MaterialTheme
                    .typography
                    .bodySmall,

            color =
                MaterialTheme
                    .colorScheme
                    .onSurfaceVariant
        )
    }
}