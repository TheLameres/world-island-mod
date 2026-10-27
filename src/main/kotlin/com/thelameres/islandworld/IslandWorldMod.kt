package com.thelameres.islandworld

import com.thelameres.islandworld.border.BoundaryManager
import com.thelameres.islandworld.border.BoundaryManager.onDisconnect
import com.thelameres.islandworld.border.EdgeRespawn
import com.thelameres.islandworld.border.IslandConfigLoader
import com.thelameres.islandworld.locator.WaypointInteraction
import com.thelameres.islandworld.locator.WaypointRegistry
import com.thelameres.islandworld.util.FirstJoinStore
import com.thelameres.islandworld.util.SpawnUtil
import com.thelameres.islandworld.worldgen.density.ModDensityFunctions
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.resources.Identifier
import net.minecraft.world.level.storage.LevelData
import org.slf4j.Logger
import org.slf4j.LoggerFactory


object IslandWorldMod : ModInitializer {
    const val MOD_ID: String = "islandworld"

    val LOGGER: Logger = LoggerFactory.getLogger(MOD_ID)

    override fun onInitialize() {
        ModDensityFunctions.register()

        // LocatorBar: регистрация payload и периодическая отправка позиций игрокам с компасом
        com.thelameres.islandworld.locator.CompassLocator.register()
        WaypointInteraction.register()

        // Стронгхолды только в океане (план, раздел 3.1)
        com.thelameres.islandworld.worldgen.structure.OceanConcentricRingsPlacement.register()

        // Чёрная комната (план, раздел 5)
        com.thelameres.islandworld.worldgen.blackroom.BlackRoomChunkGenerator.register()
        com.thelameres.islandworld.worldgen.blackroom.BlackRoomWrapAround.register()
        com.thelameres.islandworld.worldgen.blackroom.BlackRoomProtection.register()

        // Первый вход игрока: телепорт на берег океана у (-930, 600)
        ServerPlayConnectionEvents.JOIN.register { handler, _, server ->
            val player = handler.player
            if (FirstJoinStore.hasJoined(server, player.uuid)) return@register

            val level = server.overworld()
            val spawn = SpawnUtil.findPlayerSpawn(level)
            if (spawn == null) {
                // Не отмечаем игрока, чтобы попробовать снова при следующем входе
                LOGGER.warn("Стартовая точка для {} не найдена", player.name.string)
                return@register
            }

            player.teleportTo(
                level, spawn.x + 0.5, spawn.y.toDouble(), spawn.z + 0.5, emptySet(), 0f, 0f, false
            )

            server.respawnData = LevelData.RespawnData.of(level.dimension(), spawn, 0f, 0f)

            FirstJoinStore.markJoined(server, player.uuid)
            LOGGER.info("Игрок {} получил стартовую точку {}", player.name.string, spawn)
        }

        ServerLifecycleEvents.SERVER_STOPPED.register { _ ->
            SpawnUtil.reset()
            EdgeRespawn.reset()
        }

        // Waypoint-метки: после старта возвращаем сохранённые точки в WaypointManager.
        // Реестр живёт в SavedData, но менеджер создаётся заново и о точках не знает,
        // пока чанки не загрузятся. Без этого метки пропадали бы до первого визита чанка.
        ServerLifecycleEvents.SERVER_STARTED.register { server ->
            for (level in server.allLevels) {
                WaypointRegistry.of(level).attach(level)
            }
        }

        // Граница 2000 блоков: конфиг, тики, отключение, респавн
        BoundaryManager.init(
            IslandConfigLoader.load()
        )
        ServerTickEvents.END_SERVER_TICK.register { server ->
            BoundaryManager.tick(server)
        }
        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ ->
            onDisconnect(handler.player.uuid)
        }
        ServerPlayerEvents.AFTER_RESPAWN.register { oldPlayer, newPlayer, _ ->
            BoundaryManager.onRespawn(newPlayer.uuid)
            EdgeRespawn.handleRespawn(newPlayer)
        }

        LOGGER.info("Island World initialised (worldgen stage 1, boundary 2000)")
    }

    fun id(path: String): Identifier = Identifier.fromNamespaceAndPath(MOD_ID, path)
}
