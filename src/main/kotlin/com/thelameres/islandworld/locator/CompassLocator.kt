package com.thelameres.islandworld.locator

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
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

    fun register() {
        ServerTickEvents.START_LEVEL_TICK.register { server ->
            clear()
            server
                .players()
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

            server.players()
                .filter { player ->
                    player.isHoldingCompass() && player.isAtVanillaDimensions()
                }
                .forEach { player ->
                    //TODO: Send to players infos about other players
                    //ServerPlayNetworking.send(player)
                }
        }
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


