package com.thelameres.islandworld.locator

import com.thelameres.islandworld.IslandWorldMod
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking
import net.minecraft.resources.Identifier
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.ai.attributes.AttributeInstance
import net.minecraft.world.entity.ai.attributes.AttributeModifier
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.Items
import net.minecraft.world.level.Level
import java.util.*

/**
 * Единая серверная точка LocatorBar:
 *
 * 1. Атрибуты нативного LocatorBar:
 *    - `waypoint_receive_range` обнуляется без компаса в руке (main/off hand);
 *    - `waypoint_transmit_range` обнуляется, если на игроке голова `PLAYER_HEAD`.
 * 2. Собственный канал: раз в [SEND_INTERVAL_TICKS] тиков каждому игроку с компасом
 *    отправляется [PlayerPositionsPayload] со списком других игроков того же измерения.
 *
 * Карты `playerListAt*` заполняются только игроками без `PLAYER_HEAD`, поэтому
 * для атрибутов обход идёт по `server.playerList.players`, а не по картам.
 */
object CompassLocator {

    val playerListAtOverworld: MutableMap<UUID, Player> = mutableMapOf()
    val playerListAtNether: MutableMap<UUID, Player> = mutableMapOf()
    val playerListAtEnd: MutableMap<UUID, Player> = mutableMapOf()

    private val vanillaDimensions = setOf(Level.OVERWORLD, Level.NETHER, Level.END)

    private const val SEND_INTERVAL_TICKS = 4
    private var tickCounter = 0

    /** Игроки, которым на прошлом цикле отправлялся непустой список; нужны для очистки бара. */
    private var previousHolders: Set<UUID> = emptySet()

    private val RECEIVE_HIDDEN_ID: Identifier =
        Identifier.fromNamespaceAndPath(IslandWorldMod.MOD_ID, "locator_receive_hidden")
    private val TRANSMIT_HIDDEN_ID: Identifier =
        Identifier.fromNamespaceAndPath(IslandWorldMod.MOD_ID, "locator_transmit_hidden")

    /** ADD_MULTIPLIED_TOTAL с -1 даёт итоговое значение 0: значение *= (1 + (-1)). */
    private const val ZERO_MULTIPLIER = -1.0

    /** Последнее применённое состояние атрибутов (receive, transmit) по UUID. */
    private val lastAttributeState: MutableMap<UUID, Pair<Boolean, Boolean>> = mutableMapOf()

    fun register() {
        // Карты очищаем один раз за серверный тик, а не на каждом уровне.
        ServerTickEvents.START_SERVER_TICK.register { _ ->
            clear()
        }

        // ServerLevel.players() — метод уровня, не сервера
        ServerTickEvents.START_LEVEL_TICK.register { level ->
            level.players().filter { it.getHeadItem() != Items.PLAYER_HEAD }.forEach { player ->
                when (player.level().dimension()) {
                    Level.OVERWORLD -> playerListAtOverworld[player.uuid] = player
                    Level.NETHER -> playerListAtNether[player.uuid] = player
                    Level.END -> playerListAtEnd[player.uuid] = player
                }
            }
        }

        ServerTickEvents.END_SERVER_TICK.register { server ->
            syncWaypointAttributes(server)

            tickCounter++
            if (tickCounter < SEND_INTERVAL_TICKS) return@register
            tickCounter = 0
            sendPositions(server)
        }

        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ ->
            lastAttributeState.remove(handler.player.uuid)
        }
    }

    /** Атрибуты нативного LocatorBar: обходим всех игроков сервера, включая тех, кто в PLAYER_HEAD. */
    private fun syncWaypointAttributes(server: MinecraftServer) {
        val players = server.playerList.players
        lastAttributeState.keys.retainAll(players.map { it.uuid }.toSet())

        players.forEach { player ->
            val receiveVisible = player.isHoldingCompass()
            val transmitVisible = !player.isWearingPlayerHead()
            val state = receiveVisible to transmitVisible

            if (lastAttributeState[player.uuid] == state) return@forEach

            applyModifier(player.getAttribute(Attributes.WAYPOINT_RECEIVE_RANGE), RECEIVE_HIDDEN_ID, receiveVisible)
            applyModifier(player.getAttribute(Attributes.WAYPOINT_TRANSMIT_RANGE), TRANSMIT_HIDDEN_ID, transmitVisible)
            lastAttributeState[player.uuid] = state
        }
    }

    private fun applyModifier(instance: AttributeInstance?, id: Identifier, visible: Boolean) {
        instance ?: return
        instance.removeModifier(id)
        if (!visible) {
            instance.addTransientModifier(
                AttributeModifier(id, ZERO_MULTIPLIER, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
            )
        }
    }

    /**
     * Отправляет каждому игроку с компасом (в обычных измерениях) список других видимых игроков
     * того же измерения. Источник данных — карты playerListAt*.
     */
    private fun sendPositions(server: MinecraftServer) {
        val everyone = allPlayers()
        val currentHolders = mutableSetOf<UUID>()

        everyone.filter { it.isHoldingCompass() && it.isAtVanillaDimensions() }.forEach { receiver ->
            val serverReceiver = receiver as? ServerPlayer ?: return@forEach
            currentHolders.add(receiver.uuid)

            val dimension = receiver.level().dimension()
            val entries =
                everyone.filter { it.uuid != receiver.uuid }.filter { it.level().dimension() == dimension }
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

    private fun clear() {
        playerListAtOverworld.clear()
        playerListAtNether.clear()
        playerListAtEnd.clear()
    }

    private fun Player.isHoldingCompass(): Boolean = mainHandItem.`is`(Items.COMPASS) || offhandItem.`is`(Items.COMPASS)

    private fun Player.isWearingPlayerHead(): Boolean = getItemBySlot(EquipmentSlot.HEAD).`is`(Items.PLAYER_HEAD)

    private fun Player.isAtVanillaDimensions(): Boolean = vanillaDimensions.contains(level().dimension())

    private fun Player.getHeadItem(): Item = getItemBySlot(EquipmentSlot.HEAD).item

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
}
