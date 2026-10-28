package com.thelameres.islandworld.client.shake

import kotlin.math.sin

/**
 * Математика тряски камеры (план 4.4, фаза C). Чистая функция без Minecraft.
 *
 * Смещение применяется только к позе отрисовки камеры (миксин), не к yaw/pitch игрока,
 * поэтому прицеливание не сбивается. Затухание линейное: к концу длительности смещение строго 0,
 * без обрыва.
 */
object CameraShake {
    /** Текущее смещение (yaw, pitch) в градусах для миксина; выставляется клиентским контроллером. */
    @Volatile private var offsetYaw: Float = 0f
    @Volatile private var offsetPitch: Float = 0f

    /** Возвращает [yaw, pitch] в градусах. Массив новый на каждый вызов: вызывается раз за кадр. */
    @JvmStatic fun currentOffsetDegrees(): FloatArray = floatArrayOf(offsetYaw, offsetPitch)

    /** Выставляет смещение на текущий тик. */
    @JvmStatic fun setCurrent(yaw: Float, pitch: Float) {
        offsetYaw = yaw
        offsetPitch = pitch
    }

    /** Максимальный угол смещения при intensity = 1, градусы. Малый: тряска — акцент, а не укачивание. */
    const val MAX_DEGREES: Float = 1.2f

    /** Линейный множитель 1 → 0 на отрезке [0, duration). */
    fun envelope(age: Int, duration: Int): Float {
        if (duration <= 0 || age >= duration) return 0f
        if (age <= 0) return 1f
        return 1f - age.toFloat() / duration
    }

    /** Смещение камеры (yaw, pitch) в градусах для тика [age] внутри эффекта длительностью [duration]. */
    fun offsetDegrees(age: Int, intensity: Float, duration: Int): Pair<Float, Float> {
        val amplitude = MAX_DEGREES * intensity.coerceIn(0f, 1f) * envelope(age, duration)
        if (amplitude == 0f) return 0f to 0f
        val yaw = amplitude * sin(age * 0.7).toFloat()
        val pitch = amplitude * sin(age * 0.9 + 1.3).toFloat()
        return yaw to pitch
    }
}
