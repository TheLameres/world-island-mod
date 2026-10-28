package com.thelameres.islandworld.client.hallucination

import com.thelameres.islandworld.hallucination.HallucinationMessage

/**
 * Клиентское состояние визуальных галлюцинаций (план 4.4). Чистая логика без Minecraft:
 * все решения здесь, а Minecraft-часть только читает [current] и применяет результат.
 *
 * Шесть правил сброса из плана:
 *  1. пакет stage = 0 (сервер: возврат / смерть / смена измерения) → [onMessage]
 *  2. истёк durationTicks → [tick]
 *  3. смерть игрока → [onPlayerDied]
 *  4. смена измерения → [onDimensionChanged]
 *  5. выход из мира → [onDisconnect]
 *  6. смена ресурспака → [onResourcesReloaded]
 *
 * Идемпотентность: повторный пакет той же стадии не усиливает эффект, а перезапускает таймер.
 */
class HallucinationController {

    data class Active(
        val message: HallucinationMessage,
        var ageTicks: Int = 0,
    ) {
        val remainingTicks: Int get() = message.durationTicks - ageTicks
    }

    private var active: Active? = null

    /** Текущий активный эффект или null. Для Minecraft-части: читать, не менять. */
    val current: Active? get() = active

    /** Вызывается, когда пакет пришёл. Возвращает true, если состояние изменилось. */
    fun onMessage(message: HallucinationMessage): Boolean {
        if (message.stage == 0) {
            return clear()
        }
        val before = active?.message
        // Повтор той же стадии не накапливает силу: сохраняем тот же дескриптор, сбрасываем таймер.
        active = Active(message)
        return before != message
    }

    /** Вызывается один раз за клиентский тик. Возвращает true, если эффект истёк и сброшен. */
    fun tick(): Boolean {
        val a = active ?: return false
        a.ageTicks++
        if (a.remainingTicks <= 0) {
            active = null
            return true
        }
        return false
    }

    fun onPlayerDied(): Boolean = clear()

    fun onDimensionChanged(): Boolean = clear()

    fun onDisconnect(): Boolean = clear()

    /** Ресурспак перезагружен: активный эффект пересоздаётся, потому что шейдер мог потерять ссылку. */
    fun onResourcesReloaded(): Boolean {
        val a = active ?: return false
        active = Active(a.message)
        return true
    }

    private fun clear(): Boolean {
        if (active == null) return false
        active = null
        return true
    }
}
