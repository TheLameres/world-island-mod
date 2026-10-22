package com.thelameres.islandworld.worldgen.blackroom

import com.thelameres.islandworld.IslandWorldMod
import net.minecraft.core.registries.Registries
import net.minecraft.resources.ResourceKey
import net.minecraft.world.level.Level
import kotlin.jvm.JvmField

/**
 * Константы «Чёрной комнаты» (план, раздел 5).
 *
 * Комната занимает чанки [-4..4] по X и Z (9×9 чанков = 144×144 блока).
 * В блоках это [MIN, MAX) = [-64, 80). Центр комнаты — (8, 8).
 */
object BlackRoom {
	const val MIN_BLOCK: Int = -64
	const val MAX_BLOCK: Int = 80
	const val SIZE_BLOCKS: Int = MAX_BLOCK - MIN_BLOCK // 144
	const val ROOM_HALF_CHUNKS: Int = 4

	const val FLOOR_Y: Int = 63
	const val SPAWN_Y: Double = 64.0

	@JvmField
	val DIMENSION: ResourceKey<Level> =
		ResourceKey.create(Registries.DIMENSION, IslandWorldMod.id("black_room"))
}
