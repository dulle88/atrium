package io.github.dulle88.atrium.sky

data class Rgb(val r: Float, val g: Float, val b: Float) {
    fun mix(o: Rgb, t: Float) = Rgb(r + (o.r - r) * t, g + (o.g - g) * t, b + (o.b - b) * t)
}

object SkyPalette {
    private class Key(val elevation: Double, val zenith: Rgb, val horizon: Rgb)

    private fun c(r: Int, g: Int, b: Int) = Rgb(r / 255f, g / 255f, b / 255f)

    private val keys = listOf(
        Key(-18.0, c(4, 7, 20), c(10, 18, 46)),
        Key(-12.0, c(8, 14, 38), c(26, 38, 84)),
        Key(-6.0, c(22, 30, 76), c(128, 82, 128)),
        Key(-1.0, c(48, 80, 140), c(240, 140, 96)),
        Key(4.0, c(64, 120, 196), c(247, 200, 140)),
        Key(14.0, c(62, 134, 214), c(170, 208, 240)),
        Key(45.0, c(44, 116, 208), c(152, 200, 240))
    )

    fun zenith(sunElevation: Double): Rgb = sample(sunElevation) { it.zenith }
    fun horizon(sunElevation: Double): Rgb = sample(sunElevation) { it.horizon }

    private fun sample(elevation: Double, pick: (Key) -> Rgb): Rgb {
        if (elevation <= keys.first().elevation) return pick(keys.first())
        for (i in 1 until keys.size) {
            if (elevation <= keys[i].elevation) {
                val a = keys[i - 1]
                val b = keys[i]
                val t = ((elevation - a.elevation) / (b.elevation - a.elevation)).toFloat()
                return pick(a).mix(pick(b), t)
            }
        }
        return pick(keys.last())
    }
}
