package com.thelameres.islandworld.locator

import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Items
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.entity.BannerBlockEntity
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.SignBlockEntity
import net.minecraft.world.level.block.entity.SignTextSlot
import net.minecraft.world.phys.BlockHitResult

/**
 * Клик компасом по табличке или флагу — включение/выключение waypoint-метки.
 *
 * Логика:
 * - требуется компас в руке, которой кликнули;
 * - обычный ПКМ по табличке без Shift не перехватывается, чтобы не ломать ванильное редактирование;
 * - повторный Shift+ПКМ по той же точке снимает метку.
 *
 * Все сообщения отправляются как Component.translatable: клиент сам подставит текст
 * из assets/islandworld/lang/<locale>.json, поэтому строки не зашиты в код.
 */
object WaypointInteraction {
    private const val KEY_ADDED = "islandworld.waypoint.added"
    private const val KEY_REMOVED = "islandworld.waypoint.removed"
    private const val KEY_DEFAULT_NAME = "islandworld.waypoint.default_name"

    fun register() {
        UseBlockCallback.EVENT.register(UseBlockCallback { player: Player, level: Level, hand: InteractionHand, hit: BlockHitResult ->
            onUseBlock(
                player,
                level,
                hand,
                hit
            )
        })
    }

    private fun onUseBlock(
        player: Player,
        level: Level,
        hand: InteractionHand,
        hit: BlockHitResult
    ): InteractionResult {
        if (level !is ServerLevel) {
            return InteractionResult.PASS
        }
        if (player !is ServerPlayer) {
            return InteractionResult.PASS
        }

        val stack = player.getItemInHand(hand)
        if (!stack.`is`(Items.COMPASS)) {
            return InteractionResult.PASS
        }

        // Shift обязателен: иначе перехватим ванильное открытие таблички
        if (!player.isShiftKeyDown) {
            return InteractionResult.PASS
        }

        val pos = hit.blockPos
        val blockEntity = level.getBlockEntity(pos)
        if (blockEntity !is BlockWaypointTransmitter) {
            return InteractionResult.PASS
        }

        val state: BlockWaypointState = blockEntity.`islandworld$waypointState`()
        state.bind(pos)
        toggle(level, blockEntity, state, blockEntity, player)
        return InteractionResult.SUCCESS_SERVER
    }

    private fun toggle(
        level: ServerLevel, transmitter: BlockWaypointTransmitter,
        state: BlockWaypointState, blockEntity: BlockEntity, player: ServerPlayer
    ) {
        if (state.isEnabled) {
            level.waypointManager.untrackWaypoint(transmitter)
            state.setEnabled(false)
            blockEntity.setChanged()
            player.sendSystemMessage(
                Component.translatable(KEY_REMOVED, displayName(state, blockEntity))
                    .withStyle(ChatFormatting.GRAY)
            )
            return
        }

        // В state храним только «плоское» имя для сравнения; в чат уходит Component,
        // чтобы безымянная точка тоже локализовалась на клиенте.
        state.name = resolveName(blockEntity)?.string
        state.setEnabled(true)
        level.waypointManager.trackWaypoint(transmitter)
        blockEntity.setChanged()

        val pos = blockEntity.blockPos
        player.sendSystemMessage(
            Component.translatable(
                KEY_ADDED,
                displayName(state, blockEntity),
                pos.x,
                pos.y,
                pos.z
            ).withStyle(ChatFormatting.GREEN)
        )
    }

    /**
     * Имя точки, если оно задано вручную: первая непустая строка таблички либо
     * кастомное имя флага. null — имени нет, и вызывающий подставляет перевод-заглушку.
     */
    private fun resolveName(blockEntity: BlockEntity): Component? {
        if (blockEntity is SignBlockEntity) {
            val lines = blockEntity.getText(SignTextSlot.FRONT).getMessages(false)
            for (line in lines) {
                val text = line.string.trim { it <= ' ' }
                if (text.isNotEmpty()) {
                    return line
                }
            }
        }
        if (blockEntity is BannerBlockEntity) {
            val custom = blockEntity.customName
            if (custom != null) {
                val text = custom.string.trim { it <= ' ' }
                if (text.isNotEmpty()) {
                    return custom
                }
            }
        }
        return null
    }

    private fun displayName(state: BlockWaypointState, blockEntity: BlockEntity): Component {
        val stored = state.name
        if (!stored.isNullOrEmpty()) {
            return Component.literal(stored)
        }
        resolveName(blockEntity)?.let { return it }

        val pos = blockEntity.blockPos
        return Component.translatable(KEY_DEFAULT_NAME, pos.x, pos.y, pos.z)
    }
}
