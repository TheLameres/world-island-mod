package com.thelameres.islandworld.client.hud

import com.thelameres.islandworld.IslandWorldMod
import com.thelameres.islandworld.client.hud.CompassCoordinatesHud.REFRESH_TICKS
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.world.item.Items
import net.minecraft.world.level.Level
import kotlin.random.Random

/**
 * HUD-элемент: если игрок держит компас (в основной или во второй руке),
 * в левом верхнем углу выводятся его координаты.
 *
 * - Ванильные измерения (overworld / nether / end) — реальные координаты.
 * - Любые другие измерения (кастомные миры, black_room и т.д.) — случайные числа,
 *   которые обновляются каждые [REFRESH_TICKS] тиков.
 */
object CompassCoordinatesHud {
    private val ELEMENT_ID: Identifier =
        Identifier.fromNamespaceAndPath(IslandWorldMod.MOD_ID, "compass_coordinates")

    private const val REFRESH_TICKS = 5
    private const val RANDOM_XZ_RANGE = 30_000_000

    private val VANILLA_DIMENSIONS = setOf(Level.OVERWORLD, Level.NETHER, Level.END)

    private var tickCounter = 0
    private var randomX = 0
    private var randomY = 0
    private var randomZ = 0

    fun register() {
        ClientTickEvents.END_CLIENT_TICK.register { _ ->
            tickCounter++
            if (tickCounter >= REFRESH_TICKS) {
                tickCounter = 0
                regenerateRandom()
                render()
            }
        }
    }

    private fun regenerateRandom() {
        randomX = Random.nextInt(-RANDOM_XZ_RANGE, RANDOM_XZ_RANGE)
        randomY = Random.nextInt(-64, 321)
        randomZ = Random.nextInt(-RANDOM_XZ_RANGE, RANDOM_XZ_RANGE)
    }

    private fun render() {
        val mc = Minecraft.getInstance()
        val player: LocalPlayer = mc.player ?: return
        val level = mc.level ?: return

        if (!isHoldingCompass(player)) return

        val (x, y, z) = if (level.dimension() in VANILLA_DIMENSIONS) {
            val pos = player.blockPosition()
            Triple(pos.x, pos.y, pos.z)
        } else {
            Triple(randomX, randomY, randomZ)
        }
        val message = Component.literal("$x $y $z")
        player.sendOverlayMessage(message)
    }

    private fun isHoldingCompass(player: LocalPlayer): Boolean =
        player.mainHandItem.item == Items.COMPASS || player.offhandItem.item == Items.COMPASS
}
