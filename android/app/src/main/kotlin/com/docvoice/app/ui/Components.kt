package com.docvoice.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LargeTitle(text: String) {
    Text(
        text, fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Ios.Label,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 4.dp),
    )
}

@Composable
fun GroupHeader(text: String) {
    Text(
        text, fontSize = 13.sp, color = Ios.Secondary, letterSpacing = 0.2.sp,
        modifier = Modifier.padding(start = 16.dp, top = 10.dp, bottom = 0.dp),
    )
}

@Composable
fun GroupFooter(text: String) {
    Text(text, fontSize = 13.sp, color = Ios.Secondary, modifier = Modifier.padding(horizontal = 16.dp), lineHeight = 17.sp)
}

class GroupScope {
    val rows = ArrayList<@Composable () -> Unit>()
    fun row(content: @Composable () -> Unit) { rows.add(content) }
}

/** iOS 의 inset grouped 목록 */
@Composable
fun Group(content: GroupScope.() -> Unit) {
    val scope = GroupScope().apply(content)
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Ios.Card)) {
        scope.rows.forEachIndexed { i, r ->
            r()
            if (i < scope.rows.lastIndex) Box(Modifier.padding(start = 16.dp).fillMaxWidth().height(0.5.dp).background(Ios.Separator))
        }
    }
}

@Composable
fun RowShell(onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 46.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

@Composable
fun ValueRow(title: String, value: String?, onClick: (() -> Unit)? = null, valueColor: Color = Ios.Secondary, titleColor: Color = Ios.Label) {
    RowShell(onClick) {
        Text(title, fontSize = 17.sp, color = titleColor, modifier = Modifier.weight(1f))
        if (value != null) Text(value, fontSize = 17.sp, color = valueColor, maxLines = 1)
    }
}

@Composable
fun CheckRow(title: String, checked: Boolean, onClick: () -> Unit) {
    RowShell(onClick) {
        Text(title, fontSize = 17.sp, color = Ios.Label, modifier = Modifier.weight(1f))
        if (checked) Icon(Icons.Rounded.Check, null, tint = Ios.Blue, modifier = Modifier.size(22.dp))
    }
}

@Composable
fun ToggleRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    RowShell {
        Text(title, fontSize = 17.sp, color = Ios.Label, modifier = Modifier.weight(1f))
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = Ios.Green, checkedThumbColor = Color.White, checkedBorderColor = Ios.Green,
                uncheckedTrackColor = Color(0xFFE9E9EA), uncheckedThumbColor = Color.White, uncheckedBorderColor = Color(0xFFE9E9EA),
            ),
        )
    }
}

@Composable
fun SegmentedRow(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) { Segmented(options, selected, onSelect) }
}

@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val outer = RoundedCornerShape(9.dp)
    val inner = RoundedCornerShape(7.dp)
    Row(Modifier.fillMaxWidth().clip(outer).background(Ios.SegmentTrack).padding(2.dp)) {
        options.forEachIndexed { i, label ->
            val sel = i == selected
            Box(
                Modifier.weight(1f).height(32.dp)
                    .then(if (sel) Modifier.shadow(1.5.dp, inner).background(Color.White, inner) else Modifier)
                    .clip(inner)
                    .clickable { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) {
                Text(label, fontSize = 13.sp, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Medium, color = Ios.Label, textAlign = TextAlign.Center, maxLines = 1)
            }
        }
    }
}

@Composable
fun SliderRow(title: String, valueText: String, value: Float, range: ClosedFloatingPointRange<Float>, steps: Int, onChange: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text(title, fontSize = 17.sp, color = Ios.Label, modifier = Modifier.weight(1f))
            Text(valueText, fontSize = 17.sp, color = Ios.Secondary)
        }
        Slider(
            value = value, onValueChange = onChange, valueRange = range, steps = steps,
            thumb = { Box(Modifier.size(26.dp).shadow(3.dp, CircleShape).background(Color.White, CircleShape)) },
            colors = SliderDefaults.colors(
                activeTrackColor = Ios.Blue, inactiveTrackColor = Ios.Fill,
                activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent,
            ),
        )
    }
}

@Composable
fun FilledButton(text: String, enabled: Boolean = true, color: Color = Ios.Blue, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(14.dp))
            .background(if (enabled) color else Ios.Fill)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = if (enabled) Color.White else Ios.Tertiary)
    }
}

@Composable
fun TintedButton(text: String, color: Color = Ios.Blue, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.12f)).clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 8.dp),
    ) { Text(text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = color) }
}

@Composable
fun TextAction(text: String, color: Color = Ios.Blue, onClick: () -> Unit) {
    Text(
        text, fontSize = 17.sp, color = color, textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
    )
}

@Composable
fun Gap(h: Int = 12) = Spacer(Modifier.height(h.dp))

@Composable
fun HGap(w: Int = 8) = Spacer(Modifier.width(w.dp))

