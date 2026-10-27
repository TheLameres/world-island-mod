package com.thelameres.islandworld.border

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import com.thelameres.islandworld.util.SpawnUtil
import net.minecraft.world.entity.Relative
import net.minecraft.world.level.levelgen.Heightmap
import org.slf4j.LoggerFactory
import java.util.UUID
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * Респавн после смерти от границы: игрока переносит на случайный край острова.
 *
 * Алгоритм: выбираем случайное направление от центра границы и идём от внешнего радиуса
 * к центру. Первая суша, за которой в пределах coast_check_radius есть океан, и на которой
 * есть безопасное место для стояния, — это край острова в этом направлении.
 */
object EdgeRespawn {
	private val LOGGER = LoggerFactory.getLogger("islandworld/edge")

	/** Шаг вдоль луча, блоки. */
	private const val RAY_STEP = 4

	private val pending = HashSet<UUID>()

	fun markPending(uuid: UUID) {
		pending.add(uuid)
	}

	fun clear(uuid: UUID) {
		pending.remove(uuid)
	}

	/** Вызывается на AFTER_RESPAWN. Переносит игрока, только если его смерть была от границы. */
	fun handleRespawn(player: ServerPlayer) {
		if (!pending.remove(player.uuid)) return

		val level = player.level()
		val spot = findRandomEdge(level)
		if (spot == null) {
			LOGGER.warn("Край острова не найден, игрок {} остаётся на обычном респавне", player.name.string)
			return
		}
		player.teleportTo(
			level,
			spot.x + 0.5,
			spot.y.toDouble(),
			spot.z + 0.5,
			emptySet(),
			0f,
			0f,
			false
		)
		LOGGER.info("Игрок {} респавнен на краю острова: {}", player.name.string, spot)
	}

	fun reset() {
		pending.clear()
	}

	/** Случайная точка края острова или null, если за заданное число попыток не нашли. */
	fun findRandomEdge(level: ServerLevel): BlockPos? {
		val cfg = BoundaryManager.respawn
		val boundary = BoundaryManager.cfg
		val random = level.random
		for (attempt in 0 until cfg.attempts) {
			val angle = random.nextDouble() * 2.0 * PI
			findOnRay(level, angle, boundary.centerX, boundary.centerZ, cfg.maxRadius, cfg.coastCheckRadius)
				?.let { return it }
		}
		return null
	}

	/** Идём от maxRadius к центру по лучу; первая подходящая точка и есть край острова в этом направлении. */
	private fun findOnRay(
		level: ServerLevel,
		angle: Double,
		cx: Double,
		cz: Double,
		maxRadius: Double,
		coastRadius: Int,
	): BlockPos? {
		val dx = cos(angle)
		val dz = sin(angle)
		var r = maxRadius
		while (r >= 0) {
			val x = floor(cx + dx * r).toInt()
			val z = floor(cz + dz * r).toInt()
			if (!isOcean(level, x, z) && isCoast(level, x, z, dx, dz, coastRadius)) {
				safeSpot(level, x, z)?.let { return it }
			}
			r -= RAY_STEP
		}
		return null
	}

	/** Есть ли океан в пределах coastRadius за точкой по лучу. */
	private fun isCoast(level: ServerLevel, x: Int, z: Int, dx: Double, dz: Double, coastRadius: Int): Boolean {
		var d = RAY_STEP
		while (d <= coastRadius) {
			val ox = floor(x + 0.5 + dx * d).toInt()
			val oz = floor(z + 0.5 + dz * d).toInt()
			if (isOcean(level, ox, oz)) return true
			d += RAY_STEP
		}
		return false
	}

	private fun isOcean(level: ServerLevel, x: Int, z: Int): Boolean =
		SpawnUtil.isOceanAt(level, x, z)

	/**
	 * Безопасная точка стоянки: опора под ногами — земля/песок/трава (те же правила, что у
	 * стартовой точки, см. [SpawnUtil]), точка и место над головой свободны, дерева сверху нет.
	 *
	 * Раньше здесь принимался любой непустой блок, включая камень и гравий, — на каменистом
	 * обрыве игрок мог оказаться на узком уступе. Теперь требования строже.
	 *
	 * Поиск привязан к поверхности (WORLD_SURFACE), а не к скану вниз по колонке: иначе
	 * на обрыве можно было найти «место» в пещере под ним.
	 */
	private fun safeSpot(level: ServerLevel, x: Int, z: Int): BlockPos? {
		val surface = BlockPos(x, level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1, z)
		if (!SpawnUtil.isGround(level.getBlockState(surface))) return null

		val feet = surface.above()
		if (!SpawnUtil.isPassable(level, feet)) return null
		if (!SpawnUtil.isPassable(level, feet.above())) return null
		if (SpawnUtil.hasTreeAbove(level, feet)) return null
		return feet
	}
}
