@file:OptIn(ExperimentalLayoutApi::class)

package com.docvoice.app.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.docvoice.app.AppViewModel
import com.docvoice.app.JobState
import com.docvoice.app.OutFile
import com.docvoice.app.R
import com.docvoice.app.core.Extractors
import com.docvoice.app.core.TtsVoices
import com.docvoice.app.core.WhisperSize
import java.util.Locale

@Composable
fun DocVoiceApp(vm: AppViewModel) {
    Scaffold(
        containerColor = Pastel.Bg,
        bottomBar = {
            NavigationBar(containerColor = Pastel.Card, tonalElevation = 0.dp) {
                val colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Pastel.LavenderDeep,
                    selectedTextColor = Pastel.LavenderDeep,
                    indicatorColor = Pastel.LavenderSoft,
                    unselectedIconColor = Pastel.Muted,
                    unselectedTextColor = Pastel.Muted,
                )
                NavigationBarItem(
                    selected = vm.tab == 0, onClick = { vm.tab = 0 }, colors = colors,
                    icon = { Icon(Icons.Rounded.RecordVoiceOver, null) }, label = { Text("문서 → 음성") },
                )
                NavigationBarItem(
                    selected = vm.tab == 1, onClick = { vm.tab = 1 }, colors = colors,
                    icon = { Icon(Icons.Rounded.Description, null) }, label = { Text("음성 → 문서") },
                )
            }
        },
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Header()
            JobPanel(vm)
            if (vm.tab == 0) TtsScreen(vm) else SttScreen(vm)
            Text(
                "변환한 파일은 '다운로드/DocVoice' 폴더에 저장돼요.",
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
            )
        }
    }
}

@Composable
private fun Header() {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
        Image(
            painterResource(R.drawable.app_logo), null,
            Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)), contentScale = ContentScale.Fit,
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text("DocVoice", style = androidx.compose.material3.MaterialTheme.typography.headlineSmall)
            Text("문서와 음성을 서로 바꿔 드려요", style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
        }
    }
}

// ---- 문서 → 음성 -------------------------------------------------------------

@Composable
private fun TtsScreen(vm: AppViewModel) {
    val busy = vm.job.collectAsState().value is JobState.Running
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::pickDoc) }

    SectionCard("1. 문서 선택") {
        PickButton(
            if (vm.ttsFile == null) "문서 파일 고르기" else "다른 파일 고르기",
            Icons.Rounded.UploadFile,
        ) { picker.launch(arrayOf("*/*")) }
        FileLine(vm.ttsFile?.name, vm.ttsFile?.size)
        Text(
            Extractors.SUPPORTED.joinToString(" · "),
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
        )
    }

    SectionCard("2. 목소리") {
        Label("한국어 (남성)")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TtsVoices.KO.forEach { (label, id) -> Pill(vm.koVoice == id, label) { vm.koVoice = id; vm.save() } }
        }
        Label("영어 억양")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill(vm.accent == "us", "미국식") { vm.accent = "us"; vm.save() }
            Pill(vm.accent == "uk", "영국식") { vm.accent = "uk"; vm.save() }
        }
        Label("영어 목소리 (남성)")
        val list = if (vm.accent == "uk") TtsVoices.EN_UK else TtsVoices.EN_US
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            list.forEach { (label, id) ->
                Pill(vm.enVoice == id, label) {
                    if (vm.accent == "uk") vm.enVoiceUk = id else vm.enVoiceUs = id
                    vm.save()
                }
            }
        }
        Label("속도  ${String.format(Locale.US, "%.1f", vm.speed)}배")
        PastelSlider(vm.speed, 0.7f..1.5f, 7) { vm.speed = (Math.round(it * 10) / 10f); vm.save() }
    }

    PrimaryButton("MP3 만들기", enabled = vm.ttsFile != null && !busy, color = Pastel.Lavender) { vm.startTts() }
    Text(
        "Microsoft 온라인 음성을 사용해 인터넷 연결이 필요해요.",
        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(start = 4.dp),
    )
}

// ---- 음성 → 문서 -------------------------------------------------------------

@Composable
private fun SttScreen(vm: AppViewModel) {
    val busy = vm.job.collectAsState().value is JobState.Running
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::pickAudio) }

    SectionCard("1. 음성 파일 선택") {
        PickButton(
            if (vm.sttFile == null) "음성 파일 고르기" else "다른 파일 고르기",
            Icons.Rounded.GraphicEq,
        ) { picker.launch(arrayOf("audio/*", "video/*")) }
        FileLine(vm.sttFile?.name, vm.sttFile?.size)
        Text("mp3 · m4a · wav · ogg · flac · mp4 등", style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
    }

    SectionCard("2. 저장 형식") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            vm.formats.forEach { f ->
                Pill(vm.format == f, f.uppercase(Locale.ROOT)) { vm.format = f; vm.save() }
            }
        }
        Text(
            when (vm.format) {
                "xlsx" -> "한 문장이 한 행에 들어가요. (영어는 단어 3개 이상일 때만 한 문장)"
                "txt" -> "화자 · 발언 간격에 맞춰 줄을 바꿔요."
                else -> "화자가 바뀌거나 발언 간격이 길면 줄을 바꿔요."
            },
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
        )
    }

    SectionCard("3. 인식 설정") {
        Label("언어")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill(vm.language == "", "자동") { vm.language = ""; vm.save() }
            Pill(vm.language == "ko", "한국어") { vm.language = "ko"; vm.save() }
            Pill(vm.language == "en", "영어") { vm.language = "en"; vm.save() }
        }
        Label("정확도")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            WhisperSize.values().forEach { s -> Pill(vm.size == s, s.label) { vm.size = s; vm.save() } }
        }
        Text(
            "${vm.size.hint} · 처음 한 번만 모델을 내려받고 이후엔 오프라인으로 인식해요.",
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
        )
        SwitchRow("화자 구분", vm.diarize) { vm.diarize = it; vm.save() }
        if (vm.diarize) SwitchRow("문서에 화자 표시", vm.showSpeaker) { vm.showSpeaker = it; vm.save() }
        SwitchRow("문서에 시간 표시", vm.includeTime) { vm.includeTime = it; vm.save() }
        Label("줄바꿈 기준 발언 간격  ${String.format(Locale.US, "%.1f", vm.gap)}초")
        PastelSlider(vm.gap, 0.5f..4f, 6) { vm.gap = (Math.round(it * 10) / 10f); vm.save() }
    }

    PrimaryButton("문서로 변환", enabled = vm.sttFile != null && !busy, color = Pastel.Mint, textColor = Pastel.Ink) { vm.startStt() }
    Text(
        "휴대폰에서 직접 처리하므로 길이에 따라 시간이 걸려요. 화면을 꺼도 계속돼요.",
        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(start = 4.dp),
    )
}

// ---- 진행 / 결과 패널 --------------------------------------------------------

@Composable
private fun JobPanel(vm: AppViewModel) {
    val ctx = LocalContext.current
    when (val s = vm.job.collectAsState().value) {
        JobState.Idle -> {}
        is JobState.Running -> Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Pastel.LavenderSoft).padding(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(s.title, style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                Text(s.stage, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
                if (s.fraction == null) {
                    LinearProgressIndicator(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)), color = Pastel.Lavender, trackColor = Color.White)
                } else {
                    LinearProgressIndicator(
                        progress = { s.fraction },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                        color = Pastel.Lavender, trackColor = Color.White,
                    )
                }
                TextButton(onClick = vm::cancel) { Text("취소", color = Pastel.RoseDeep) }
            }
        }
        is JobState.Done -> Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Pastel.MintSoft).padding(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("✓ ${s.message}", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                s.files.forEach { f ->
                    Text(f.name, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SmallButton(if (f.mime.startsWith("audio")) "재생" else "열기") { openFile(ctx, f) }
                        SmallButton("공유") { shareFile(ctx, f) }
                    }
                }
                s.note?.let { Text(it, style = androidx.compose.material3.MaterialTheme.typography.bodySmall) }
                TextButton(onClick = vm::dismissResult) { Text("닫기", color = Pastel.Muted) }
            }
        }
        is JobState.Failed -> Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Pastel.RoseSoft).padding(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(s.message, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
                TextButton(onClick = vm::dismissResult) { Text("확인", color = Pastel.RoseDeep) }
            }
        }
    }
}

private fun openFile(ctx: Context, f: OutFile) {
    try {
        ctx.startActivity(
            Intent(Intent.ACTION_VIEW).setDataAndType(f.uri, f.mime)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(ctx, "이 파일을 열 수 있는 앱이 없어요. 다운로드/DocVoice 폴더를 확인하세요.", Toast.LENGTH_LONG).show()
    }
}

private fun shareFile(ctx: Context, f: OutFile) {
    val send = Intent(Intent.ACTION_SEND).setType(f.mime).putExtra(Intent.EXTRA_STREAM, f.uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    ctx.startActivity(Intent.createChooser(send, "공유").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

// ---- 공통 컴포넌트 -----------------------------------------------------------

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Pastel.Card)
            .border(1.dp, Pastel.Border, RoundedCornerShape(22.dp))
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title, style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
        content()
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun Pill(selected: Boolean, label: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(
        Modifier
            .clip(shape)
            .background(if (selected) Pastel.Lavender else Pastel.LavenderSoft)
            .then(if (selected) Modifier else Modifier.border(BorderStroke(1.dp, Pastel.Border), shape))
            .clickableNoRipple(onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
    ) {
        Text(label, color = if (selected) Color.White else Pastel.Ink, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable
private fun PickButton(text: String, icon: ImageVector, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.5.dp, Pastel.Lavender),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = Pastel.LavenderSoft, contentColor = Pastel.LavenderDeep),
        modifier = Modifier.fillMaxWidth().height(52.dp),
    ) {
        Icon(icon, null, Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SmallButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        colors = ButtonDefaults.buttonColors(containerColor = Pastel.Mint, contentColor = Pastel.Ink),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp, vertical = 6.dp),
    ) { Text(text, fontWeight = FontWeight.SemiBold) }
}

@Composable
private fun PrimaryButton(text: String, enabled: Boolean, color: Color, textColor: Color = Color.White, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = color, contentColor = textColor,
            disabledContainerColor = Pastel.Border, disabledContentColor = Pastel.Muted,
        ),
        modifier = Modifier.fillMaxWidth().height(56.dp),
    ) { Text(text, style = androidx.compose.material3.MaterialTheme.typography.labelLarge) }
}

@Composable
private fun FileLine(name: String?, size: Long?) {
    if (name == null) return
    val sz = if (size != null && size >= 0) "  ·  " + humanSize(size) else ""
    Text("$name$sz", style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, color = Pastel.Ink)
}

private fun humanSize(b: Long): String = when {
    b >= 1 shl 20 -> String.format(Locale.US, "%.1f MB", b / 1048576.0)
    b >= 1 shl 10 -> String.format(Locale.US, "%.0f KB", b / 1024.0)
    else -> "$b B"
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = androidx.compose.material3.MaterialTheme.typography.bodyLarge)
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = Pastel.Lavender, checkedThumbColor = Color.White,
                uncheckedTrackColor = Pastel.LavenderSoft, uncheckedThumbColor = Pastel.Muted,
                uncheckedBorderColor = Pastel.Border,
            ),
        )
    }
}

@Composable
private fun PastelSlider(value: Float, range: ClosedFloatingPointRange<Float>, steps: Int, onChange: (Float) -> Unit) {
    Slider(
        value = value, onValueChange = onChange, valueRange = range, steps = steps,
        colors = SliderDefaults.colors(
            thumbColor = Pastel.Lavender, activeTrackColor = Pastel.Lavender,
            inactiveTrackColor = Pastel.LavenderSoft, activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent,
        ),
    )
}
