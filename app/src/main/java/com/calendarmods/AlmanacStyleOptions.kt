package com.calendarmods

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import java.util.Locale

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun AlmanacStyleOptions(style: ThemeStyle, enabled: Boolean, onChange: (ThemeStyle) -> Unit) {
    val context = LocalContext.current
    val environment = LocalAlmanacEnvironment.current
    var editingStart by rememberSaveable { mutableStateOf<Boolean?>(null) }
    editingStart?.let { start ->
        val minute = if (start) style.darkStart else style.darkEnd
        val time = rememberTimePickerState(minute / 60, minute % 60, is24Hour = true)
        PredictiveDialog(onDismiss = { editingStart = null }) {
            Text(if (start) "开始时间" else "结束时间", style = MaterialTheme.typography.headlineSmall)
            TimeInput(time)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { editingStart = null }) { Text("取消") }
                TextButton(onClick = {
                    val value = time.hour * 60 + time.minute
                    onChange(if (start) style.copy(darkStart = value) else style.copy(darkEnd = value))
                    editingStart = null
                }) { Text("确定") }
            }
        }
    }
    Text("字体颜色", style = MaterialTheme.typography.titleMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AlmanacInk.entries.forEach { ink ->
            FilterChip(selected = style.ink == ink, onClick = { onChange(style.copy(ink = ink)) }, enabled = enabled,
                label = { Text(ink.title) }, modifier = Modifier.testTag("almanac-ink-${ink.name}"),
                leadingIcon = if (ink == AlmanacInk.DEFAULT) null else {
                    { Box(Modifier.size(14.dp).background(Color(ink.light), CircleShape)) }
                })
        }
    }
    StyleSwitch("节日特别版式", "节日换上花边与题签；时和主题同时展示节日和二十四节气插画。",
        style.festive, enabled, "almanac-festive") { onChange(style.copy(festive = it)) }
    HorizontalDivider()
    Text("夜间显示", style = MaterialTheme.typography.titleMedium)
    StyleSwitch("深色模式", "手动保持暗色纸面、浅色文字。关闭后按下面的自动设置切换。",
        style.dark, enabled, "almanac-dark") { onChange(style.copy(dark = it)) }
    StyleSwitch("定时深色模式", "按设备当前时间切换，浏览其他日期不影响定时。",
        style.scheduledDark, enabled, "almanac-schedule") { onChange(style.copy(scheduledDark = it)) }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf(true, false).forEach { start ->
            val minute = if (start) style.darkStart else style.darkEnd
            OutlinedButton(enabled = enabled && style.scheduledDark, modifier = Modifier.weight(1f)
                .testTag(if (start) "almanac-night-start" else "almanac-night-end"), onClick = {
                    editingStart = start
                }) { Text((if (start) "开始 " else "结束 ") + String.format(Locale.ROOT, "%02d:%02d", minute / 60, minute % 60)) }
        }
    }
    if (style.scheduledDark && style.darkStart == style.darkEnd) Text("开始与结束相同，定时暂不生效。", style = MaterialTheme.typography.bodySmall)
    StyleSwitch("环境光自适应", if (environment.lightAvailable)
        "持续暗 30 秒后开启；恢复明亮 10 秒后关闭。与定时同时开启时，满足任一条件即使用深色。"
        else "此设备没有可用的光线传感器，仍可使用手动或定时模式。",
        style.ambientDark, enabled && environment.lightAvailable, "almanac-ambient") { onChange(style.copy(ambientDark = it)) }
    environment.lux?.takeIf { style.ambientDark }?.let {
        Text("当前环境光：${it.toInt()} lx", style = MaterialTheme.typography.bodySmall)
    }
}
