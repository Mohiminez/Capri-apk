package app.capri.mobile

import org.junit.Assert.*
import org.junit.Test

class VoiceGainTest {
    @Test fun belowAndAtFullUsesThePlayerVolume() {
        assertEquals(0.5f.toDouble(), VoiceGain.player(50).toDouble(), 0.0001)
        assertEquals(1.0, VoiceGain.player(100).toDouble(), 0.0001)
        assertEquals(0, VoiceGain.millibels(100))
        assertEquals(0, VoiceGain.millibels(60))
    }

    @Test fun boostAboveFull() {
        assertEquals(1.0, VoiceGain.player(200).toDouble(), 0.0001)
        assertEquals(602, VoiceGain.millibels(200))     // +6 dB
        assertEquals(352, VoiceGain.millibels(150))     // +3.5 dB
    }

    @Test fun outOfRangeIsClamped() {
        assertEquals(200, VoiceGain.clampPercent(900))
        assertEquals(20, VoiceGain.clampPercent(-5))
        assertEquals(602, VoiceGain.millibels(900))
    }
}
