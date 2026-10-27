package com.thelameres.islandworld.border

import com.thelameres.islandworld.IslandWorldMod
import net.minecraft.core.Holder
import net.minecraft.core.registries.Registries
import net.minecraft.network.protocol.game.ClientboundSoundPacket
import net.minecraft.resources.ResourceKey
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundSource
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.damagesource.DamageType

/** Смерть от неизвестной сущности: звуки + урон islandworld:unknown + отметка на респавн у края острова. */
object UnknownDeath {
    val DAMAGE_TYPE_KEY: ResourceKey<DamageType> =
        ResourceKey.create(Registries.DAMAGE_TYPE, IslandWorldMod.id("unknown"))

    private val VICTIM_SOUND: Holder<SoundEvent> =
        Holder.direct(SoundEvent.createVariableRangeEvent(IslandWorldMod.id("unknown_death")))
    private val DISTANT_SOUND: Holder<SoundEvent> =
        Holder.direct(SoundEvent.createVariableRangeEvent(IslandWorldMod.id("unknown_death_distant")))

    fun kill(player: ServerPlayer) {
        val level: ServerLevel = player.level()
        val seed = level.random.nextLong()

        // 1. Жертва слышит звук без позиции
        player.connection.send(
            ClientboundSoundPacket(VICTIM_SOUND, SoundSource.MASTER, player.x, player.y, player.z, 1.0f, 1.0f, seed)
        )

        // 2. Остальные игроки слышат позиционный звук в точке смерти
        for (other in level.server.playerList.players) {
            if (other === player) continue
            other.connection.send(
                ClientboundSoundPacket(
                    DISTANT_SOUND,
                    SoundSource.HOSTILE,
                    player.x,
                    player.y,
                    player.z,
                    1.0f,
                    1.0f,
                    seed
                )
            )
        }

        // 3. Отмечаем игрока: после респавна его перенесёт на случайный край острова
        EdgeRespawn.markPending(player.uuid)

        // 4. Урон (тип islandworld:unknown обходит бессмертие через тег)
        val holder = level.registryAccess()
            .lookupOrThrow(Registries.DAMAGE_TYPE)
            .getOrThrow(DAMAGE_TYPE_KEY)
        player.hurtServer(level, DamageSource(holder), Float.MAX_VALUE)
    }
}
