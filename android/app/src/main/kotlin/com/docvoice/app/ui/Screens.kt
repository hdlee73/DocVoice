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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
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
import com.docvoice.app.core.TtsVoices
import com.docvoice.app.core.WhisperSize
import java.util.Locale

@Composable
fun DocVoiceApp(vm: AppViewModel) {
    Column(Modifier.fillMaxSize().background(Ios.Bg)) {
        Column(
            Modifier.weight(1f).statusBarsPadding().verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            when (vm.tab) {
                0 -> RecordScreen(vm)
                1 -> TtsScreen(vm)
                else -> SttScreen(vm)
            }
        }
        TabBar(vm.tab) { vm.tab = it }
    }
}

@Composable
private fun TabBar(selected: Int, onSelect: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().background(Color(0xFFF9F9F9))) {
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(Ios.Separator))
        Row(Modifier.fillMaxWidth().navigationBarsPadding().height(56.dp)) {
            TabItem(Icons.Rounded.Mic, "녹음", selected == 0) { onSelect(0) }
            TabItem(Icons.Rounded.VolumeUp, "문서 → 음성", selected == 1) { onSelect(1) }
            TabItem(Icons.Rounded.Description, "음성 → 문서", selected == 2) { onSelect(2) }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.TabItem(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    val c = if (selected) Ios.Blue else Ios.Secondary
    Column(
        Modifier.weight(1f).fillMaxSize().clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = c, modifier = Modifier.size(26.dp))
        Text(label, fontSize = 10.sp, color = c, fontWeight = FontWeight.Medium)
    }
}

// ---- 녹음 ----------------------------------------------------------------------

private fun clock(sec: Double): String {
    val s = sec.toInt()
    return if (s >= 3600) String.format(Locale.US, "%d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)
    else String.format(Locale.US, "%02d:%02d", s / 60, s % 60)
}

@Composable
private fun RecordScreen(vm: AppViewModel) {
    val ctx = LocalContext.current
    val rec by vm.rec.collectAsState()
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) vm.startRecording() else Toast.makeText(ctx, "마이크 권한이 필요해요.", Toast.LENGTH_SHORT).show()
    }
    fun start() {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) vm.startRecording()
        else permission.launch(Manifest.permission.RECORD_AUDIO)
    }

    LargeTitle("녹음")
    JobPanel(vm)

    val live = rec.phase == RecPhase.Recording || rec.phase == RecPhase.Paused
    val idle = rec.phase == RecPhase.Idle || rec.phase == RecPhase.Failed

    // 타이머 + 레벨
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Ios.Card).padding(vertical = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            clock(rec.seconds), fontSize = 54.sp, fontWeight = FontWeight.Light, color = Ios.Label,
            style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"),
        )
        Box(Modifier.height(48.dp).padding(top = 6.dp), contentAlignment = Alignment.Center) {
            LevelBars(rec.level, rec.phase == RecPhase.Recording)
        }
        Box(Modifier.height(24.dp), contentAlignment = Alignment.Center) {
            val msg = when (rec.phase) {
                RecPhase.Preparing -> rec.message ?: "준비 중"
                RecPhase.Recording -> if (rec.speaking) "듣는 중…" else "말씀하세요"
                RecPhase.Paused -> "일시정지됨"
                RecPhase.Finishing -> rec.message ?: "마무리 중"
                RecPhase.Stopped -> "녹음 완료"
                RecPhase.Failed -> rec.message ?: "오류"
                RecPhase.Idle -> "버튼을 눌러 녹음을 시작하세요"
            }
            Text(msg, fontSize = 15.sp, color = if (rec.phase == RecPhase.Failed) Ios.Red else Ios.Secondary)
        }
        if (rec.phase == RecPhase.Preparing || rec.phase == RecPhase.Finishing) {
            Gap(8)
            if (rec.fraction != null) {
                LinearProgressIndicator(
                    progress = { rec.fraction!! }, color = Ios.Blue, trackColor = Ios.Fill,
                    modifier = Modifier.padding(horizontal = 40.dp).fillMaxWidth().height(4.dp).clip(CircleShape),
                )
            } else {
                LinearProgressIndicator(color = Ios.Blue, trackColor = Ios.Fill, modifier = Modifier.padding(horizontal = 40.dp).fillMaxWidth().height(4.dp).clip(CircleShape))
            }
        }
        Gap(14)
        // 컨트롤
        Row(horizontalArrangement = Arrangement.spacedBy(36.dp), verticalAlignment = Alignment.CenterVertically) {
            if (live) {
                CircleButton(Ios.Fill, 56.dp, if (rec.phase == RecPhase.Paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause, Ios.Label) { vm.togglePause() }
                RecordButton(recording = true) { vm.stopRecording() }
                Box(Modifier.size(56.dp))
            } else if (rec.phase == RecPhase.Preparing || rec.phase == RecPhase.Finishing) {
                RecordButton(recording = true, enabled = rec.phase == RecPhase.Preparing) { vm.stopRecording() }
            } else {
                RecordButton(recording = false) { start() }
            }
        }
    }

    // 실시간 받아쓰기
    if (rec.phase != RecPhase.Idle || rec.segments.isNotEmpty()) {
        GroupHeader("실시간 받아쓰기")
        val scroll = rememberScrollState()
        LaunchedEffect(rec.segments.size) { scroll.animateScrollTo(scroll.maxValue) }
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Ios.Card)
                .heightIn(min = 120.dp, max = 340.dp).verticalScroll(scroll).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (rec.segments.isEmpty()) {
                Text(
                    if (live) "말씀을 마치면 잠시 뒤 여기에 글자가 나타나요." else "아직 인식된 내용이 없어요.",
                    fontSize = 15.sp, color = Ios.Secondary,
                )
            }
            rec.segments.forEach { seg ->
                Row {
                    Text(clock(seg.start), fontSize = 12.sp, color = Ios.Secondary, modifier = Modifier.width(46.dp).padding(top = 3.dp))
                    Text(seg.text, fontSize = 17.sp, color = Ios.Label, lineHeight = 24.sp, modifier = Modifier.weight(1f))
                }
            }
            if (rec.speaking) Text("…", fontSize = 17.sp, color = Ios.Tertiary)
        }
    }

    // 녹음 설정 (대기 중)
    if (idle) {
        GroupHeader("받아쓰기 설정")
        Group {
            row {
                Column {
                    Box(Modifier.padding(start = 16.dp, top = 12.dp)) { Text("언어", fontSize = 13.sp, color = Ios.Secondary) }
                    SegmentedRow(listOf("자동", "한국어", "영어"), listOf("", "ko", "en").indexOf(vm.language).coerceAtLeast(0)) {
                        vm.language = listOf("", "ko", "en")[it]; vm.save()
                    }
                }
            }
            row {
                Column {
                    Box(Modifier.padding(start = 16.dp, top = 12.dp)) { Text("정확도", fontSize = 13.sp, color = Ios.Secondary) }
                    SegmentedRow(WhisperSize.values().map { it.label }, vm.size.ordinal) { vm.size = WhisperSize.values()[it]; vm.save() }
                }
            }
        }
        GroupFooter("말이 끝나면 곧바로 글자로 바뀌어요. 실시간에는 '빠름'이 더 부드럽습니다. 처음 한 번만 음성 모델을 내려받아요.")
    }

    // 녹음 끝난 뒤: 문서로 내보내기
    if (rec.phase == RecPhase.Stopped) {
        GroupHeader("문서로 내보내기")
        val formats = listOf("docx", "pdf", "xlsx", "txt")
        Group {
            row { SegmentedRow(formats.map { it.uppercase(Locale.ROOT) }, formats.indexOf(vm.recFormat).coerceAtLeast(0)) { vm.recFormat = formats[it] } }
            row { ToggleRow("화자 구분", vm.recDiarize) { vm.recDiarize = it } }
            if (vm.recDiarize) row { ToggleRow("화자 표시", vm.showSpeaker) { vm.showSpeaker = it; vm.save() } }
            row { ToggleRow("시간 표시", vm.includeTime) { vm.includeTime = it; vm.save() } }
            row {
                SliderRow("줄바꿈 간격", String.format(Locale.US, "%.1f초", vm.gap), vm.gap, 0.5f..4f, 6) { vm.gap = Math.round(it * 10) / 10f; vm.save() }
            }
        }
        GroupFooter("화자가 바뀌거나 발언 사이가 이 간격보다 길면 줄을 바꿉니다.")
        FilledButton("문서로 내보내기", enabled = rec.segments.isNotEmpty()) { vm.exportRecording() }
        rec.wav?.let { w ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                TintedButton("녹음 파일 재생") { openFile(ctx, w) }
                TintedButton("공유") { shareFile(ctx, w) }
            }
            GroupFooter("녹음 원본은 다운로드/DocVoice 폴더에 저장됐어요.")
        }
        TextAction("새 녹음", Ios.Blue) { vm.newRecording() }
    }
}

@Composable
private fun LevelBars(level: Float, active: Boolean) {
    val history = remember { mutableStateListOf<Float>().apply { repeat(36) { add(0f) } } }
    LaunchedEffect(level, active) {
        history.removeAt(0)
        history.add(if (active) level else 0f)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        history.forEach { v ->
            Box(Modifier.width(3.dp).height((4 + 40 * v).dp).clip(CircleShape).background(if (active) Ios.Red.copy(alpha = 0.85f) else Ios.Tertiary))
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

@Composable
private fun CircleButton(bg: Color, size: androidx.compose.ui.unit.Dp, icon: ImageVector, tint: Color, onClick: () -> Unit) {
    Box(Modifier.size(size).clip(CircleShape).background(bg).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(28.dp))
    }
}

// ---- 문서 → 음성 -----------------------------------------------------------------

@Composable
private fun TtsScreen(vm: AppViewModel) {
    val busy = vm.job.collectAsState().value is JobState.Running
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::pickDoc) }

    LargeTitle("문서 → 음성")
    JobPanel(vm)

    GroupHeader("문서")
    Group {
        row {
            ValueRow(
                title = vm.ttsFile?.name ?: "파일 선택",
                value = vm.ttsFile?.size?.takeIf { it >= 0 }?.let(::humanSize) ?: "",
                onClick = { picker.launch(arrayOf("*/*")) },
                titleColor = if (vm.ttsFile == null) Ios.Blue else Ios.Label,
            )
        }
    }
    GroupFooter(Extractors.SUPPORTED.joinToString(" · "))

    GroupHeader("한국어 목소리")
    Group { TtsVoices.KO.forEach { (label, id) -> row { CheckRow(label, vm.koVoice == id) { vm.koVoice = id; vm.save() } } } }

    GroupHeader("영어")
    Group {
        row { SegmentedRow(listOf("미국식", "영국식"), if (vm.accent == "uk") 1 else 0) { vm.accent = if (it == 1) "uk" else "us"; vm.save() } }
        val list = if (vm.accent == "uk") TtsVoices.EN_UK else TtsVoices.EN_US
        list.forEach { (label, id) ->
            row {
                CheckRow(label, vm.enVoice == id) {
                    if (vm.accent == "uk") vm.enVoiceUk = id else vm.enVoiceUs = id
                    vm.save()
                }
            }
        }
    }

    GroupHeader("속도")
    Group {
        row { SliderRow("읽기 속도", String.format(Locale.US, "%.1f×", vm.speed), vm.speed, 0.7f..1.5f, 7) { vm.speed = Math.round(it * 10) / 10f; vm.save() } }
    }
    GroupFooter("Microsoft 신경망 음성을 사용하므로 인터넷 연결이 필요해요.")

    FilledButton("MP3 만들기", enabled = vm.ttsFile != null && !busy) { vm.startTts() }
}

// ---- 음성 → 문서 -----------------------------------------------------------------

@Composable
private fun SttScreen(vm: AppViewModel) {
    val busy = vm.job.collectAsState().value is JobState.Running
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::pickAudio) }

    LargeTitle("음성 → 문서")
    JobPanel(vm)

    GroupHeader("음성 파일")
    Group {
        row {
            ValueRow(
                title = vm.sttFile?.name ?: "파일 선택",
                value = vm.sttFile?.size?.takeIf { it >= 0 }?.let(::humanSize) ?: "",
                onClick = { picker.launch(arrayOf("audio/*", "video/*")) },
                titleColor = if (vm.sttFile == null) Ios.Blue else Ios.Label,
            )
        }
    }
    GroupFooter("mp3 · m4a · wav · ogg · flac · mp4 등")

    GroupHeader("저장 형식")
    Group {
        row { SegmentedRow(vm.formats.map { it.uppercase(Locale.ROOT) }, vm.formats.indexOf(vm.format).coerceAtLeast(0)) { vm.format = vm.formats[it]; vm.save() } }
    }
    GroupFooter(
        when (vm.format) {
            "xlsx" -> "한 문장이 한 행에 들어가요. 영어는 단어 3개 이상일 때만 한 문장으로 쳐요."
            else -> "화자가 바뀌거나 발언 간격이 길면 줄을 바꿔요."
        }
    )

    GroupHeader("인식")
    Group {
        row {
            Column {
                Box(Modifier.padding(start = 16.dp, top = 12.dp)) { Text("언어", fontSize = 13.sp, color = Ios.Secondary) }
                SegmentedRow(listOf("자동", "한국어", "영어"), listOf("", "ko", "en").indexOf(vm.language).coerceAtLeast(0)) {
                    vm.language = listOf("", "ko", "en")[it]; vm.save()
                }
            }
        }
        row {
            Column {
                Box(Modifier.padding(start = 16.dp, top = 12.dp)) { Text("정확도", fontSize = 13.sp, color = Ios.Secondary) }
                SegmentedRow(WhisperSize.values().map { it.label }, vm.size.ordinal) { vm.size = WhisperSize.values()[it]; vm.save() }
            }
        }
        row { ToggleRow("화자 구분", vm.diarize) { vm.diarize = it; vm.save() } }
        if (vm.diarize) row { ToggleRow("화자 표시", vm.showSpeaker) { vm.showSpeaker = it; vm.save() } }
        row { ToggleRow("시간 표시", vm.includeTime) { vm.includeTime = it; vm.save() } }
        row { SliderRow("줄바꿈 간격", String.format(Locale.US, "%.1f초", vm.gap), vm.gap, 0.5f..4f, 6) { vm.gap = Math.round(it * 10) / 10f; vm.save() } }
    }
    GroupFooter("${vm.size.hint} · 처음 한 번만 모델을 내려받고, 이후에는 오프라인으로 인식해요. 길이에 따라 시간이 걸리며 화면을 꺼도 계속돼요.")

    FilledButton("문서로 변환", enabled = vm.sttFile != null && !busy) { vm.startStt() }
}

// ---- 진행 / 결과 ------------------------------------------------------------------

@Composable
private fun JobPanel(vm: AppViewModel) {
    val ctx = LocalContext.current
    when (val s = vm.job.collectAsState().value) {
        JobState.Idle -> {}
        is JobState.Running -> Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Ios.Card).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(s.title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Ios.Label)
            Text(s.stage, fontSize = 15.sp, color = Ios.Secondary)
            if (s.fraction == null) {
                LinearProgressIndicator(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape), color = Ios.Blue, trackColor = Ios.Fill)
            } else {
                LinearProgressIndicator(
                    progress = { s.fraction }, modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
                    color = Ios.Blue, trackColor = Ios.Fill,
                )
            }
            Text("취소", fontSize = 15.sp, color = Ios.Red, modifier = Modifier.clickable { vm.cancel() })
        }
        is JobState.Done -> Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Ios.Card).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(s.message, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Ios.Green)
            s.files.forEach { f ->
                Text(f.name, fontSize = 15.sp, color = Ios.Label)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TintedButton(if (f.mime.startsWith("audio")) "재생" else "열기") { openFile(ctx, f) }
                    TintedButton("공유") { shareFile(ctx, f) }
                }
            }
            s.note?.let { Text(it, fontSize = 13.sp, color = Ios.Secondary) }
            Text("닫기", fontSize = 15.sp, color = Ios.Secondary, modifier = Modifier.clickable { vm.dismissResult() })
        }
        is JobState.Failed -> Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Ios.Card).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(s.message, fontSize = 15.sp, color = Ios.Red)
            Text("확인", fontSize = 15.sp, color = Ios.Blue, modifier = Modifier.clickable { vm.dismissResult() })
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

private fun humanSize(b: Long): String = when {
    b >= 1 shl 20 -> String.format(Locale.US, "%.1f MB", b / 1048576.0)
    b >= 1 shl 10 -> String.format(Locale.US, "%.0f KB", b / 1024.0)
    else -> "$b B"
}
