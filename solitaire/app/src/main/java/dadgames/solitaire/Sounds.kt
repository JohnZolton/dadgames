package dadgames.solitaire

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool

/** Three tiny synthesized effects (see tools/make_sounds.py). Loaded only once sound is switched on. */
class Sounds(private val context: Context) {
    enum class Fx { PLACE, FLIP, WIN }

    private var pool: SoundPool? = null
    private val ids = IntArray(3)

    private fun ensure(): SoundPool {
        pool?.let { return it }
        val p = SoundPool.Builder().setMaxStreams(4).setAudioAttributes(
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        ).build()
        ids[0] = p.load(context, R.raw.snd_place, 1)
        ids[1] = p.load(context, R.raw.snd_flip, 1)
        ids[2] = p.load(context, R.raw.snd_win, 1)
        pool = p
        return p
    }

    fun warmUp() { ensure() }

    fun play(fx: Fx) {
        val vol = if (fx == Fx.WIN) 0.8f else 0.6f
        ensure().play(ids[fx.ordinal], vol, vol, 1, 0, 1f)
    }

    fun release() { pool?.release(); pool = null }
}
