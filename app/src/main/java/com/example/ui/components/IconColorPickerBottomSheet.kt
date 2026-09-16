package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import com.example.ui.util.IconHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IconColorPickerBottomSheet(
    initialColorHex: String,
    initialIconName: String,
    onDismiss: () -> Unit,
    onSave: (colorHex: String, iconName: String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    
    val initialParsedColor = remember(initialColorHex) { IconHelper.parseColor(initialColorHex) }
    val initialHsl = remember(initialParsedColor) {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(initialParsedColor.toArgb(), hsl)
        hsl
    }
    
    var hue by remember { mutableFloatStateOf(initialHsl[0]) }
    var lightness by remember { mutableFloatStateOf(initialHsl[2]) }
    
    val currentSelectedColor = remember(hue, lightness) {
        Color(ColorUtils.HSLToColor(floatArrayOf(hue, 1f, lightness)))
    }
    
    val selectedColorHex = remember(currentSelectedColor) {
        val r = (currentSelectedColor.red * 255).toInt()
        val g = (currentSelectedColor.green * 255).toInt()
        val b = (currentSelectedColor.blue * 255).toInt()
        String.format("#%02X%02X%02X", r, g, b)
    }

    var selectedIcon by remember { mutableStateOf(initialIconName) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text("Выбрать цвет и иконку", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(16.dp))

            Text("Цвет категории", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(8.dp))
            
            val rainbowColors = remember {
                listOf(
                    Color.Red,
                    Color.Yellow,
                    Color.Green,
                    Color.Cyan,
                    Color.Blue,
                    Color.Magenta,
                    Color.Red
                )
            }

            val lightnessColors = remember(hue) {
                listOf(
                    Color.Black,
                    Color(ColorUtils.HSLToColor(floatArrayOf(hue, 1f, 0.5f))),
                    Color.White
                )
            }

            // Hue slider
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp)
                        .height(24.dp)
                        .clip(CircleShape)
                        .background(Brush.horizontalGradient(rainbowColors))
                )
                Slider(
                    value = hue,
                    onValueChange = { hue = it },
                    valueRange = 0f..360f,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(ColorUtils.HSLToColor(floatArrayOf(hue, 1f, 0.5f))),
                        activeTrackColor = Color.Transparent,
                        inactiveTrackColor = Color.Transparent
                    )
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Lightness slider
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp)
                        .height(24.dp)
                        .clip(CircleShape)
                        .background(Brush.horizontalGradient(lightnessColors))
                )
                Slider(
                    value = lightness,
                    onValueChange = { lightness = it },
                    valueRange = 0f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = currentSelectedColor,
                        activeTrackColor = Color.Transparent,
                        inactiveTrackColor = Color.Transparent
                    )
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text("Иконка категории", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(8.dp))
            
            LazyVerticalGrid(
                columns = GridCells.Adaptive(56.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.heightIn(max = 300.dp)
            ) {
                items(IconHelper.allIconKeys) { iconKey ->
                    val isSelected = selectedIcon == iconKey
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .clickable { selectedIcon = iconKey },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = IconHelper.getIconByName(iconKey),
                            contentDescription = iconKey,
                            tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { onSave(selectedColorHex, selectedIcon) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Готово")
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
