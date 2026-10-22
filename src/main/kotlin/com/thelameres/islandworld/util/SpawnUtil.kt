package com.thelameres.islandworld.util

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.tags.BiomeTags
import net.minecraft.tags.BlockTags
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.levelgen.Heightmap
import org.slf4j.LoggerFactory
import kotlin.math.cos
import kotlin.math.sin
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
 *     свободный воздух, нет дерева над головой и рядом (SHORE_DISTANCE) есть океан.
 *  3. Результат кэшируется на сессию сервера.
 *
 * Поиск идёт кольцами с возрастающим радиусом, поэтому всегда возвращается ближайший
 * подходящий вариант, а не первый попавшийся.
 */
object SpawnUtil {

	private val LOGGER = LoggerFactory.getLogger("islandworld/spawn")

	const val TARGET_X = -930
	const val TARGET_Z = 600

	/** Максимальный радиус поиска суши от цели. */
	private const val LAND_MAX_RADIUS = 400
	/** Максимальный радиус поиска берега от найденной суши. */
	private const val COAST_MAX_RADIUS = 200
	/** Шаг между кольцами, блоки. */
	private const val RING_STEP = 4
	/** Угловой шаг на кольце, градусы. */
	private const val ANGLE_STEP_DEG = 6

	/** Максимальное расстояние от спавна до океана. */
	private const val SHORE_DISTANCE = 8
	private const val TREE_CHECK_HEIGHT = 6

	/** Результат поиска не меняется за сессию, поэтому кэшируем. */
	@Volatile
	private var cachedSpawn: BlockPos? = null

	/** Сбрасывает кэш (вызывается при остановке сервера). */
	fun reset() {
		cachedSpawn = null
	}

	/**
	 * Возвращает стартовую точку или null, если подходящего места нет.
	 * Первый вызов тяжёлый, дальше используется кэш.
	 */
	fun findPlayerSpawn(level: ServerLevel): BlockPos? {
		cachedSpawn?.let { return it }

		val land = findNearestLand(level, TARGET_X, TARGET_Z)
		if (land == null) {
			LOGGER.warn("Суша рядом с ({}, {}) не найдена", TARGET_X, TARGET_Z)
			return null
		}
		LOGGER.info("Ближайшая суша к цели: {}", land)

		val spawn = findCoastSpawn(level, land.x, land.z)
		if (spawn == null) {
			LOGGER.warn("Место у берега рядом с {} не найдено", land)
			return null
		}
		cachedSpawn = spawn

		val dx = (spawn.x - TARGET_X).toDouble()
		val dz = (spawn.z - TARGET_Z).toDouble()
		LOGGER.info("Стартовая точка у океана: {} (до цели {} блоков)", spawn, sqrt(dx * dx + dz * dz).toInt())
		return spawn
	}

	/** Ближайшая к (cx, cz) колонка, биом которой не океанский. */
	private fun findNearestLand(level: ServerLevel, cx: Int, cz: Int): BlockPos? =
		findInRings(level, cx, cz, LAND_MAX_RADIUS) { x, z ->
			val surface = BlockPos(x, level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1, z)
			if (!isOceanAt(level, surface)) surface else null
		}

	/** Ближайшее к (cx, cz) место у берега, пригодное для спавна. */
	private fun findCoastSpawn(level: ServerLevel, cx: Int, cz: Int): BlockPos? =
		findInRings(level, cx, cz, COAST_MAX_RADIUS) { x, z ->
			findSpawnInColumn(level, x, z)
		}

	/**
	 * Проверяет колонки кольцами от центра наружу до maxRadius.
	 * Возвращает первый результат test, не равный null.
	 */
	private inline fun findInRings(
		level: ServerLevel,
		cx: Int,
		cz: Int,
		maxRadius: Int,
		test: (Int, Int) -> BlockPos?
	): BlockPos? {
		var r = 0
		while (r <= maxRadius) {
			if (r == 0) {
				level.getChunk(cx shr 4, cz shr 4)
				test(cx, cz)?.let { return it }
			} else {
				var deg = 0
				while (deg < 360) {
					val rad = Math.toRadians(deg.toDouble())
					val x = cx + Math.round(cos(rad) * r).toInt()
					val z = cz + Math.round(sin(rad) * r).toInt()
					level.getChunk(x shr 4, z shr 4)
					test(x, z)?.let { return it }
					deg += ANGLE_STEP_DEG
				}
			}
			r += RING_STEP
		}
		return null
	}

	/** Сверху вниз: земля/трава/песок под ногами, свободно, без дерева, рядом океан. */
	private fun findSpawnInColumn(level: ServerLevel, x: Int, z: Int): BlockPos? {
		val top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z)
		var y = top
		val minY = level.minY + 1
		while (y > minY) {
			val feet = BlockPos(x, y, z)
			if (isGround(level.getBlockState(feet.below()))
				&& isPassable(level, feet)
				&& isPassable(level, feet.above())
			) {
				if (hasTreeAbove(level, feet)) return null
				return if (isOceanNearby(level, feet)) feet else null
			}
			y--
		}
		return null
	}

	private fun isOceanNearby(level: ServerLevel, feet: BlockPos): Boolean {
		for (dx in -SHORE_DISTANCE..SHORE_DISTANCE step 2) {
			for (dz in -SHORE_DISTANCE..SHORE_DISTANCE step 2) {
				val x = feet.x + dx
				val z = feet.z + dz
				val surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1
				if (isOceanAt(level, BlockPos(x, surfaceY, z))) return true
			}
		}
		return false
	}

	private fun isOceanAt(level: ServerLevel, pos: BlockPos): Boolean =
		level.getBiome(pos).`is`(BiomeTags.IS_OCEAN)

	private fun isGround(state: BlockState): Boolean =
		state.`is`(BlockTags.DIRT)
			|| state.`is`(BlockTags.SAND)
			|| state.`is`(Blocks.GRASS_BLOCK)

	private fun isPassable(level: ServerLevel, pos: BlockPos): Boolean {
		val state = level.getBlockState(pos)
		return state.getCollisionShape(level, pos).isEmpty
			&& level.getFluidState(pos).isEmpty
			&& !state.`is`(BlockTags.LOGS)
			&& !state.`is`(BlockTags.LEAVES)
	}

	private fun hasTreeAbove(level: ServerLevel, feet: BlockPos): Boolean {
		for (i in 1..TREE_CHECK_HEIGHT) {
			val state = level.getBlockState(feet.above(i))
			if (state.`is`(BlockTags.LOGS) || state.`is`(BlockTags.LEAVES)) return true
		}
		return false
	}
}
