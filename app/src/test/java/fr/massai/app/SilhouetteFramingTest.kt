package fr.massai.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SilhouetteFramingTest {
    @Test fun detectsTruncatedMask() {
        assertTrue(SilhouetteFraming.isVerticallyClipped(0, 450, 480))
        assertTrue(SilhouetteFraming.isVerticallyClipped(22, 479, 480))
        assertTrue(SilhouetteFraming.isVerticallyClipped(2, 478, 480))
    }
    @Test fun acceptsFramedSubject() {
        assertFalse(SilhouetteFraming.isVerticallyClipped(20, 450, 480))
        assertFalse(SilhouetteFraming.isVerticallyClipped(10, 465, 480))
    }
    @Test fun rejectsBrokenBounds() {
        assertTrue(SilhouetteFraming.isVerticallyClipped(-1, 200, 480))
        assertTrue(SilhouetteFraming.isVerticallyClipped(300, 250, 480))
    }
}
