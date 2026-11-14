package com.mindtrace.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.SentimentDissatisfied
import androidx.compose.material.icons.rounded.SentimentNeutral
import androidx.compose.material.icons.rounded.SentimentSatisfied
import androidx.compose.material.icons.rounded.SentimentVeryDissatisfied
import androidx.compose.material.icons.rounded.SentimentVerySatisfied
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import com.mindtrace.app.ui.i18n.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mindtrace.app.data.DateUtils
import com.mindtrace.app.ui.theme.MoodColors
import com.mindtrace.app.ui.theme.MoodLabels
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@Composable
fun WarmBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val colors = MaterialTheme.colorScheme
    Box(modifier = modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            colors.background,
                            colors.surfaceContainerLow,
                            colors.background
                        )
                    )
                )
        )
        // 主题色「极光」光斑：让背景有光影层次，半透明卡片才有可透出的质感。
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(colors.primary.copy(alpha = 0.32f), Color.Transparent),
                    center = Offset(w * 0.18f, h * 0.10f),
                    radius = w * 0.95f
                ),
                radius = w * 0.95f,
                center = Offset(w * 0.18f, h * 0.10f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(colors.tertiary.copy(alpha = 0.24f), Color.Transparent),
                    center = Offset(w * 0.95f, h * 0.36f),
                    radius = w * 0.9f
                ),
                radius = w * 0.9f,
                center = Offset(w * 0.95f, h * 0.36f)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(colors.secondary.copy(alpha = 0.20f), Color.Transparent),
                    center = Offset(w * 0.22f, h * 0.92f),
                    radius = w * 1.05f
                ),
                radius = w * 1.05f,
                center = Offset(w * 0.22f, h * 0.92f)
            )
        }
        content()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SoftCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = MaterialTheme.shapes.large
    // 毛玻璃卡片：用 Canvas 自绘圆角渐变 + 主题色外发光，避免系统 elevation
    // 阴影在部分 GPU（尤其模拟器软渲染）上被画成矩形色块。
    val primary = MaterialTheme.colorScheme.primary
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val glassTop = if (isDark) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.30f)
    val glassMid = containerColor.copy(alpha = if (isDark) 0.46f else 0.62f)
    val glassBottom = containerColor.copy(alpha = if (isDark) 0.32f else 0.46f)
    val borderColor = if (isDark) Color.White.copy(alpha = 0.15f)
        else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.60f)
    val corner = with(androidx.compose.ui.platform.LocalDensity.current) { 24.dp.toPx() }
    val base = Modifier
        .fillMaxWidth()
        .drawBehind {
            // 柔和外发光（分层圆角矩形，替代 elevation 阴影）；只向下扩散，避免光晕压过文字。
            for (i in 1..6) {
                drawRoundRect(
                    color = primary.copy(alpha = if (isDark) 0.010f else 0.008f),
                    topLeft = androidx.compose.ui.geometry.Offset(-i * 1.2f, i * 2.0f),
                    size = androidx.compose.ui.geometry.Size(
                        size.width + i * 2.4f,
                        size.height + i * 3.6f
                    ),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner + i * 1.8f)
                )
            }
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(glassTop, glassMid, glassBottom),
                    startY = 0f,
                    endY = size.height
                ),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner)
            )
            drawRoundRect(
                color = borderColor,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx()),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner)
            )
        }
    val clickable = when {
        onLongClick != null -> base.combinedClickable(
            onClick = onClick ?: {},
            onLongClick = onLongClick
        )
        onClick != null -> base.clickable(onClick = onClick)
        else -> base
    }
    Column(
        modifier = modifier.then(clickable).padding(contentPadding),
        horizontalAlignment = horizontalAlignment,
        content = content
    )
}

@Composable
fun SectionTitle(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    action: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            if (subtitle != null) {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        action?.invoke()
    }
}

@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Icons.Rounded.ArrowBack, contentDescription = "返回")
            }
            Spacer(Modifier.width(2.dp))
        } else {
            Spacer(Modifier.width(10.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        actions?.invoke()
    }
}

@Composable
fun AppMark(modifier: Modifier = Modifier, size: Int = 44) {
    Box(
        modifier = modifier
            .size(size.dp)
            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.Spa,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size((size * 0.55).dp)
        )
    }
}

@Composable
fun Pill(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text, style = MaterialTheme.typography.bodyMedium) },
        shape = MaterialTheme.shapes.small,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
            containerColor = MaterialTheme.colorScheme.surface,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = MaterialTheme.colorScheme.outlineVariant,
            selectedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
        ),
        modifier = modifier
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagFlow(
    tags: List<String>,
    selected: Set<String>,
    modifier: Modifier = Modifier,
    onToggle: (String) -> Unit
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        tags.forEach { tag ->
            Pill(text = tag, selected = tag in selected, onClick = { onToggle(tag) })
        }
    }
}

@Composable
fun MoodSelector(
    value: Int?,
    modifier: Modifier = Modifier,
    onChange: (Int) -> Unit
) {
    val icons = listOf(
        Icons.Rounded.SentimentVeryDissatisfied,
        Icons.Rounded.SentimentDissatisfied,
        Icons.Rounded.SentimentNeutral,
        Icons.Rounded.SentimentSatisfied,
        Icons.Rounded.SentimentVerySatisfied
    )
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        (1..5).forEach { mood ->
            val selected = value == mood
            val scale by animateFloatAsState(if (selected) 1.12f else 1f, label = "moodScale")
            val tint by animateColorAsState(
                if (selected) MoodColors[mood - 1] else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                label = "moodTint"
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .scale(scale)
                    .clickable { onChange(mood) }
                    .padding(horizontal = 4.dp, vertical = 4.dp)
            ) {
                Icon(
                    imageVector = icons[mood - 1],
                    contentDescription = MoodLabels[mood - 1],
                    tint = tint,
                    modifier = Modifier.size(38.dp)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = MoodLabels[mood - 1],
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) MaterialTheme.colorScheme.onBackground
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun IntensitySlider(
    label: String,
    value: Int?,
    modifier: Modifier = Modifier,
    lowLabel: String = "轻",
    highLabel: String = "重",
    unsetLabel: String = "未记录",
    onClear: (() -> Unit)? = null,
    onChange: (Int) -> Unit
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f)
            )
            if (value != null && onClear != null) {
                Text(
                    text = "清除",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clickable { onClear() }
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
            Text(
                text = value?.toString() ?: unsetLabel,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = (value ?: 0).toFloat(),
            onValueChange = { onChange(it.toInt().coerceIn(0, 10)) },
            valueRange = 0f..10f,
            steps = 9,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent
            )
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(lowLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("0 – 10", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(highLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun <T> SegmentedOptions(
    options: List<Pair<T, String>>,
    selected: T,
    modifier: Modifier = Modifier,
    onSelect: (T) -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEach { (value, label) ->
            val isSelected = value == selected
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSelect(value) },
                shape = RoundedCornerShape(11.dp),
                color = if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent,
                shadowElevation = if (isSelected) 1.dp else 0.dp
            ) {
                Text(
                    text = label,
                    modifier = Modifier.padding(vertical = 9.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun PrimaryActionButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp),
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
    }
}

@Composable
fun SecondaryActionButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 50.dp),
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
    }
}

@Composable
fun InfoBanner(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Rounded.Info,
    tint: Color = MaterialTheme.colorScheme.tertiary
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(tint.copy(alpha = 0.10f), MaterialTheme.shapes.medium)
            .padding(14.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(19.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun MetricTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    suffix: String = "",
    tint: Color = MaterialTheme.colorScheme.primary
) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f), MaterialTheme.shapes.medium)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                color = tint,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (suffix.isNotBlank()) {
                Text(
                    suffix,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(start = 2.dp, bottom = 3.dp)
                )
            }
        }
    }
}

@Composable
fun EmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Rounded.Spa
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f), modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String = "确定",
    dismissLabel: String = "取消",
    destructive: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    confirmLabel,
                    color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(dismissLabel) }
        }
    )
}

@Composable
fun DateField(
    label: String,
    isoDate: String,
    modifier: Modifier = Modifier,
    onDateChange: (String) -> Unit
) {
    var showPicker by remember { mutableStateOf(false) }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable { showPicker = true },
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(2.dp))
            Text(DateUtils.displayFullDate(isoDate), style = MaterialTheme.typography.bodyLarge)
        }
    }
    if (showPicker) {
        val english = com.mindtrace.app.ui.i18n.LocalAppLanguage.current.isEnglish
        val today = DateUtils.today()
        val selected = DateUtils.parse(isoDate) ?: today
        var pickerYear by remember(showPicker) { mutableIntStateOf(selected.year) }
        var pickerMonth by remember(showPicker) { mutableIntStateOf(selected.monthValue) }
        var picked by remember(showPicker) { mutableStateOf(DateUtils.iso(selected)) }
        AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text(if (english) "Select date" else "选择日期") },
            text = {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = {
                            if (pickerMonth == 1) { pickerYear -= 1; pickerMonth = 12 } else pickerMonth -= 1
                        }) { Icon(Icons.Rounded.ChevronLeft, contentDescription = "上个月") }
                        Text(
                            DateUtils.monthLabel(pickerYear, pickerMonth),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center
                        )
                        IconButton(onClick = {
                            if (pickerMonth == 12) { pickerYear += 1; pickerMonth = 1 } else pickerMonth += 1
                        }) { Icon(Icons.Rounded.ChevronRight, contentDescription = "下个月") }
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth()) {
                        DateUtils.weekdayHeaders().forEach { text ->
                            Text(
                                text,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    val first = java.time.LocalDate.of(pickerYear, pickerMonth, 1)
                    val cells = buildList<java.time.LocalDate?> {
                        repeat(first.dayOfWeek.value - 1) { add(null) }
                        for (day in 1..first.lengthOfMonth()) {
                            add(java.time.LocalDate.of(pickerYear, pickerMonth, day))
                        }
                        while (size % 7 != 0) add(null)
                    }
                    cells.chunked(7).forEach { week ->
                        Row(Modifier.fillMaxWidth()) {
                            week.forEach { date ->
                                Box(
                                    Modifier.weight(1f).aspectRatio(1f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (date != null) {
                                        val iso = DateUtils.iso(date)
                                        val future = date.isAfter(today)
                                        val isPicked = iso == picked
                                        Box(
                                            Modifier
                                                .fillMaxSize()
                                                .padding(2.dp)
                                                .background(
                                                    if (isPicked) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                                    RoundedCornerShape(10.dp)
                                                )
                                                .clickable(enabled = !future) { picked = iso },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                date.dayOfMonth.toString(),
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = when {
                                                    isPicked -> MaterialTheme.colorScheme.onPrimaryContainer
                                                    future -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                                    else -> MaterialTheme.colorScheme.onSurface
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { onDateChange(picked); showPicker = false }) {
                    Text(if (english) "OK" else "确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) {
                    Text(if (english) "Cancel" else "取消")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeField(
    label: String,
    time: String,
    modifier: Modifier = Modifier,
    onChange: (String) -> Unit
) {
    var showPicker by remember { mutableStateOf(false) }
    val parts = time.split(':')
    val initialHour = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: 20
    val initialMinute = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 30
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable { showPicker = true },
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(2.dp))
            Text(time, style = MaterialTheme.typography.bodyLarge)
        }
    }
    if (showPicker) {
        val state = androidx.compose.material3.rememberTimePickerState(
            initialHour = initialHour,
            initialMinute = initialMinute,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text(label) },
            text = {
                androidx.compose.material3.TimePicker(state = state)
            },
            confirmButton = {
                TextButton(onClick = {
                    onChange(DateUtils.timeLabel(state.hour, state.minute))
                    showPicker = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("取消") }
            }
        )
    }
}

@Composable
fun CheckRow(
    text: String,
    checked: Boolean,
    modifier: Modifier = Modifier,
    onToggle: (Boolean) -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = interaction,
                indication = null
            ) { onToggle(!checked) }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .background(
                    if (checked) MaterialTheme.colorScheme.primary else Color.Transparent,
                    CircleShape
                )
                .then(
                    if (!checked) Modifier.background(MaterialTheme.colorScheme.surface, CircleShape) else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            if (checked) {
                Icon(Icons.Rounded.Check, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(15.dp))
            } else {
                Box(
                    Modifier
                        .size(20.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun BottomSafeSpacer() {
    Spacer(
        Modifier
            .navigationBarsPadding()
            .height(12.dp)
    )
}
