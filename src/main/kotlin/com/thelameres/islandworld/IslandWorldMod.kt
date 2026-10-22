package com.thelameres.islandworld

import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents
import net.minecraft.resources.Identifier
import net.minecraft.world.level.Level
import org.slf4j.Logger
import org.slf4j.LoggerFactory

object IslandWorldMod : ModInitializer {
	const val MOD_ID: String = "islandworld"

	val LOGGER: Logger = LoggerFactory.getLogger(MOD_ID)

	override fun onInitialize() {
		com.thelameres.islandworld.worldgen.density.ModDensityFunctions.register()
		ServerLevelEvents.LOAD.register { _, level ->
			if (level.dimension() == Level.OVERWORLD) {
				com.thelameres.islandworld.util.SpawnUtil.setIslandSpawn(level)
			}
		}
		LOGGER.info("Island World initialised (worldgen stage 1)")
	}

	fun id(path: String): Identifier = Identifier.fromNamespaceAndPath(MOD_ID, path)
}
