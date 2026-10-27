package com.thelameres.islandworld.util

import com.thelameres.islandworld.core.SessionScoped
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import org.slf4j.LoggerFactory
import kotlin.math.sqrt

/**
 * Стартовая точка игрока у океана рядом с целевой точкой (-930, 600) на юго-западе острова.
 *
 * Координаты: юг = +Z, запад = -X.
 *
 * Алгоритм:
 *  1. От целевой точки ищем ближайшую сушу: колонку с биомом, который не входит
 *     в #minecraft:is_ocean. Если сама цель на суше, это она.
 *  2. От найденной суши ищем ближайшее место у берега: земля/трава/песок под ногами,
 *     свободный воздух, нет дерева над головой и рядом есть океан.
 *  3. Результат кэшируется на сессию сервера.
 *
 * Поиск идёт кольцами с возрастающим радиусом, поэтому всегда возвращается ближайший
 * подходящий вариант, а не первый попавшийся.
 *
 * Сами проверки мира живут в [LevelPredicates] как расширения: здесь остаётся только
 * то, что относится к стартовой точке, — константы цели и кэш.
 */
object SpawnUtil : SessionScoped {

    private val LOGGER = LoggerFactory.getLogger("islandworld/spawn")

    const val TARGET_X = -930
    const val TARGET_Z = 600

    /** Результат поиска не меняется за сессию, поэтому кэшируем. */
    @Volatile
    private var cachedSpawn: BlockPos? = null

    /** Сбрасывает кэш стартовой точки: вызывается на SERVER_STOPPED через Lifecycle. */
    override fun reset() {
        cachedSpawn = null
    }

    /**
     * Возвращает стартовую точку или null, если подходящего места нет.
     * Первый вызов тяжёлый, дальше используется кэш.
     */
    fun findPlayerSpawn(level: ServerLevel): BlockPos? {
        cachedSpawn?.let { return it }

        val land = level.findNearestLand(TARGET_X, TARGET_Z)
        if (land == null) {
            LOGGER.warn("Суша рядом с ({}, {}) не найдена", TARGET_X, TARGET_Z)
            return null
        }
        LOGGER.info("Ближайшая суша к цели: {}", land)

        val spawn = level.findCoastSpawn(land.x, land.z)
        if (spawn == null) {
            LOGGER.warn("Место у берега рядом с {} не найдено", land)
            return null
        }
        cachedSpawn = spawn

        val dx = (spawn.x - TARGET_X).toDouble()
        val dz = (spawn.z - TARGET_Z).toDouble()
        LOGGER.info(
            "Стартовая точка у океана: {} (до цели {} блоков)",
            spawn,
            sqrt(dx * dx + dz * dz).toInt()
        )
        return spawn
    }
}
