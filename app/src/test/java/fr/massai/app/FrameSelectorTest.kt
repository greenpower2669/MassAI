package fr.massai.app

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

class FrameSelectorTest {
    @Test fun keepsLowerLevelDespiteDifferentSharpnessAndRepeatedSignature() {
        val frames = (0 until 8).map { i ->
            CandidateFrame(
                File("candidate_$i.jpg"), i * 100L,
                if (i < 4) 100.0 else 2.0,
                // Same signature across level boundary should not discard
                // the first lower-level shot.
                if (i == 3 || i == 5) DoubleArray(64) { 1.0 }
                else DoubleArray(64) { if (it <= i * 4) 1.0 else 0.0 }
            )
        }
        val metadata = ScanMetadata(
            (0 until 8).map { i ->
                OrientationSample(i * 100L, (i % 4) * Math.PI / 2.0, i / 4)
            },
            sensorAvailable = true, targetLevels = 2, completedLevels = 2
        )
        val selected = FrameSelector.select(frames, 4, metadata)
        assertEquals(4, selected.size)
        assertEquals(2, selected.count { it.scanLevel == 0 })
        assertEquals(2, selected.count { it.scanLevel == 1 })
    }
}
