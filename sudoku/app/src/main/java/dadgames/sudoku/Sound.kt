package dadgames.sudoku

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

enum class Sfx { PLACE, SOFT, WRONG, WIN }

/** Small, soft synthesized sounds (no audio files). Off unless he turns sound on. */
class SoundFx {
    private val rate = 22050
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))
    private val cache = HashMap<Sfx, ShortArray>()

    private fun tone(notes: List<Pair<Double, Double>>, gap: Double = 0.0, volume: Double = 0.25): ShortArray {
        val out = ArrayList<Short>()
        for ((freq, secs) in notes) {
            val n = (rate * secs).toInt()
            for (i in 0 until n) {
                val t = i.toDouble() / rate
                val env = exp(-t * 9.0 / secs) * minOf(1.0, i / (rate * 0.004))
                val s = (sin(2 * PI * freq * t) + 0.3 * sin(4 * PI * freq * t)) * env * volume
                out += (s * Short.MAX_VALUE).toInt().coerceIn(-32767, 32767).toShort()
            }
            repeat((rate * gap).toInt()) { out += 0 }
        }
        return out.toShortArray()
    }

    private fun samples(s: Sfx): ShortArray = cache.getOrPut(s) {
        when (s) {
            Sfx.PLACE -> tone(listOf(880.0 to 0.09))
            Sfx.SOFT -> tone(listOf(660.0 to 0.07), volume = 0.15)
            Sfx.WRONG -> tone(listOf(233.0 to 0.22), volume = 0.22)
            Sfx.WIN -> tone(listOf(523.25 to 0.16, 659.25 to 0.16, 783.99 to 0.16, 1046.5 to 0.45), gap = 0.02)
        }
    }

    fun play(s: Sfx, enabled: Boolean) {
        if (!enabled) return
        scope.launch {
            runCatching {
                val data = samples(s)
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
                }, android.os.Handler(android.os.Looper.getMainLooper()))
                track.play()
            }
        }
    }

    fun release() {}
}
