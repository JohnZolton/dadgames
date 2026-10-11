package blocks.core

import kotlinx.serialization.Serializable

/**
 * SplitMix64. Tiny, good quality, and its whole state is one Long, so a saved game resumes with exactly the
 * same upcoming pieces it would have had.
 */
@Serializable
data class Rng(val state: Long) {
    /** Returns (value in 0 until bound, next generator). */
    fun nextInt(bound: Int): Pair<Int, Rng> {
        require(bound > 0)
        val s = state + -0x61c8864680b583ebL
        var z = s
        z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
        z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
        z = z xor (z ushr 31)
        // Unbiased enough for bound <= 49: use the top 31 bits.
        val v = ((z ushr 33) % bound).toInt()
        return v to Rng(s)
    }
}
