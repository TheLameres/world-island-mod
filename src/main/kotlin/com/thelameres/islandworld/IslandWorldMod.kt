package com.thelameres.islandworld

import com.thelameres.islandworld.border.BoundaryManager
import com.thelameres.islandworld.locator.CompassLocator
import com.thelameres.islandworld.locator.WaypointInteraction
import com.thelameres.islandworld.locator.WaypointStore
import com.thelameres.islandworld.player.FirstJoinSpawn
import com.thelameres.islandworld.worldgen.blackroom.BlackRoomChunkGenerator
import com.thelameres.islandworld.worldgen.blackroom.BlackRoomProtection
import com.thelameres.islandworld.worldgen.blackroom.BlackRoomWrapAround
import com.thelameres.islandworld.worldgen.density.ModDensityFunctions
import com.thelameres.islandworld.worldgen.structure.OceanConcentricRingsPlacement
import net.fabricmc.api.ModInitializer
import net.minecraft.resources.Identifier
import org.slf4j.Logger
import org.slf4j.LoggerFactory


object IslandWorldMod : ModInitializer {
    const val MOD_ID: String = "islandworld"

    val LOGGER: Logger = LoggerFactory.getLogger(MOD_ID)

    /**
     * Точка сборки: здесь только register-вызовы. Каждая подсистема сама решает,
     * на какие события подписаться и как жить в их рамках — см. её register().
     */
    override fun onInitialize() {
        // Генерация мира
        ModDensityFunctions.register()
        OceanConcentricRingsPlacement.register()
        BlackRoomChunkGenerator.register()
        BlackRoomWrapAround.register()
        BlackRoomProtection.register()

        // LocatorBar и waypoint-метки
        CompassLocator.register()
        WaypointStore.register()
        WaypointInteraction.register()

        // Игровые подсистемы
        FirstJoinSpawn.register()
        BoundaryManager.register()

        LOGGER.info("Island World initialised (worldgen stage 1, boundary 2000)")
    }

    fun id(path: String): Identifier = Identifier.fromNamespaceAndPath(MOD_ID, path)
}
