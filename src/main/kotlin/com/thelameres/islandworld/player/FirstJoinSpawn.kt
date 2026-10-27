package com.thelameres.islandworld.player

import com.thelameres.islandworld.IslandWorldMod
import com.thelameres.islandworld.core.Registrable
import com.thelameres.islandworld.util.FirstJoinStore
import com.thelameres.islandworld.util.SpawnUtil
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.world.level.storage.LevelData

/**
 * Первый вход игрока: телепорт на берег океана у (-930, 600) и отметка в [FirstJoinStore].
 *
 * Вынесено из IslandWorldMod, чтобы `onInitialize` оставался списком register-вызовов,
 * а логика первого входа читалась целиком в одном месте.
 *
 * Кэш стартовой точки в [SpawnUtil] описывает состояние конкретной сессии сервера,
 * поэтому сбрасывается здесь же — на остановке.
 */
object FirstJoinSpawn : Registrable {

    override fun register() {
        ServerPlayConnectionEvents.JOIN.register { handler, _, server ->
            val player = handler.player
            if (FirstJoinStore.hasJoined(server, player.uuid)) return@register

            val level = server.overworld()
            val spawn = SpawnUtil.findPlayerSpawn(level)
            if (spawn == null) {
                // Не отмечаем игрока, чтобы попробовать снова при следующем входе
                IslandWorldMod.LOGGER.warn(
                    "Стартовая точка для {} не найдена",
                    player.name.string
                )
                return@register
            }

            player.teleportTo(
                level,
                spawn.x + 0.5,
                spawn.y.toDouble(),
                spawn.z + 0.5,
                emptySet(),
                0f,
                0f,
                false
            )

            server.respawnData = LevelData.RespawnData.of(level.dimension(), spawn, 0f, 0f)

            FirstJoinStore.markJoined(server, player.uuid)
            IslandWorldMod.LOGGER.info(
                "Игрок {} получил стартовую точку {}",
                player.name.string,
                spawn
            )
        }
    }
}
