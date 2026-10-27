package com.thelameres.islandworld.util

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.tags.BiomeTags
import net.minecraft.tags.BlockTags
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.levelgen.Heightmap
import kotlin.math.cos
import kotlin.math.sin

/** Максимальный радиус поиска суши от цели. */
private const val LAND_MAX_RADIUS = 400

/** Максимальный радиус поиска берега от найденной суши. */
private const val COAST_MAX_RADIUS = 200

/** Шаг между кольцами, блоки. */
private const val RING_STEP = 4

/** Угловой шаг на кольце, градусы. */
private const val ANGLE_STEP_DEG = 6

/** Максимальное расстояние от точки до океана, чтобы считать её берегом. */
private const val SHORE_DISTANCE = 8

/** Высота проверки дерева над точкой. */
private const val TREE_CHECK_HEIGHT = 6


/**
 * Предикаты мира и блоков, общие для стартовой точки ([SpawnUtil]) и респавна
 * на краю острова (EdgeRespawn).
 *
 * Сделаны расширениями, потому что первый параметр у них — субъект действия,
 * а не ещё один аргумент: `level.isOceanAt(pos)` читается как «уровень, океан ли
 * в точке», тогда как `isOceanAt(level, pos)` требует помнить порядок аргументов.
 *
 * Вынесены из [SpawnUtil] отдельным файлом: сам он про поиск точки и держит
 * состояние (кэш), а здесь только чистые проверки без состояния.
 */

/** Океанский ли биом в точке [pos]. */
fun ServerLevel.isOceanAt(pos: BlockPos): Boolean =
    getBiome(pos).`is`(BiomeTags.IS_OCEAN)

/** Океанский ли биом в колонке ([x], [z]) на уровне моря. */
fun ServerLevel.isOceanAt(x: Int, z: Int): Boolean =
    getBiome(BlockPos(x, seaLevel, z)).`is`(BiomeTags.IS_OCEAN)

/** Годится ли блок как опора под ногами: земля, песок или трава. */
fun BlockState.isGround(): Boolean =
    `is`(BlockTags.DIRT)
        || `is`(BlockTags.SAND)
        || `is`(Blocks.GRASS_BLOCK)

/** Свободно ли место для игрока: нет коллизии, жидкости, брёвен и листвы. */
fun ServerLevel.isPassable(pos: BlockPos): Boolean {
    val state = getBlockState(pos)
    return state.getCollisionShape(this, pos).isEmpty
        && getFluidState(pos).isEmpty
        && !state.`is`(BlockTags.LOGS)
        && !state.`is`(BlockTags.LEAVES)
}

/** Есть ли ствол или листва в пределах [TREE_CHECK_HEIGHT] над точкой [feet]. */
fun ServerLevel.hasTreeAbove(feet: BlockPos): Boolean {
    for (i in 1..TREE_CHECK_HEIGHT) {
        val state = getBlockState(feet.above(i))
        if (state.`is`(BlockTags.LOGS) || state.`is`(BlockTags.LEAVES)) return true
    }
    return false
}

// --- Поиск позиций -----------------------------------------------------

/**
 * Сверху вниз: грунт под ногами, свободно в точке и над головой, без дерева сверху,
 * рядом ([SHORE_DISTANCE]) есть океан. Возвращает точку стояния или null.
 */
fun ServerLevel.findSpawnInColumn(x: Int, z: Int): BlockPos? {
    val top = getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z)
    var y = top
    val minY = this.minY + 1
    while (y > minY) {
        val feet = BlockPos(x, y, z)
        if (getBlockState(feet.below()).isGround()
            && isPassable(feet)
            && isPassable(feet.above())
        ) {
            if (hasTreeAbove(feet)) return null
            return if (isOceanNearby(feet)) feet else null
        }
        y--
    }
    return null
}

/** Ближайшая к ([cx], [cz]) колонка, биом которой не океанский. */
fun ServerLevel.findNearestLand(cx: Int, cz: Int): BlockPos? =
    findInRings(cx, cz, LAND_MAX_RADIUS) { x, z ->
        val surface = BlockPos(x, getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1, z)
        if (!isOceanAt(surface)) surface else null
    }

/** Ближайшее к ([cx], [cz]) место у берега, пригодное для спавна. */
fun ServerLevel.findCoastSpawn(cx: Int, cz: Int): BlockPos? =
    findInRings(cx, cz, COAST_MAX_RADIUS) { x, z ->
        findSpawnInColumn(x, z)
    }

/**
 * Проверяет колонки кольцами от центра наружу до [maxRadius].
 * Возвращает первый результат [test], не равный null.
 */
private inline fun ServerLevel.findInRings(
    cx: Int,
    cz: Int,
    maxRadius: Int,
    test: (Int, Int) -> BlockPos?
): BlockPos? {
    var r = 0
    while (r <= maxRadius) {
        if (r == 0) {
            getChunk(cx shr 4, cz shr 4)
            test(cx, cz)?.let { return it }
        } else {
            var deg = 0
            while (deg < 360) {
                val rad = Math.toRadians(deg.toDouble())
                val x = cx + Math.round(cos(rad) * r).toInt()
                val z = cz + Math.round(sin(rad) * r).toInt()
                getChunk(x shr 4, z shr 4)
                test(x, z)?.let { return it }
                deg += ANGLE_STEP_DEG
            }
        }
        r += RING_STEP
    }
    return null
}

/** Есть ли океан в квадрате [SHORE_DISTANCE] вокруг точки [feet]. */
private fun ServerLevel.isOceanNearby(feet: BlockPos): Boolean {
    for (dx in -SHORE_DISTANCE..SHORE_DISTANCE step 2) {
        for (dz in -SHORE_DISTANCE..SHORE_DISTANCE step 2) {
            val x = feet.x + dx
            val z = feet.z + dz
            val surfaceY = getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1
            if (isOceanAt(BlockPos(x, surfaceY, z))) return true
        }
    }
    return false
}
