package org.erthi.noor

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nexvary.hifzai.learning.*
import java.time.LocalDate

@Composable fun HomeScreen(onSettings: () -> Unit, model: NoorModel = viewModel()) {
    val state by model.state.collectAsStateWithLifecycle()
    val corpus = state.corpus
    val haptic = LocalHapticFeedback.current
    var search by rememberSaveable { mutableStateOf("") }
    var selecting by rememberSaveable { mutableStateOf(false) }
    var license by rememberSaveable { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    if (license) {
        var content by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(Unit) { content = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            listOf("NEXVARY-MIT.txt","TANZIL-NOTICE.txt","AMIRI-OFL.txt","Apache-2.0.txt").joinToString("\n\n") { name -> context.assets.open("licenses/$name").bufferedReader().use { it.readText() } }
        } }
        AlertDialog(onDismissRequest={license=false}, title={Text(stringResource(R.string.sources))},
            text={LazyColumn { item { Text(content ?: stringResource(R.string.loading)) } }},
            confirmButton={TextButton(onClick={license=false}) { Text(stringResource(R.string.back)) }})
    }
    LazyColumn(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal=20.dp), verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween) {
            Text(stringResource(R.string.app_name), style=MaterialTheme.typography.headlineMedium, modifier=Modifier.weight(1f).semantics { heading() })
            IconButton(onClick=onSettings) { Icon(Icons.Default.Settings,stringResource(R.string.settings)) }
        }; Text(stringResource(R.string.identity), style=MaterialTheme.typography.labelMedium) }
        item { Text(stringResource(R.string.review_notice), style=MaterialTheme.typography.bodySmall) }
        if (state.loading) item { CircularProgressIndicator(); Text(stringResource(R.string.loading)) }
        if (state.error) item { Text(stringResource(R.string.error)); Button(onClick=model::load) { Text(stringResource(R.string.retry)) } }
        if (corpus != null) {
            val session=state.session
            val verse=corpus.find(session.chapter,session.ayah)!!
            item { OutlinedButton(onClick={selecting=!selecting}, modifier=Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.chapter)+": "+corpus.info(session.chapter).arabicName)
            } }
            if (selecting) {
                item { OutlinedTextField(value=search,onValueChange={search=it},label={Text(stringResource(R.string.search))},modifier=Modifier.fillMaxWidth()) }
                items(corpus.surahs.filter { search.isBlank() || it.arabicName.contains(search) || it.number.toString()==search },key={it.number}) { surah ->
                    TextButton(onClick={model.select(surah.number);selecting=false;search=""},modifier=Modifier.fillMaxWidth()) { Text("${surah.number} · ${surah.arabicName}") }
                }
                val address=QuranSearchIndex.address(search)?.let { corpus.find(it.first,it.second) }
                val results=if(address!=null) listOf(address) else if(search.length>=2) corpus.searchIndex.search(search,20) else emptyList()
                items(results,key={ "verse-${it.id}" }) { found ->
                    TextButton(onClick={model.select(found.chapter,found.verse);selecting=false;search=""},modifier=Modifier.fillMaxWidth()) {
                        Column { Text(stringResource(R.string.verse_address,found.chapter,found.verse));Text(found.text,maxLines=2) }
                    }
                }
            }
            item { Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween) {
                OutlinedButton(onClick={model.move(-1)},enabled=session.ayah>1) { Text(stringResource(R.string.previous)) }
                Text(stringResource(R.string.verse_address,session.chapter,session.ayah),modifier=Modifier.weight(1f).padding(8.dp))
                OutlinedButton(onClick={model.move(1)},enabled=session.ayah<corpus.info(session.chapter).ayahCount) { Text(stringResource(R.string.next)) }
            } }
            item {
                val stageRes=when(session.stage) {
                    PracticeKind.READ->R.string.stage_read; PracticeKind.MASK_LIGHT->R.string.stage_light
                    PracticeKind.MASK_HEAVY->R.string.stage_heavy;PracticeKind.RECALL->R.string.stage_recall;PracticeKind.BRIDGE->R.string.stage_bridge
                }
                PageTitle(stringResource(stageRes))
                val concealed=RecallLadder.concealment(session.stage,session.hints,session.exposed)
                Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(20.dp)) {
                    // Both pixels and accessibility semantics receive only the presentation mask.
                    // The canonical record remains immutable. No hidden answer in contentDescription.
                    Text(VerseMask.display(verse.text,concealed,verse.id),fontFamily=FontFamily(Font(R.font.amiri_quran)),fontSize=30.sp,lineHeight=60.sp,
                        style=TextStyle(textDirection=TextDirection.Rtl),modifier=Modifier.fillMaxWidth())
                    if(session.stage==PracticeKind.BRIDGE) {
                        corpus.find(session.chapter,session.ayah+1)?.let { next ->
                            HorizontalDivider(Modifier.padding(vertical=12.dp))
                            Text(stringResource(R.string.verse_address,next.chapter,next.verse))
                            Text(VerseMask.display(next.text,concealed,next.id),fontFamily=FontFamily(Font(R.font.amiri_quran)),fontSize=30.sp,lineHeight=60.sp,
                                style=TextStyle(textDirection=TextDirection.Rtl))
                        }
                    }
                } }
            }
            item {
                Button(onClick={model.nextStage();haptic.performHapticFeedback(HapticFeedbackType.LongPress)},modifier=Modifier.fillMaxWidth()) { Text(stringResource(R.string.next_stage)) }
                if(session.stage!=PracticeKind.READ) {
                    Row { TextButton(onClick=model::hint) { Text(stringResource(R.string.hint)) };TextButton(onClick=model::reveal) { Text(stringResource(R.string.reveal)) } }
                    Text(stringResource(R.string.hint_count,session.hints))
                }
            }
            item {
                Text(stringResource(R.string.self_report))
                listOf(RecallGrade.AGAIN to R.string.again, RecallGrade.HARD to R.string.hard,RecallGrade.GOOD to R.string.good,RecallGrade.EASY to R.string.easy).forEach { (grade,label) ->
                    OutlinedButton(onClick={model.grade(grade);haptic.performHapticFeedback(HapticFeedbackType.LongPress)},
                        enabled=session.stage==PracticeKind.RECALL || session.stage==PracticeKind.BRIDGE, modifier=Modifier.fillMaxWidth()) { Text(stringResource(label)) }
                }
            }
            item {
                PageTitle(stringResource(R.string.progress))
                Text(stringResource(R.string.due_count,MemoryCoach.dueCount(state.cards.values,LocalDate.now().toEpochDay())))
                Text(stringResource(R.string.saved_count,state.cards.size),modifier=Modifier.semantics { liveRegion=LiveRegionMode.Polite })
                Text(stringResource(R.string.daily_target))
                Text(stringResource(R.string.goal_count,state.target))
                Slider(value=state.target.toFloat(),onValueChange={model.target(it.toInt())},valueRange=1f..20f,steps=18,
                    modifier=Modifier.semantics { contentDescription=context.getString(R.string.daily_target) })
                Button(onClick=model::dailyPractice,modifier=Modifier.fillMaxWidth()) { Text(stringResource(R.string.practice)) }
            }
            item { Text(stringResource(R.string.no_audio)); Text(stringResource(R.string.source_credit));
                TextButton(onClick={license=true}) { Text(stringResource(R.string.sources)) }; Spacer(Modifier.height(20.dp)) }
        }
    }
}
