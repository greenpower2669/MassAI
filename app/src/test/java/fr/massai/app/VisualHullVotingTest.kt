package fr.massai.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class VisualHullVotingTest {
    @Test fun missingViewsAreNotNegativeEvidence() {
        val minimum = VisualHullVoting.minimumObserved(20, false)
        assertEquals(4, minimum)
        assertTrue(VisualHullVoting.accepts(4, 4, minimum, 0.86))
    }

    @Test fun tooFewViewsCannotInventGeometry() {
        val minimum = VisualHullVoting.minimumObserved(60, true)
        assertEquals(10, minimum)
        assertFalse(VisualHullVoting.accepts(8, 8, minimum, 0.75))
        assertTrue(VisualHullVoting.accepts(12, 15, minimum, 0.75))
    }

    @Test fun strictAndTolerantSettingsDiffer() {
        val minimum = VisualHullVoting.minimumObserved(20, false)
        assertFalse(VisualHullVoting.accepts(6, 8, minimum, 0.86))
        assertTrue(VisualHullVoting.accepts(6, 8, minimum, 0.75))
    }
}
