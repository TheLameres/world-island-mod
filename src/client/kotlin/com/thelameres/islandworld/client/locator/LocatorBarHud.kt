package com.thelameres.islandworld.client.locator

import com.thelameres.islandworld.IslandWorldMod
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements
import net.minecraft.resources.Identifier
import net.minecraft.world.item.Items

/**
 * Отрисовка собственного LocatorBar: полоска над хотбаром, на которой отмечены
 * игроки из [CompassLocatorClient.currentEntries].
 *
 * Отображается только при компасе в main hand или off hand. Нативный LocatorBar
 * управляется серверными атрибутами и этот HUD не заменяет.
 * CompassCoordinatesHud не затрагивается.
 */
object LocatorBarHud {

    private val ELEMENT_ID: Identifier = IslandWorldMod.id("locator_bar")

    /** Половина ширины полоски в пикселях. */
    private const val HALF_WIDTH = 91

    /** Угол обзора, в пределах которого маркер показывается (±градусов от взгляда). */
    private const val FOV_HALF_DEGREES = 90f

    /** Отступ от низа экрана до полоски, чтобы не перекрывать хотбар. */
    private const val BOTTOM_OFFSET = 36

    private const val BAR_COLOR = 0x80000000.toInt()
    private const val TICK_COLOR = 0xFFFFFFFF.toInt()
    private const val TEXT_COLOR = 0xFFFFFFFF.toInt()

    fun register() {
        HudElementRegistry.attachElementBefore(
            VanillaHudElements.CHAT,
            ELEMENT_ID,
        ) { graphics, _ -> render(graphics) }
    }

    private fun render(graphics: net.minecraft.client.gui.GuiGraphicsExtractor) {
//        val mc = Minecraft.getInstance()
//        val player = mc.player ?: return
//        val level = mc.level ?: return
//
//        if (!player.isHoldingCompass()) return
//
//        val entries = CompassLocatorClient.currentEntries()
//        if (entries.isEmpty()) return
//
//        val currentDimension = level.dimension().identifier()
//        val screenWidth = mc.window.guiScaledWidth
//        val screenHeight = mc.window.guiScaledHeight
//        val centerX = screenWidth / 2
//        val barY = screenHeight - BOTTOM_OFFSET
//
//
//        graphics.fill(centerX - HALF_WIDTH, barY, centerX + HALF_WIDTH, barY + 2, BAR_COLOR)
//        graphics.fill(centerX, barY - 3, centerX + 1, barY + 5, TICK_COLOR)
//
//        for (entry in entries) {
//            if (entry.dimension != currentDimension) continue
//
//            val dx = (entry.x + 0.5) - player.x
//            val dy = (entry.y + 0.5) - player.y
//            val dz = (entry.z + 0.5) - player.z
//
//            // Азимут до цели в системе yaw Minecraft: направление взгляда = (-sin(yaw), cos(yaw))
//            val targetYaw = Math.toDegrees(atan2(-dx, dz)).toFloat()
//            val relative = Mth.wrapDegrees(targetYaw - player.yRot)
//            if (kotlin.math.abs(relative) > FOV_HALF_DEGREES) continue
//
//            val markerX = centerX + (relative / FOV_HALF_DEGREES * HALF_WIDTH).toInt()
//            val distance = sqrt(dx * dx + dy * dy + dz * dz).toInt()
//
//            graphics.fill(markerX - 1, barY - 2, markerX + 2, barY + 4, TEXT_COLOR)
//            graphics.text(
//                mc.font,
//                "${entry.name} ${distance}м",
//                markerX - 20,
//                barY - 12,
//                TEXT_COLOR,
//                true,
//            )
//        }
    }

    private fun net.minecraft.world.entity.player.Player.isHoldingCompass(): Boolean =
        mainHandItem.`is`(Items.COMPASS) || offhandItem.`is`(Items.COMPASS)
}
