package com.thelameres.islandworld.worldgen.blackroom

import com.thelameres.islandworld.core.Registrable
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.Mob
import net.minecraft.world.level.Level

/**
 * Защита «Чёрной комнаты» (план, раздел 5.6):
 * запрет ломания и установки блоков, отключение спавна мобов,
 * респавн игрока на острове.
 */
object BlackRoomProtection : Registrable {

    override fun register() {
        // Запрет ломания блоков
        PlayerBlockBreakEvents.BEFORE.register { level, _, _, _, _ ->
            level.dimension() != BlackRoom.DIMENSION
        }

        // Запрет установки и использования блоков
        UseBlockCallback.EVENT.register { _, level, _, _ ->
            if (level.dimension() == BlackRoom.DIMENSION) InteractionResult.FAIL
            else InteractionResult.PASS
        }

        // Отключение спавна мобов: при загрузке сущности моб удаляется
        ServerEntityEvents.ENTITY_LOAD.register { entity: Entity, level: ServerLevel ->
            if (level.dimension() == BlackRoom.DIMENSION && entity is Mob) {
                entity.discard()
            }
        }

        // Респавн: вернуть игрока на остров (точка мирового спавна Overworld)
        ServerPlayerEvents.AFTER_RESPAWN.register { _, newPlayer: ServerPlayer, _ ->
            val level = newPlayer.level()
            val server = level.server
            if (level.dimension() == BlackRoom.DIMENSION) {
                val overworld = server.getLevel(Level.OVERWORLD) ?: return@register

                val spawn: BlockPos =
                    if (newPlayer.respawnConfig != null) newPlayer.respawnConfig!!.respawnData.pos() else server.respawnData.pos()
                newPlayer.teleportTo(
                    overworld,
                    spawn.x + 0.5, spawn.y.toDouble(), spawn.z + 0.5,
                    emptySet(), 0f, 0f, false,
                )
            }
        }
    }
}
