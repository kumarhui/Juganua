package cvam.dignity.juganua.features.backgroundremover

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight

@Composable
fun BackgroundPreview(
    bitmap: Bitmap?,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                MaterialTheme.colorScheme.surfaceVariant
            ),
        contentAlignment = Alignment.Center
    ) {

        if (bitmap != null) {

            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Selfie preview",
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(
                        bitmap.width.toFloat() /
                                bitmap.height.toFloat()
                    )
                    .clip(RoundedCornerShape(20.dp))
            )

        } else {

            Column(
                horizontalAlignment =
                    Alignment.CenterHorizontally,
                modifier = Modifier.padding(40.dp)
            ) {

                Icon(
                    imageVector = Icons.Default.Image,
                    contentDescription = null,
                    modifier = Modifier.size(52.dp)
                )

                Spacer(
                    modifier = Modifier.height(12.dp)
                )

                Text(
                    text = "Select a selfie",
                    style =
                        MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                Text(
                    text = "Choose an image from your gallery",
                    style =
                        MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
fun ProcessingOverlay() {

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Color.Black.copy(alpha = 0.55f),
                RoundedCornerShape(20.dp)
            )
            .padding(28.dp),
        contentAlignment = Alignment.Center
    ) {

        Column(
            horizontalAlignment =
                Alignment.CenterHorizontally
        ) {

            CircularProgressIndicator(
                modifier = Modifier.size(42.dp),
                color = Color.White
            )

            Spacer(
                modifier = Modifier.height(14.dp)
            )

            Text(
                text = "Removing background…",
                color = Color.White,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun BackgroundSelector(
    selected: BackgroundStyle,
    onSelected: (BackgroundStyle) -> Unit
) {

    Column {

        Text(
            text = "Background",
            style =
                MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(
            modifier = Modifier.height(12.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement =
                Arrangement.SpaceBetween
        ) {

            BackgroundStyle.entries.forEach { style ->

                BackgroundSwatch(
                    style = style,
                    selected = style == selected,
                    onClick = {
                        onSelected(style)
                    }
                )
            }
        }
    }
}

@Composable
private fun BackgroundSwatch(
    style: BackgroundStyle,
    selected: Boolean,
    onClick: () -> Unit
) {

    val brush =
        if (style.endColor != null) {

            Brush.linearGradient(
                colors = listOf(
                    Color(style.startColor),
                    Color(style.endColor)
                )
            )

        } else {

            Brush.linearGradient(
                colors = listOf(
                    Color(style.startColor),
                    Color(style.startColor)
                )
            )
        }

    Column(
        horizontalAlignment =
            Alignment.CenterHorizontally
    ) {

        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(brush)
                .then(
                    if (selected) {
                        Modifier.border(
                            BorderStroke(
                                3.dp,
                                MaterialTheme.colorScheme.primary
                            ),
                            CircleShape
                        )
                    } else {
                        Modifier
                    }
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {

            if (selected) {

                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = Color.Black,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Spacer(
            modifier = Modifier.height(5.dp)
        )

        Text(
            text = style.title,
            style =
                MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
fun BackgroundActionBar(
    onSave: () -> Unit,
    onPrint: () -> Unit,
    onShare: () -> Unit,
    onAddImage: () -> Unit,
    enabled: Boolean
) {

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement =
            Arrangement.SpaceEvenly,
        verticalAlignment =
            Alignment.CenterVertically
    ) {

        ActionButton(
            icon = Icons.Default.Save,
            label = "Save",
            enabled = enabled,
            onClick = onSave
        )

        ActionButton(
            icon = Icons.Default.Print,
            label = "Print",
            enabled = enabled,
            onClick = onPrint
        )

        ActionButton(
            icon = Icons.AutoMirrored.Filled.OpenInNew,
            label = "Open",
            enabled = enabled,
            onClick = onShare
        )

        ActionButton(
            icon = Icons.Default.AddPhotoAlternate,
            label = "More",
            enabled = true,
            onClick = onAddImage
        )
    }
}

@Composable
private fun ActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit
) {

    Column(
        horizontalAlignment =
            Alignment.CenterHorizontally
    ) {

        IconButton(
            onClick = onClick,
            enabled = enabled
        ) {

            Icon(
                imageVector = icon,
                contentDescription = label
            )
        }

        Text(
            text = label,
            style =
                MaterialTheme.typography.labelSmall
        )
    }
}