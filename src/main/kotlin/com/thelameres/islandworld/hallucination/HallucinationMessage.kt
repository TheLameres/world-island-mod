package com.thelameres.islandworld.hallucination

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException

/**
 * Контракт сервер → клиент для визуальных галлюцинаций (план 4.4).
 *
 * Чистый JVM-код без зависимостей от Minecraft: кодирование и декодирование тестируются отдельно.
 * Обёртка в CustomPacketPayload — отдельный файл, его API в 26.3 ещё не сверен.
 *
 * stage = 0 — явный сброс. Сервер шлёт его при возврате в безопасную зону, смерти и смене измерения.
 */
data class HallucinationMessage(
    val stage: Int,
    val intensity: Float,
    val postEffect: String?,
    val screenShake: Boolean,
    val durationTicks: Int,
) {
    init {
        require(stage in 0..MAX_STAGE) { "stage вне диапазона: $stage" }
        require(durationTicks >= 0) { "durationTicks < 0: $durationTicks" }
    }

    fun encode(): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeByte(VERSION)
            out.writeByte(stage)
            out.writeFloat(intensity.coerceIn(0f, 1f))
            out.writeUTF(postEffect.orEmpty())
            out.writeBoolean(screenShake)
            out.writeInt(durationTicks)
        }
        return bytes.toByteArray()
    }

    companion object {
        const val VERSION = 1
        const val MAX_STAGE = 3

        fun reset(): HallucinationMessage = HallucinationMessage(0, 0f, null, false, 0)

        /**
         * Возвращает null при неизвестной версии, обрыве или мусоре. Клиент в таком случае
         * ничего не применяет, а не падает.
         */
        fun decode(data: ByteArray): HallucinationMessage? {
            return try {
                DataInputStream(ByteArrayInputStream(data)).use { input ->
                    if (input.readUnsignedByte() != VERSION) return null
                    val stage = input.readUnsignedByte()
                    if (stage > MAX_STAGE) return null
                    val intensity = input.readFloat()
                    val post = input.readUTF().ifEmpty { null }
                    val shake = input.readBoolean()
                    val duration = input.readInt()
                    if (duration < 0 || intensity.isNaN()) return null
                    HallucinationMessage(stage, intensity.coerceIn(0f, 1f), post, shake, duration)
                }
            } catch (e: IOException) {
                null
            }
        }
    }
}
