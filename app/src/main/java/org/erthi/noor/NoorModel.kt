package org.erthi.noor

import android.app.Application
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nexvary.hifzai.learning.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.time.LocalDate

/** Source loading and persistence are kept outside composables. */
class NoorRepository(app: Application) {
    private val prefs = app.getSharedPreferences("noor-learning-v1", 0)
    fun loadCorpus() = QuranCorpus.load(app)
    private val app = app
    internal fun importLegacy(uri: Uri, corpus: QuranCorpus): Int {
        val file = File(app.filesDir, "legacy-progress.csv")
        val isVerse: (Int, Int) -> Boolean = { chapter, ayah -> corpus.find(chapter, ayah) != null }
        val imported = app.contentResolver.openInputStream(uri)?.use {
            LegacyProgressCsv.read(it, isVerse)
        } ?: throw IOException("Could not open progress document")
        val previous = if (file.exists()) file.inputStream().use {
            LegacyProgressCsv.read(it, isVerse)
        } else emptyMap()
        val merged = LegacyProgressCsv.merge(previous, imported)
        if (merged != previous) {
            val pending = File.createTempFile("legacy-progress-", ".tmp", app.cacheDir)
            try {
                FileOutputStream(pending).use {
                    it.write(LegacyProgressCsv.encode(merged))
                    it.fd.sync()
                }
                Files.move(pending.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING)
            } finally {
                pending.delete()
            }
        }
        return imported.count { (id, _) -> merged[id] != previous[id] }
    }

    internal fun legacyVerseCount(corpus: QuranCorpus): Int {
        val path = File(app.filesDir, "legacy-progress.csv")
        if (!path.exists()) return 0
        return path.inputStream().use { input ->
            LegacyProgressCsv.read(input) { chapter, ayah -> corpus.find(chapter, ayah) != null }.size
        }
    }

    fun session(): PracticeSession = runCatching {
        val o = JSONObject(prefs.getString("session", "{}")!!)
        PracticeSession(o.getInt("chapter"), o.getInt("ayah"), PracticeKind.valueOf(o.getString("stage")),
            o.getInt("hints"), o.getBoolean("exposed"), DifficultyTag.NONE)
    }.getOrDefault(PracticeSession(1,1,PracticeKind.READ,0,false,DifficultyTag.NONE))
    fun target() = prefs.getInt("target", 2).coerceIn(1,20)
    fun cards(): Map<Int, ReviewCard> {
        val result = mutableMapOf<Int,ReviewCard>()
        val root = runCatching { JSONObject(prefs.getString("cards", "{}")!!) }.getOrDefault(JSONObject())
        root.keys().forEach { k -> runCatching {
            val o = root.getJSONObject(k)
            val c = ReviewCard(k.toInt(),o.getInt("repetitions"),o.getDouble("ease"),o.getInt("interval"),o.getLong("due"),o.getInt("lapses"))
            require(c.repetitions >= 0 && c.intervalDays >= 0 && c.ease in 1.3..3.0 && c.lapses >= 0)
            result[c.verseId] = c
        } }
        return result
    }
    fun save(session: PracticeSession, target: Int, cards: Map<Int, ReviewCard>): Boolean {
        val s = JSONObject().put("chapter",session.chapter).put("ayah",session.ayah).put("stage",session.stage.name)
            .put("hints",session.hints).put("exposed",session.exposed)
        val c = JSONObject()
        cards.forEach { (id, card) -> c.put(id.toString(), JSONObject().put("repetitions",card.repetitions)
            .put("ease",card.ease).put("interval",card.intervalDays).put("due",card.dueEpochDay).put("lapses",card.lapses)) }
        return prefs.edit().putString("session",s.toString()).putInt("target",target).putString("cards",c.toString()).commit()
    }
}

data class NoorState(val corpus: QuranCorpus? = null, val loading: Boolean = true, val error: Boolean = false,
    val session: PracticeSession = PracticeSession(1,1,PracticeKind.READ,0,false,DifficultyTag.NONE),
    val target: Int = 2, val cards: Map<Int,ReviewCard> = emptyMap())

enum class LegacyImportStatus { BUSY, SAVED, UNCHANGED, FAILED }

class NoorModel(app: Application) : AndroidViewModel(app) {
    private val repository = NoorRepository(app)
    private val _state = MutableStateFlow(NoorState())
    val state = _state.asStateFlow()
    private val _legacyCount = MutableStateFlow<Int?>(null)
    val legacyCount = _legacyCount.asStateFlow()
    private val _importStatus = MutableStateFlow<LegacyImportStatus?>(null)
    val importStatus = _importStatus.asStateFlow()

    internal fun importLegacy(uri: Uri) {
        val corpus = _state.value.corpus ?: return
        if (_importStatus.value == LegacyImportStatus.BUSY) return
        _importStatus.value = LegacyImportStatus.BUSY
        viewModelScope.launch {
            try {
                val (changed, total) = withContext(Dispatchers.IO) {
                    val changed = repository.importLegacy(uri, corpus)
                    changed to repository.legacyVerseCount(corpus)
                }
                _legacyCount.value = total
                _importStatus.value = if (changed > 0) LegacyImportStatus.SAVED else LegacyImportStatus.UNCHANGED
            } catch (_: IOException) {
                _importStatus.value = LegacyImportStatus.FAILED
            } catch (_: IllegalArgumentException) {
                _importStatus.value = LegacyImportStatus.FAILED
            } catch (_: SecurityException) {
                _importStatus.value = LegacyImportStatus.FAILED
            }
        }
    }
    // Serializes all writes; a later attempt cannot be overwritten by an older snapshot.
    private val writes = kotlinx.coroutines.sync.Mutex()
    init { load() }
    fun load() { viewModelScope.launch {
        _state.value = _state.value.copy(loading = true, error = false)
        runCatching { withContext(Dispatchers.IO) {
            val corpus = repository.loadCorpus()
            val session = repository.session().takeIf { corpus.find(it.chapter,it.ayah) != null }
                ?: PracticeSession(1,1,PracticeKind.READ,0,false,DifficultyTag.NONE)
            NoorState(corpus,false,false,session,repository.target(),repository.cards().filterKeys { id -> corpus.find(id/1000,id%1000) != null })
        } }.onSuccess { loaded ->
            _state.value = loaded
            _legacyCount.value = withContext(Dispatchers.IO) {
                try {
                    loaded.corpus?.let(repository::legacyVerseCount)
                } catch (_: IOException) {
                    null
                } catch (_: IllegalArgumentException) {
                    null
                }
            }
        }.onFailure { _state.value = NoorState(loading=false,error=true) }
    } }
    private fun update(transform: (NoorState) -> NoorState) {
        if (_state.value.corpus == null) return
        val next = transform(_state.value)
        _state.value = next
        viewModelScope.launch {
            writes.lock()
            try {
                val saved = withContext(Dispatchers.IO) { repository.save(next.session,next.target,next.cards) }
                if (!saved) _state.value = _state.value.copy(error=true)
            } finally { writes.unlock() }
        }
    }
    fun select(chapter: Int, ayah: Int = 1) = update { s ->
        if (s.corpus?.find(chapter,ayah) == null) s else s.copy(session=PracticeSession(chapter,ayah,PracticeKind.READ,0,false,DifficultyTag.NONE))
    }
    fun move(delta: Int) { val s=_state.value; select(s.session.chapter,s.session.ayah+delta) }
    fun target(value: Int) = update { it.copy(target=value.coerceIn(1,20)) }
    fun nextStage() = update { it.copy(session=it.session.changeStage(RecallLadder.next(it.session.stage))) }
    fun reveal() = update { it.copy(session=it.session.copy(exposed=true)) }
    fun hint() = update { it.copy(session=it.session.copy(hints=(it.session.hints+1).coerceAtMost(100))) }
    fun grade(reported: RecallGrade) = update { s ->
        if (s.session.stage != PracticeKind.RECALL && s.session.stage != PracticeKind.BRIDGE) return@update s
        val id = s.session.chapter*1000+s.session.ayah
        val grade=RecallLadder.boundedGrade(reported,s.session.hints,s.session.exposed)
        val card=HifzScheduler.grade(s.cards[id] ?: ReviewCard(id),grade,LocalDate.now().toEpochDay())
        s.copy(cards=s.cards+(id to card),session=s.session.copy(stage=PracticeKind.READ,hints=0,exposed=false))
    }
    fun dailyPractice() {
        val s=_state.value;val corpus=s.corpus ?: return
        val plan=DailyMemorizationPlan.create(corpus.chapter(s.session.chapter),s.cards,LocalDate.now().toEpochDay(),maxNew=s.target)
        plan.allVerseIds.firstOrNull()?.let { select(it/1000,it%1000) }
    }
}
