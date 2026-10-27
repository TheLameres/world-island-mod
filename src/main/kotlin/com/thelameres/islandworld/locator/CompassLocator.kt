package com.thelameres.islandworld.locator

import com.thelameres.islandworld.IslandWorldMod
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.resources.Identifier
import net.minecraft.server.MinecraftServer
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.ai.attributes.AttributeInstance
import net.minecraft.world.entity.ai.attributes.AttributeModifier
import net.minecraft.world.entity.ai.attributes.Attributes
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Items
import java.util.*

/**
 * Серверная часть нативного LocatorBar.
 *
 * Управляет атрибутами ванильного локатора:
 *  - `waypoint_receive_range` обнуляется без компаса в руке (main/off hand);
 *  - `waypoint_transmit_range` обнуляется, если на игроке голова `PLAYER_HEAD`.
 *
 * Своего HUD и сетевого канала у мода нет: список игроков строит движок
 * из атрибутов, отрисовывает ваниль.
 */
object CompassLocator {

    private val RECEIVE_HIDDEN_ID: Identifier =
        Identifier.fromNamespaceAndPath(IslandWorldMod.MOD_ID, "locator_receive_hidden")
    private val TRANSMIT_HIDDEN_ID: Identifier =
        Identifier.fromNamespaceAndPath(IslandWorldMod.MOD_ID, "locator_transmit_hidden")

    /** ADD_MULTIPLIED_TOTAL с -1 даёт итоговое значение 0: значение *= (1 + (-1)). */
    private const val ZERO_MULTIPLIER = -1.0

    /** Последнее применённое состояние атрибутов (receive, transmit) по UUID. */
    private val lastAttributeState: MutableMap<UUID, Pair<Boolean, Boolean>> = mutableMapOf()

    fun register() {
        ServerTickEvents.END_SERVER_TICK.register { server ->
            syncWaypointAttributes(server)
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

    private fun Player.isHoldingCompass(): Boolean =
        mainHandItem.`is`(Items.COMPASS) || offhandItem.`is`(Items.COMPASS)

    private fun Player.isWearingPlayerHead(): Boolean =
        getItemBySlot(EquipmentSlot.HEAD).`is`(Items.PLAYER_HEAD)
}
