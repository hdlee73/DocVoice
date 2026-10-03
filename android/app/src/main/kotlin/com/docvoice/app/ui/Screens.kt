package com.docvoice.app.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Subject
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.docvoice.app.AppViewModel
import com.docvoice.app.JobState
import com.docvoice.app.OutFile
import com.docvoice.app.RecPhase
import com.docvoice.app.core.Extractors
import com.docvoice.app.core.LiveLang
import com.docvoice.app.core.TtsVoices
import com.docvoice.app.core.WhisperSize
import java.util.Locale

@Composable
fun DocVoiceApp(vm: AppViewModel) {
    Column(Modifier.fillMaxSize().background(Ios.Bg)) {
        Box(Modifier.weight(1f).fillMaxWidth().statusBarsPadding()) {
            if (vm.tab == 0) {
                Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { RecordScreen(vm) }
            } else {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) { if (vm.tab == 1) TtsScreen(vm) else SttScreen(vm) }
            }
        }
        TabBar(vm.tab) { vm.tab = it }
    }
}

@Composable
private fun TabBar(selected: Int, onSelect: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().background(Color(0xFFF9F9F9))) {
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(Ios.Separator))
        Row(Modifier.fillMaxWidth().navigationBarsPadding().height(54.dp)) {
            TabItem(Icons.Rounded.Mic, "녹음", selected == 0) { onSelect(0) }
            TabItem(Icons.Rounded.VolumeUp, "읽어주기", selected == 1) { onSelect(1) }
            TabItem(Icons.Rounded.Description, "받아쓰기", selected == 2) { onSelect(2) }
        }
    }
}

@Composable
private fun RowScope.TabItem(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    val c = if (selected) Ios.Blue else Ios.Secondary
    Column(
        Modifier.weight(1f).fillMaxSize().clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = c, modifier = Modifier.size(26.dp))
        Text(label, fontSize = 10.sp, color = c, fontWeight = FontWeight.Medium)
    }
}

// ---- 설정 시트 --------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSheet(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Ios.Bg,
    ) {
        Column(
            Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp).navigationBarsPadding().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) { content() }
    }
}

// ---- 녹음 ---------------------------------------------------------------------------

private fun clock(sec: Double): String {
    val s = sec.toInt()
    return if (s >= 3600) String.format(Locale.US, "%d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)
    else String.format(Locale.US, "%02d:%02d", s / 60, s % 60)
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.RecordScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    val rec by vm.rec.collectAsState()
    var sheet by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) vm.startRecording() else Toast.makeText(ctx, "마이크 권한이 필요해요.", Toast.LENGTH_SHORT).show()
    }
    fun start() {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) vm.startRecording()
        else permission.launch(Manifest.permission.RECORD_AUDIO)
    }
    val live = rec.phase == RecPhase.Recording || rec.phase == RecPhase.Paused
    val busy = rec.phase == RecPhase.Preparing || rec.phase == RecPhase.Finishing
    val stopped = rec.phase == RecPhase.Stopped
    val idle = rec.phase == RecPhase.Idle || rec.phase == RecPhase.Failed

    TitleBar("녹음") {
        if (idle) LangPill(vm)
        if (stopped) RoundIcon(Icons.Rounded.Tune, Ios.Blue) { sheet = true }
    }
    JobPanel(vm)

    // 받아쓰기 영역 (화면의 대부분)
    val scroll = rememberScrollState()
    LaunchedEffect(rec.segments.size, rec.partial) { scroll.animateScrollTo(scroll.maxValue) }
    Box(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Ios.Card)) {
        if (rec.segments.isEmpty() && rec.partial.isEmpty()) {
            Icon(
                if (busy) Icons.Rounded.GraphicEq else Icons.Rounded.Mic, null, tint = Ios.Fill,
                modifier = Modifier.align(Alignment.Center).size(88.dp),
            )
        }
        Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            rec.segments.forEach { seg ->
                Column {
                    Text(clock(seg.start), fontSize = 11.sp, color = Ios.Tertiary)
                    Text(seg.text, fontSize = 24.sp, color = Ios.Label, lineHeight = 33.sp, fontWeight = FontWeight.Medium)
                }
            }
            if (rec.partial.isNotEmpty()) Text(rec.partial, fontSize = 24.sp, color = Ios.Blue, lineHeight = 33.sp, fontWeight = FontWeight.Medium)
        }
    }

    // 하단 컨트롤
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (busy) {
            val fr = rec.fraction
            if (fr != null) LinearProgressIndicator(progress = { fr }, color = Ios.Blue, trackColor = Ios.Fill, modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape))
            else LinearProgressIndicator(color = Ios.Blue, trackColor = Ios.Fill, modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape))
            rec.message?.let { Text(it, fontSize = 13.sp, color = Ios.Secondary) }
        }
        if (rec.phase == RecPhase.Failed) Text(rec.message ?: "", fontSize = 13.sp, color = Ios.Red)
        if (!stopped) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(clock(rec.seconds), fontSize = 28.sp, fontWeight = FontWeight.Light, color = Ios.Label, style = TextStyle(fontFeatureSettings = "tnum"))
                if (live) LevelBars(rec.level, rec.phase == RecPhase.Recording)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(40.dp), verticalAlignment = Alignment.CenterVertically) {
                if (live) {
                    CircleIcon(if (rec.phase == RecPhase.Paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause, Ios.Fill, Ios.Label, 56.dp) { vm.togglePause() }
                    RecordButton(true, true) { vm.stopRecording() }
                    Spacer(Modifier.size(56.dp))
                } else RecordButton(busy, rec.phase == RecPhase.Preparing || !busy) { if (busy) vm.stopRecording() else start() }
            }
        } else {
            val formats = listOf("docx", "pdf", "xlsx", "txt")
            Segmented(formats.map { it.uppercase(Locale.ROOT) }, formats.indexOf(vm.recFormat).coerceAtLeast(0)) { vm.recFormat = formats[it] }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                rec.wav?.let { w ->
                    CircleIcon(Icons.Rounded.PlayArrow, Ios.Fill, Ios.Label, 52.dp) { openFile(ctx, w) }
                    CircleIcon(Icons.Rounded.IosShare, Ios.Fill, Ios.Label, 52.dp) { shareFile(ctx, w) }
                }
                CircleIcon(Icons.Rounded.Add, Ios.Fill, Ios.Red, 52.dp) { vm.newRecording() }
                FilledButton("내보내기", Icons.Rounded.Description, enabled = rec.segments.isNotEmpty(), modifier = Modifier.weight(1f)) { vm.exportRecording() }
            }
        }
    }

    if (sheet) SettingsSheet({ sheet = false }) {
        TitleBar("내보내기 설정", "• 정밀 재인식: 녹음이 끝난 뒤 더 정확한 모델로 처음부터 다시 받아써서 문서에 담아요. (시간이 더 걸려요)\n• 화자 구분: 누가 말했는지 나눠 줄을 바꿔요.\n• 줄바꿈 간격: 발언 사이가 이 시간보다 길면 줄을 바꿔요.")
        Group {
            row { ToggleRow(Icons.Rounded.AutoAwesome, Ios.Purple, "정밀 재인식", vm.recRefine) { vm.recRefine = it } }
            if (vm.recRefine) row { SegmentedRow(WhisperSize.values().map { it.label }, vm.size.ordinal) { vm.size = WhisperSize.values()[it]; vm.save() } }
            row { ToggleRow(Icons.Rounded.People, Ios.Orange, "화자 구분", vm.recDiarize) { vm.recDiarize = it } }
            if (vm.recDiarize) row { ToggleRow(Icons.Rounded.RecordVoiceOver, Ios.Teal, "화자 표시", vm.showSpeaker) { vm.showSpeaker = it; vm.save() } }
            row { ToggleRow(Icons.Rounded.Schedule, Ios.Blue, "시간 표시", vm.includeTime) { vm.includeTime = it; vm.save() } }
            row { SliderRow(Icons.Rounded.Subject, Ios.Indigo, String.format(Locale.US, "%.1f초", vm.gap), vm.gap, 0.5f..4f, 6) { vm.gap = Math.round(it * 10) / 10f; vm.save() } }
        }
    }
}

/** 상단의 언어 선택 알약 (영어 / 한국어) */
@Composable
private fun LangPill(vm: AppViewModel) {
    val opts = LiveLang.values().map { it.label to it.key }
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.clip(RoundedCornerShape(50)).background(Ios.Blue.copy(alpha = 0.12f)).clickable { open = true }
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Translate, null, tint = Ios.Blue, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(LiveLang.of(vm.recLang).label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Ios.Blue)
        }
        androidx.compose.material3.DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = Ios.Card) {
            opts.forEach { (label, key) ->
                androidx.compose.material3.DropdownMenuItem(text = { Text(label) }, onClick = { open = false; vm.recLang = key; vm.save() })
            }
        }
    }
}

@Composable
private fun LevelBars(level: Float, active: Boolean) {
    val history = remember { mutableStateListOf<Float>().apply { repeat(22) { add(0f) } } }
    LaunchedEffect(level, active) {
        history.removeAt(0)
        history.add(if (active) level else 0f)
    }
    Row(Modifier.height(32.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        history.forEach { v ->
            Box(Modifier.width(3.dp).height((4 + 26 * v).dp).clip(CircleShape).background(if (active) Ios.Red.copy(alpha = 0.85f) else Ios.Tertiary))
        }
    }
}

@Composable
private fun RecordButton(recording: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        Modifier.size(72.dp).border(3.dp, Ios.Tertiary, CircleShape).clip(CircleShape).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (recording) Box(Modifier.size(28.dp).clip(RoundedCornerShape(7.dp)).background(Ios.Red))
        else Box(Modifier.size(58.dp).clip(CircleShape).background(Ios.Red))
    }
}

// ---- 읽어주기 (문서 → 음성) ---------------------------------------------------------------

@Composable
private fun TtsScreen(vm: AppViewModel) {
    val busy = vm.job.collectAsState().value is JobState.Running
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::pickDoc) }

    TitleBar(
        "읽어주기",
        "문서를 자연스러운 남성 목소리 MP3로 만들어요.\n\n지원 형식: ${Extractors.SUPPORTED.joinToString(", ")}\n\nMicrosoft 신경망 음성을 쓰므로 인터넷 연결이 필요해요. 결과는 다운로드/DocVoice 폴더에 저장돼요.",
    )
    JobPanel(vm)

    Group {
        row {
            ValueRow(
                Icons.Rounded.UploadFile, Ios.Blue, vm.ttsFile?.name ?: "문서 선택", vm.ttsFile?.size?.takeIf { it >= 0 }?.let(::humanSize),
                titleColor = if (vm.ttsFile == null) Ios.Blue else Ios.Label,
            ) { picker.launch(arrayOf("*/*")) }
        }
    }
    Group {
        row {
            MenuRow(Icons.Rounded.RecordVoiceOver, Ios.Orange, "한국어", TtsVoices.KO.entries.firstOrNull { it.value == vm.koVoice }?.key ?: "", TtsVoices.KO.map { it.key to it.value }) {
                vm.koVoice = it; vm.save()
            }
        }
        row { SegmentedRow(listOf("미국식", "영국식"), if (vm.accent == "uk") 1 else 0) { vm.accent = if (it == 1) "uk" else "us"; vm.save() } }
        row {
            val list = if (vm.accent == "uk") TtsVoices.EN_UK else TtsVoices.EN_US
            MenuRow(Icons.Rounded.Translate, Ios.Green, "English", list.entries.firstOrNull { it.value == vm.enVoice }?.key ?: "", list.map { it.key to it.value }) {
                if (vm.accent == "uk") vm.enVoiceUk = it else vm.enVoiceUs = it
                vm.save()
            }
        }
        row { SliderRow(Icons.Rounded.Speed, Ios.Purple, String.format(Locale.US, "%.1f×", vm.speed), vm.speed, 0.7f..1.5f, 7) { vm.speed = Math.round(it * 10) / 10f; vm.save() } }
    }
    FilledButton("MP3 만들기", Icons.Rounded.VolumeUp, enabled = vm.ttsFile != null && !busy) { vm.startTts() }
}

// ---- 받아쓰기 (음성 → 문서) ---------------------------------------------------------------

@Composable
private fun SttScreen(vm: AppViewModel) {
    val busy = vm.job.collectAsState().value is JobState.Running
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::pickAudio) }
    var sheet by remember { mutableStateOf(false) }

    TitleBar(
        "받아쓰기",
        "음성 파일(mp3, m4a, wav, mp4 등)을 문서로 바꿔요.\n\n• XLSX: 한 문장이 한 행에 들어가요. 영어는 단어 3개 이상일 때만 한 문장으로 쳐요.\n• DOCX · PDF · TXT: 화자가 바뀌거나 발언 간격이 길면 줄을 바꿔요.\n\n인식은 휴대폰 안에서 이뤄져요. 처음 한 번만 모델을 내려받고, 화면을 꺼도 계속돼요.",
    ) { RoundIcon(Icons.Rounded.Tune, Ios.Blue) { sheet = true } }
    JobPanel(vm)

    Group {
        row {
            ValueRow(
                Icons.Rounded.GraphicEq, Ios.Red, vm.sttFile?.name ?: "음성 선택", vm.sttFile?.size?.takeIf { it >= 0 }?.let(::humanSize),
                titleColor = if (vm.sttFile == null) Ios.Blue else Ios.Label,
            ) { picker.launch(arrayOf("audio/*", "video/*")) }
        }
    }
    Group {
        row { SegmentedRow(vm.formats.map { it.uppercase(Locale.ROOT) }, vm.formats.indexOf(vm.format).coerceAtLeast(0)) { vm.format = vm.formats[it]; vm.save() } }
        row { ToggleRow(Icons.Rounded.People, Ios.Orange, "화자 구분", vm.diarize) { vm.diarize = it; vm.save() } }
        row { ToggleRow(Icons.Rounded.Schedule, Ios.Blue, "시간 표시", vm.includeTime) { vm.includeTime = it; vm.save() } }
    }
    FilledButton("문서로 변환", Icons.Rounded.Description, enabled = vm.sttFile != null && !busy) { vm.startStt() }

    if (sheet) SettingsSheet({ sheet = false }) {
        TitleBar("인식 설정", "정확도 '정확'은 모델이 커서 처음 내려받는 데 시간이 걸리고 인식도 더 오래 걸려요. 줄바꿈 간격은 발언 사이가 이 시간보다 길면 줄을 바꾸는 기준이에요.")
        Group {
            row { SegmentedRow(listOf("자동", "한국어", "영어"), listOf("", "ko", "en").indexOf(vm.language).coerceAtLeast(0)) { vm.language = listOf("", "ko", "en")[it]; vm.save() } }
            row { SegmentedRow(WhisperSize.values().map { it.label }, vm.size.ordinal) { vm.size = WhisperSize.values()[it]; vm.save() } }
            if (vm.diarize) row { ToggleRow(Icons.Rounded.RecordVoiceOver, Ios.Teal, "화자 표시", vm.showSpeaker) { vm.showSpeaker = it; vm.save() } }
            row { SliderRow(Icons.Rounded.Subject, Ios.Indigo, String.format(Locale.US, "%.1f초", vm.gap), vm.gap, 0.5f..4f, 6) { vm.gap = Math.round(it * 10) / 10f; vm.save() } }
        }
    }
}

// ---- 진행 / 결과 ----------------------------------------------------------------------------

@Composable
private fun JobPanel(vm: AppViewModel) {
    val ctx = LocalContext.current
    when (val s = vm.job.collectAsState().value) {
        JobState.Idle -> {}
        is JobState.Running -> Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Ios.Card).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(s.stage, fontSize = 15.sp, color = Ios.Label, modifier = Modifier.weight(1f))
                RoundIcon(Icons.Rounded.Close, Ios.Secondary, 20.dp) { vm.cancel() }
            }
            if (s.fraction == null) LinearProgressIndicator(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape), color = Ios.Blue, trackColor = Ios.Fill)
            else LinearProgressIndicator(progress = { s.fraction }, modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape), color = Ios.Blue, trackColor = Ios.Fill)
        }
        is JobState.Done -> Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Ios.Card).padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 6.dp),
        ) {
            s.files.forEach { f ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.CheckCircle, null, tint = Ios.Green, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(f.name, fontSize = 15.sp, color = Ios.Label, modifier = Modifier.weight(1f), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    RoundIcon(if (f.mime.startsWith("audio")) Icons.Rounded.PlayArrow else Icons.Rounded.OpenInNew, Ios.Blue) { openFile(ctx, f) }
                    RoundIcon(Icons.Rounded.IosShare, Ios.Blue) { shareFile(ctx, f) }
                    RoundIcon(Icons.Rounded.Close, Ios.Secondary, 20.dp) { vm.dismissResult() }
                }
            }
            s.note?.let { Text(it, fontSize = 12.sp, color = Ios.Secondary, modifier = Modifier.padding(bottom = 6.dp, end = 8.dp)) }
        }
        is JobState.Failed -> Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Ios.Card).padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.ErrorOutline, null, tint = Ios.Red, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Text(s.message, fontSize = 14.sp, color = Ios.Label, modifier = Modifier.weight(1f))
            RoundIcon(Icons.Rounded.Close, Ios.Secondary, 20.dp) { vm.dismissResult() }
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
        Toast.makeText(ctx, "열 수 있는 앱이 없어요. 다운로드/DocVoice 폴더를 확인하세요.", Toast.LENGTH_LONG).show()
    }
}

private fun shareFile(ctx: Context, f: OutFile) {
    val send = Intent(Intent.ACTION_SEND).setType(f.mime).putExtra(Intent.EXTRA_STREAM, f.uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    ctx.startActivity(Intent.createChooser(send, "공유").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

private fun humanSize(b: Long): String = when {
    b >= 1 shl 20 -> String.format(Locale.US, "%.1f MB", b / 1048576.0)
    b >= 1 shl 10 -> String.format(Locale.US, "%.0f KB", b / 1024.0)
    else -> "$b B"
}
