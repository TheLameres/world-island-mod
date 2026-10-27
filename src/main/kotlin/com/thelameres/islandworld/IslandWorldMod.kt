package com.thelameres.islandworld

import com.thelameres.islandworld.border.BoundaryManager
import com.thelameres.islandworld.core.Lifecycle
import com.thelameres.islandworld.core.Registrable
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
     * Подсистемы в порядке подключения.
     *
     * Порядок значим: общие подписки ([Lifecycle]) идут первыми, чтобы DISCONNECT
     * и SERVER_STOPPED были зарегистрированы раньше, чем частные обработчики подсистем.
     * Генерация мира не зависит от остальных, но держится в начале списка, чтобы
     * реестры заполнялись до того, как мир начнёт загружаться.
     */
    private val subsystems: List<Registrable> = listOf(
        // Общие подписки на жизненный цикл сервера
        Lifecycle,

        // Генерация мира
        ModDensityFunctions,
        OceanConcentricRingsPlacement,
        BlackRoomChunkGenerator,
        BlackRoomWrapAround,
        BlackRoomProtection,

        // LocatorBar и waypoint-метки
        CompassLocator,
        WaypointStore,
        WaypointInteraction,

        // Игровые подсистемы
        FirstJoinSpawn,
        BoundaryManager,
    )

    override fun onInitialize() {
        subsystems.forEach { it.register() }

        LOGGER.info("Island World initialised (worldgen stage 1, boundary 2000)")
    }

    fun id(path: String): Identifier {
        return Identifier.fromNamespaceAndPath(MOD_ID, path)
    }
}
