package fr.massai.app

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

class FrameSelectorCoverageTest {
    @Test fun fillsSparseAngularBinsWithoutSacrificingLevels() {
        val candidates = (0 until 12).map { i ->
            CandidateFrame(
                File("candidate_$i.jpg"), i * 100L, 100.0,
                DoubleArray(64) { if (it == i) 1.0 else 0.0 }
            )
        }
        val metadata = ScanMetadata(
            samples = (0 until 12).map { i ->
                OrientationSample(i * 100L,
                    if (i < 6) 0.20 + 0.02 * i else Math.PI + 0.02 * i,
                    i / 6)
            },
            sensorAvailable = true, targetLevels = 2, completedLevels = 2
        )
        val selected = FrameSelector.select(candidates, 8, metadata)
        assertEquals(8, selected.size)
        assertEquals(4, selected.count { it.scanLevel == 0 })
        assertEquals(4, selected.count { it.scanLevel == 1 })
    }
}
