package com.thelameres.islandworld.util

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.tags.BlockTags
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraft.world.level.storage.LevelData
import org.slf4j.LoggerFactory

/**
 * Установка мирового спавна на юго-западе острова.
 *
 * Координаты: юг = +Z, запад = -X (см. north_south_temperature: hot_z = +1000 на юге).
 * Спавн ищется по спирали вокруг цели; подходящая позиция должна:
 *  - стоять на земле (dirt/grass/podzol/mud...) или песке;
 *  - не находиться на дереве (ни ствол, ни листва под/над игроком);
 *  - не быть в воде и иметь два свободных блока над головой.
 */
object SpawnUtil {

	private val LOGGER = LoggerFactory.getLogger("islandworld/spawn")

	const val TARGET_X = -600
	const val TARGET_Z = 600
	const val SEARCH_RADIUS = 200
	private const val STEP = 2
	private const val TREE_CHECK_HEIGHT = 6

	fun setIslandSpawn(level: ServerLevel) {
		val spawn = findSafeLandSpawn(level, TARGET_X, TARGET_Z, SEARCH_RADIUS)
		if (spawn == null) {
			LOGGER.warn("Не найдено подходящее место для спавна рядом с ({}, {}), оставлен ванильный спавн", TARGET_X, TARGET_Z)
			return
		}
		level.respawnData = LevelData.RespawnData.of(Level.OVERWORLD, spawn, 90f, 0f)

		LOGGER.info("Мировой спавн установлен: {}", spawn)
	}

	/** Перебирает колонки по спирали от цели, возвращает ближайшую подходящую позицию. */
	private fun findSafeLandSpawn(level: ServerLevel, cx: Int, cz: Int, radius: Int): BlockPos? {
		val candidates = ArrayList<IntArray>()
		var dx = -radius
		while (dx <= radius) {
			var dz = -radius
			while (dz <= radius) {
				candidates.add(intArrayOf(dx, dz))
				dz += STEP
			}
			dx += STEP
		}
		candidates.sortBy { it[0] * it[0] + it[1] * it[1] }

		for ((ox, oz) in candidates.map { it[0] to it[1] }) {
			val x = cx + ox
			val z = cz + oz
			// Загружаем чанк (нужен для heightmap и блоков)
			level.getChunk(x shr 4, z shr 4)
			val pos = findSpawnInColumn(level, x, z)
			if (pos != null) return pos
		}
		return null
	}

	/** Ищет сверху вниз первую позицию, где под ногами земля/песок, а сама позиция свободна. */
	private fun findSpawnInColumn(level: ServerLevel, x: Int, z: Int): BlockPos? {
		val top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z)
		var y = top
		val minY = level.minY + 1
		while (y > minY) {
			val feet = BlockPos(x, y, z)
			val groundState = level.getBlockState(feet.below())
			if (isGround(groundState)
				&& isPassable(level, feet)
				&& isPassable(level, feet.above())
			) {
				// Если над нами дерево (листва/стволы) — колонка не подходит
				return if (hasTreeAbove(level, feet)) null else feet
			}
			y--
		}
		return null
	}

	private fun isGround(state: BlockState): Boolean =
		state.`is`(BlockTags.DIRT) || state.`is`(BlockTags.SAND)

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
