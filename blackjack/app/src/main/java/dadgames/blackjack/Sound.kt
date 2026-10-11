package dadgames.blackjack

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/** Tiny synthesized table sounds (no audio files). Only played when Sound is on in Settings. */
class Sound {
    enum class Fx { CARD, CHIP, WIN, LOSE, SHUFFLE }

    private val rate = 22050
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))
    private val clips = HashMap<Fx, ShortArray>()

    private fun clip(fx: Fx): ShortArray = clips.getOrPut(fx) {
        when (fx) {
            Fx.CARD -> noise(0.05, 0.012, 0.5)
            Fx.SHUFFLE -> noise(0.35, 0.12, 0.35)
            Fx.CHIP -> tone(2600.0, 0.05, 0.010, 0.45) + tone(3100.0, 0.06, 0.012, 0.35)
            Fx.WIN -> tone(880.0, 0.12, 0.05, 0.35) + tone(1320.0, 0.22, 0.09, 0.35)
            Fx.LOSE -> tone(330.0, 0.18, 0.08, 0.3)
        }
    }

    private fun tone(freq: Double, secs: Double, decay: Double, vol: Double): ShortArray {
        val n = (rate * secs).toInt()
        return ShortArray(n) { i ->
            val t = i.toDouble() / rate
            (sin(2 * PI * freq * t) * exp(-t / decay) * vol * Short.MAX_VALUE).toInt().toShort()
        }
    }

    private fun noise(secs: Double, decay: Double, vol: Double): ShortArray {
        val r = Random(1)
        val n = (rate * secs).toInt()
        var last = 0.0
        return ShortArray(n) { i ->
            val t = i.toDouble() / rate
            last = last * 0.55 + (r.nextDouble() * 2 - 1) * 0.45 // soft low-pass
            (last * exp(-t / decay) * vol * Short.MAX_VALUE).toInt().toShort()
        }
    }

    fun play(fx: Fx) {
        scope.launch {
            runCatching {
                val data = clip(fx)
                val track = AudioTrack.Builder()
                    .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                    .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .setBufferSizeInBytes(data.size * 2)
                    .build()
                track.write(data, 0, data.size)
                track.setNotificationMarkerPosition(data.size)
                track.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
                    override fun onMarkerReached(t: AudioTrack) { t.release() }
                    override fun onPeriodicNotification(t: AudioTrack) {}
                })
                track.play()
            }
        }
    }
}
