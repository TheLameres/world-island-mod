package com.thelameres.islandworld

import net.fabricmc.api.ModInitializer
import net.minecraft.resources.Identifier
import org.slf4j.Logger
import org.slf4j.LoggerFactory

object IslandWorldMod : ModInitializer {
	const val MOD_ID: String = "islandworld"

	val LOGGER: Logger = LoggerFactory.getLogger(MOD_ID)

	override fun onInitialize() {
		com.thelameres.islandworld.worldgen.density.ModDensityFunctions.register()
		LOGGER.info("Island World initialised (worldgen stage 1)")
	}

	fun id(path: String): Identifier = Identifier.fromNamespaceAndPath(MOD_ID, path)
}
