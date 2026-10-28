package com.thelameres.islandworld.hallucination

import com.mojang.serialization.Codec
import com.mojang.serialization.DataResult
import com.mojang.serialization.codecs.RecordCodecBuilder
import java.util.Optional

/**
 * Дескриптор стадии галлюцинаций: сервер -> клиент (план 4.4).
 *
 * Описан как Codec через RecordCodecBuilder, как в документации Fabric. Тот же кодек
 * используется для сети (ByteBufCodecs.fromCodec в HallucinationPayload).
 *
 * stage = 0 - явный сброс: сервер шлёт его при возврате в безопасную зону, смерти и смене измерения.
 */
data class HallucinationMessage(
    val stage: Int,
    val intensity: Float,
    val postEffect: String?,
    val screenShake: Boolean,
    val durationTicks: Int,
) {
    companion object {
        const val MAX_STAGE = 3
        const val MAX_DURATION_TICKS = 24_000

        /** Сброс эффекта: stage = 0, остальные поля нейтральны. */
        fun reset(): HallucinationMessage = HallucinationMessage(0, 0f, null, false, 0)

        private fun postEffectOf(m: HallucinationMessage): Optional<String> = Optional.ofNullable(m.postEffect)

        private val RAW: Codec<HallucinationMessage> = RecordCodecBuilder.create { instance ->
            instance.group(
                Codec.INT.fieldOf("stage").forGetter(HallucinationMessage::stage),
                Codec.FLOAT.fieldOf("intensity").forGetter(HallucinationMessage::intensity),
                Codec.STRING.optionalFieldOf("post_effect").forGetter(::postEffectOf),
                Codec.BOOL.fieldOf("screen_shake").forGetter(HallucinationMessage::screenShake),
                Codec.INT.fieldOf("duration_ticks").forGetter(HallucinationMessage::durationTicks),
            ).apply(instance) { stage, intensity, post, shake, duration ->
                HallucinationMessage(stage, intensity, post.orElse(null), shake, duration)
            }
        }

        /**
         * Проверка диапазонов при декодировании: неверные данные дают ошибку парсинга,
         * а не исключение внутри клиента.
         */
        val CODEC: Codec<HallucinationMessage> = RAW.validate { m ->
            when {
                m.stage !in 0..MAX_STAGE -> DataResult.error { "stage вне диапазона: ${m.stage}" }
                m.intensity.isNaN() || m.intensity < 0f || m.intensity > 1f ->
                    DataResult.error { "intensity вне 0..1: ${m.intensity}" }
                m.durationTicks !in 0..MAX_DURATION_TICKS ->
                    DataResult.error { "duration_ticks вне 0..$MAX_DURATION_TICKS: ${m.durationTicks}" }
                else -> DataResult.success(m)
            }
        }
    }
}
