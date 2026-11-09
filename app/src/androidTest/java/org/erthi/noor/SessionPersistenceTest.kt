package org.erthi.noor
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nexvary.hifzai.learning.*
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
@RunWith(AndroidJUnit4::class)
class SessionPersistenceTest {
 @Test fun assistanceAndReviewScheduleSurviveRepositoryReload() {
  val app=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as android.app.Application
  val prefs=app.getSharedPreferences("noor-learning-v1",0)
  prefs.edit().clear().commit()
  try {
   val session=PracticeSession(112,2,PracticeKind.RECALL,2,true,DifficultyTag.NONE)
   val card=HifzScheduler.grade(ReviewCard(112002),RecallGrade.GOOD,200L)
   assertTrue(NoorRepository(app).save(session,7,mapOf(card.verseId to card)))
   val reload=NoorRepository(app)
   assertEquals(session,reload.session());assertEquals(7,reload.target());assertEquals(card,reload.cards()[card.verseId])
   assertEquals(RecallGrade.HARD,RecallLadder.boundedGrade(RecallGrade.EASY,reload.session().hints,reload.session().exposed))
   assertEquals(6236,reload.loadCorpus().verses.size)
  } finally { prefs.edit().clear().commit() }
 }
}
