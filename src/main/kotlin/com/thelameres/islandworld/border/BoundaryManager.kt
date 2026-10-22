package com.thelameres.islandworld.border

import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import java.util.UUID
import kotlin.math.hypot

/**
 * Граница 2000 блоков от центра (евклидово расстояние по X/Z).
 * Три предупреждения (1800 / 1900 / 1950), затем смерть на 2000 после задержки.
 */
object BoundaryManager {
	private val states = HashMap<UUID, PlayerBoundaryState>()

	lateinit var config: IslandConfigFile
		private set

	val cfg: BoundaryConfig get() = config.boundary
	val respawn: RespawnConfig get() = config.respawn

	fun init(file: IslandConfigFile) {
		config = file
	}

	fun tick(server: MinecraftServer) {
		if (server.tickCount % cfg.checkIntervalTicks != 0) return
		for (player in server.overworld().players()) {
			tickPlayer(player)
		}
	}

	private fun tickPlayer(player: ServerPlayer) {
		val st = states.getOrPut(player.uuid) { PlayerBoundaryState() }
		val dist = hypot(player.x - cfg.centerX, player.z - cfg.centerZ)

		// Возврат в безопасную зону сбрасывает счётчики
		if (dist < cfg.warn1Radius - cfg.resetRadiusMargin) {
			st.warningsIssued = 0
			st.beyondKillTicks = 0
			return
		}

		// Предупреждения выдаются строго по порядку, даже если игрок перескочил несколько порогов
		val thresholds = doubleArrayOf(cfg.warn1Radius, cfg.warn2Radius, cfg.warn3Radius)
		while (st.warningsIssued < 3 && dist >= thresholds[st.warningsIssued]) {
			st.warningsIssued++
			sendWarning(player, st.warningsIssued)
		}

		// Смерть только после всех трёх предупреждений и задержки за границей 2000
		if (dist >= cfg.killRadius && st.warningsIssued == 3 && !isProtected(player)) {
			st.beyondKillTicks += cfg.checkIntervalTicks
			if (st.beyondKillTicks >= cfg.killDelayTicks) {
				UnknownDeath.kill(player)
				states.remove(player.uuid)
			}
		} else {
			st.beyondKillTicks = 0
		}
	}

	private fun sendWarning(player: ServerPlayer, stage: Int) {
		if (cfg.debugMessages) {
			player.sendOverlayMessage(Component.literal("[Boundary] стадия $stage"))
		}
		config.hallucinations["stage$stage"]?.let { Hallucination.apply(player, it) }
	}

	/** Творческий режим и наблюдатель не получают урон от границы. */
	private fun isProtected(player: ServerPlayer): Boolean =
		player.isCreative || player.isSpectator

	fun onDisconnect(uuid: UUID) {
		states.remove(uuid)
		EdgeRespawn.clear(uuid)
	}

	fun onRespawn(uuid: UUID) {
		states.remove(uuid)
	}
}

class PlayerBoundaryState {
	var warningsIssued = 0
	var beyondKillTicks = 0
}
