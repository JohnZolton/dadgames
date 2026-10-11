package dadgames.blocks

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/** Small synthesized sounds (no assets): a wooden tock on placement, a rising chime on a clear. Off by default. */
class Sound {
    private val rate = 22050
    private val tock = build(0.09) { t -> (sin(2 * PI * 820 * t) * 0.6 + sin(2 * PI * 1640 * t) * 0.25) * exp(-t * 55) }
    private val chime = build(0.42) { t ->
        val f = if (t < 0.12) 880.0 else if (t < 0.24) 1108.7 else 1318.5
        sin(2 * PI * f * t) * 0.45 * exp(-(t % 0.12) * 9) * (1 - t / 0.42)
    }
    private val bigChime = build(0.6) { t ->
        val notes = doubleArrayOf(880.0, 1108.7, 1318.5, 1760.0)
        val f = notes[(t / 0.13).toInt().coerceAtMost(3)]
        sin(2 * PI * f * t) * 0.45 * exp(-(t % 0.13) * 7) * (1 - t / 0.6)
    }

    private fun build(seconds: Double, wave: (Double) -> Double): AudioTrack? = runCatching {
        val n = (rate * seconds).toInt()
        val pcm = ShortArray(n) { i -> (wave(i.toDouble() / rate).coerceIn(-1.0, 1.0) * Short.MAX_VALUE * 0.8).toInt().toShort() }
        AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(n * 2)
            .build().also { it.write(pcm, 0, n) }
    }.getOrNull()

    private fun play(t: AudioTrack?) {
        t ?: return
        runCatching { t.stop(); t.reloadStaticData(); t.play() }
    }

    fun place() = play(tock)
    fun clear(combo: Int) = play(if (combo >= 2) bigChime else chime)

    fun release() { listOf(tock, chime, bigChime).forEach { it?.release() } }
}
