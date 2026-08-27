package cvam.dignity.juganua.features.rpli

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun RpliResultCard(result: CalculationResult, expanded: Boolean, onToggle: () -> Unit, onShare: () -> Unit) {
    val cardBackground = Color(0xFF1E293B)
    val accentColor = Color(0xFF818CF8)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        shadowElevation = 12.dp,
        color = cardBackground
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("ESTIMATED PREMIUM", color = Color.White.copy(0.6f), style = MaterialTheme.typography.labelMedium)
                    // FIXED: Now references local Logic object
                    Text(RpliCalculatorLogic.formatInr(result.finalPremium), style = MaterialTheme.typography.displaySmall, color = Color.White, fontWeight = FontWeight.Black)
                }
                IconButton(
                    onClick = onShare,
                    modifier = Modifier.background(Color.White.copy(0.1f), CircleShape)
                ) { Icon(Icons.Default.Share, null, tint = Color.White) }
            }

            Spacer(Modifier.height(24.dp))

            Surface(color = Color.White.copy(0.05f), shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("Maturity Amount", color = Color.White.copy(0.6f), style = MaterialTheme.typography.labelSmall)
                        Text(RpliCalculatorLogic.formatInr(result.finalMaturityAmount), color = accentColor, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                    Surface(color = accentColor.copy(0.2f), shape = CircleShape) {
                        Text("${"%.1f".format(result.roi)}% ROI", Modifier.padding(horizontal = 12.dp, vertical = 6.dp), color = accentColor, fontWeight = FontWeight.Black, fontSize = 12.sp)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            TextButton(
                onClick = onToggle,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.textButtonColors(contentColor = accentColor)
            ) {
                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
                Spacer(Modifier.width(8.dp))
                Text(if (expanded) "HIDE BREAKDOWN" else "VIEW BREAKDOWN", fontWeight = FontWeight.Bold)
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(animationSpec = tween(500)) + fadeIn()
            ) {
                Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    HorizontalDivider(color = Color.White.copy(0.1f))
                    DetailRow("Entry / Maturity Age", "${result.entryAge} / ${result.maturityAge} Yrs")
                    DetailRow("Base Premium", RpliCalculatorLogic.formatInr(result.basePremiumPerPeriod))
                    DetailRow("Sum Assured Rebate", "- ${RpliCalculatorLogic.formatInr(result.rebatePerPeriod)}")
                    DetailRow("Payable (${result.frequency})", RpliCalculatorLogic.formatInr(result.finalPremium))
                    HorizontalDivider(color = Color.White.copy(0.05f))
                    DetailRow("Total Invested", RpliCalculatorLogic.formatInr(result.totalPremiumOverTerm))
                    DetailRow("Total Bonus Accrued", RpliCalculatorLogic.formatInr(result.totalBonus))
                    DetailRow("Net Profit", RpliCalculatorLogic.formatInr(result.netGain))
                }
            }
        }
    }
}

@Composable
fun NumericField(value: String, onValueChange: (String) -> Unit, label: String, maxLength: Int = 10, prefix: String? = null) {
    var isFocused by remember { mutableStateOf(false) }
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        Box(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant.copy(0.4f))
                .border(1.dp, if (isFocused) MaterialTheme.colorScheme.primary else Color.Transparent, RoundedCornerShape(12.dp)).padding(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                prefix?.let { Text(it, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 4.dp)) }
                BasicTextField(
                    value = value,
                    onValueChange = { if (it.length <= maxLength) onValueChange(it) },
                    textStyle = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    modifier = Modifier.onFocusChanged { focusState ->
                        if (focusState.isFocused && !isFocused) onValueChange("")
                        isFocused = focusState.isFocused
                    }
                )
            }
        }
    }
}

@Composable
fun FrequencyToggle(selected: String, onSelect: (String) -> Unit, options: Map<String, String>) {
    Row(Modifier.fillMaxWidth().clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant.copy(0.5f)).padding(4.dp)) {
        options.forEach { (key, label) ->
            val isSel = key == selected
            Box(Modifier.weight(1f).height(40.dp).clip(CircleShape).background(if (isSel) MaterialTheme.colorScheme.primary else Color.Transparent).clickable { onSelect(key) }, contentAlignment = Alignment.Center) {
                Text(label, fontWeight = FontWeight.Bold, color = if (isSel) Color.White else MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
fun InputGroup(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(title, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.outline, style = MaterialTheme.typography.labelLarge)
        }
        content()
    }
}

@Composable
fun DetailRow(l: String, v: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(l, color = Color.White.copy(0.6f), style = MaterialTheme.typography.bodySmall)
        Text(v, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
    }
}