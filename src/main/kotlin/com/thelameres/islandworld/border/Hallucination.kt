package com.thelameres.islandworld.border

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import org.slf4j.LoggerFactory

/**
 * Серверная часть галлюцинаций: накладывает эффекты стадии.
 * Клиентские шейдеры и тряска камеры — отдельная клиентская часть.
 */
object Hallucination {
    private val LOGGER = LoggerFactory.getLogger("islandworld/hallucination")

    /** Длительность эффекта в тиках. */
    private const val DURATION_TICKS = 300

    fun apply(player: ServerPlayer, stage: StageConfig) {
        for (spec in stage.effects) {
            val parts = spec.split(":")
            val holder = when (parts[0]) {
                "darkness" -> MobEffects.DARKNESS
                "nausea" -> MobEffects.NAUSEA
                "slowness" -> MobEffects.SLOWNESS
                else -> {
                    LOGGER.warn("Неизвестный эффект '{}' в конфиге", spec)
                    continue
                }
            }
            // В конфиге уровень считается с 1, в MobEffectInstance amplifier — с 0
            val level = parts.getOrNull(1)?.toIntOrNull() ?: 1
            player.addEffect(MobEffectInstance(holder, DURATION_TICKS, (level - 1).coerceAtLeast(0)))
        }
    }
}
