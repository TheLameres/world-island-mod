package com.thelameres.islandworld.worldgen.blackroom

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Entity

/**
 * Wrap-around на границе комнаты (план, раздел 5.5).
 * Выход за границу [MIN, MAX) переносит сущность на противоположную сторону.
 *
 * Проверка раз в [CHECK_INTERVAL] тиков (план, 6.3).
 * В Fabric 0.164 события мира называются END_LEVEL_TICK (END_WORLD_TICK нет, проверено по байткоду).
 */
object BlackRoomWrapAround {

	private const val CHECK_INTERVAL = 9
	private const val SIZE = BlackRoom.SIZE_BLOCKS.toDouble()

	private var tickCounter = 0

	fun register() {
		ServerTickEvents.END_LEVEL_TICK.register { level: ServerLevel ->
			if (level.dimension() != BlackRoom.DIMENSION) return@register
			tickCounter++
			if (tickCounter % CHECK_INTERVAL != 0) return@register
			tick(level)
		}
	}

	private fun tick(level: ServerLevel) {
		for (entity: Entity in level.allEntities) {
			val x = entity.x
			val z = entity.z
			val nx = wrap(x)
			val nz = wrap(z)
			if (nx != x || nz != z) {
				entity.teleportTo(nx, entity.y, nz)
			}
		}
	}

	/** Переносит координату внутрь [MIN, MAX). */
	fun wrap(v: Double): Double {
		val min = BlackRoom.MIN_BLOCK.toDouble()
		val max = BlackRoom.MAX_BLOCK.toDouble()
		var r = v
		if (r >= max) r -= SIZE
		else if (r < min) r += SIZE
		return r
	}
}
