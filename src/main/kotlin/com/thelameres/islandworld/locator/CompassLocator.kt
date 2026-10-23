package com.thelameres.islandworld.locator

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.Items
import net.minecraft.world.level.Level
import java.util.*

object CompassLocator {

    val playerListAtOverworld: MutableMap<UUID, Player> = mutableMapOf()
    val playerListAtNether: MutableMap<UUID, Player> = mutableMapOf()
    val playerListAtEnd: MutableMap<UUID, Player> = mutableMapOf()

    private val vanillaDimensions = setOf(Level.OVERWORLD, Level.NETHER, Level.END)

    /** Отправка позиций раз в 4 тика (5 раз в секунду) — достаточно для плавного LocatorBar. */
    private const val SEND_INTERVAL_TICKS = 4
    private var tickCounter = 0

    /** Игроки, которым отправляли непустой список на прошлом цикле; нужны для очистки бара. */
    private var previousHolders: Set<UUID> = emptySet()

    fun register() {
        // Карты очищаем один раз за серверный тик, а не на каждом уровне,
        // иначе после обработки последнего измерения в картах осталось бы только его содержимое.
        ServerTickEvents.START_SERVER_TICK.register { _ ->
            clear()
        }

        // Заполняем карты по каждому уровню (ServerLevel.players() — метод уровня, не сервера)
        ServerTickEvents.START_LEVEL_TICK.register { level ->
            level.players()
                .filter { player ->
                    player.getHeadItem() != Items.PLAYER_HEAD
                }
                .forEach { player ->
                    when (player.level().dimension()) {
                        Level.OVERWORLD -> playerListAtOverworld[player.uuid] = player
                        Level.NETHER -> playerListAtNether[player.uuid] = player
                        Level.END -> playerListAtEnd[player.uuid] = player
                    }
                }
        }

        ServerTickEvents.END_SERVER_TICK.register { server ->
            tickCounter++
            if (tickCounter < SEND_INTERVAL_TICKS) return@register
            tickCounter = 0
            sendPositions(server)
        }
    }

    /**
     * Отправляет каждому игроку с компасом (в обычных измерениях) список других видимых игроков
     * того же измерения. Источник данных — карты playerListAt*.
     */
    private fun sendPositions(server: MinecraftServer) {
        val everyone = allPlayers()
        val currentHolders = mutableSetOf<UUID>()

        everyone
            .filter { it.isHoldingCompass() && it.isAtVanillaDimensions() }
            .forEach { receiver ->
                val serverReceiver = receiver as? ServerPlayer ?: return@forEach
                currentHolders.add(receiver.uuid)

                val dimension = receiver.level().dimension()
                val entries = everyone
                    .filter { it.uuid != receiver.uuid }
                    .filter { it.level().dimension() == dimension }
                    .map { it.toLocatorEntry() }

                ServerPlayNetworking.send(serverReceiver, PlayerPositionsPayload(entries))
            }

        // Тем, у кого компас только что убрали, отправляем пустой список — бар на клиенте очищается
        (previousHolders - currentHolders).forEach { uuid ->
            server.playerList.getPlayer(uuid)?.let { player ->
                ServerPlayNetworking.send(player, PlayerPositionsPayload(emptyList()))
            }
        }
        previousHolders = currentHolders
    }

    private fun allPlayers(): List<Player> =
        playerListAtOverworld.values + playerListAtNether.values + playerListAtEnd.values

    private fun Player.toLocatorEntry(): LocatorEntry {
        val pos = blockPosition()
        return LocatorEntry(
            uuid = uuid,
            name = gameProfile.name,
            x = pos.x,
            y = pos.y,
            z = pos.z,
            dimension = level().dimension().identifier(),
        )
    }

    private fun clear() {
        playerListAtOverworld.clear()
        playerListAtNether.clear()
        playerListAtEnd.clear()
    }

    private fun Player.isHoldingCompass(): Boolean =
        this.mainHandItem.item == Items.COMPASS || this.offhandItem.item == Items.COMPASS

    private fun Player.isAtVanillaDimensions(): Boolean =
        vanillaDimensions.contains(this.level().dimension())

    private fun Player.getHeadItem(): Item = this.getItemBySlot(EquipmentSlot.HEAD).item
}
