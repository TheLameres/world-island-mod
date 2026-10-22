package com.thelameres.islandworld

import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.Relative
import org.slf4j.Logger
import org.slf4j.LoggerFactory

object IslandWorldMod : ModInitializer {
	const val MOD_ID: String = "islandworld"

	val LOGGER: Logger = LoggerFactory.getLogger(MOD_ID)

	override fun onInitialize() {
		com.thelameres.islandworld.worldgen.density.ModDensityFunctions.register()

		// Первый вход игрока: телепорт на берег океана у (-930, 600)
		ServerPlayConnectionEvents.JOIN.register { handler, _, server ->
			val player = handler.player
			if (com.thelameres.islandworld.util.FirstJoinStore.hasJoined(server, player.uuid)) return@register

			val level = server.overworld()
			val spawn = com.thelameres.islandworld.util.SpawnUtil.findPlayerSpawn(level)
			if (spawn == null) {
				// Не отмечаем игрока, чтобы попробовать снова при следующем входе
				LOGGER.warn("Стартовая точка для {} не найдена", player.name.string)
				return@register
			}

			player.teleportTo(
				level,
				spawn.x + 0.5,
				spawn.y.toDouble(),
				spawn.z + 0.5,
				emptySet<Relative>(),
				0f,
				0f,
				false
			)
			com.thelameres.islandworld.util.FirstJoinStore.markJoined(server, player.uuid)
			LOGGER.info("Игрок {} получил стартовую точку {}", player.name.string, spawn)
		}

		ServerLifecycleEvents.SERVER_STOPPED.register { _ ->
			com.thelameres.islandworld.util.SpawnUtil.reset()
		}

		LOGGER.info("Island World initialised (worldgen stage 1)")
	}

	fun id(path: String): Identifier = Identifier.fromNamespaceAndPath(MOD_ID, path)
}
