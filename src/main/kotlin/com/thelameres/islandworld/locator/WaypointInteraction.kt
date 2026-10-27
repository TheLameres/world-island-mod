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
 */
object WaypointInteraction {
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
                Component.literal("Метка снята: " + displayName(state, blockEntity))
                    .withStyle(ChatFormatting.GRAY)
            )
            return
        }

        state.name = resolveName(blockEntity)
        state.setEnabled(true)
        level.waypointManager.trackWaypoint(transmitter)
        blockEntity.setChanged()

        val pos = blockEntity.blockPos
        player.sendSystemMessage(
            Component.literal(
                "Метка добавлена: \"${displayName(state, blockEntity)} (${pos.x}, ${pos.y}, ${pos.z})"
            )
                .withStyle(ChatFormatting.GREEN)
        )
    }

    /** Имя точки: первая непустая строка таблички, иначе кастомное имя флага, иначе координаты.  */
    private fun resolveName(blockEntity: BlockEntity): String {
        if (blockEntity is SignBlockEntity) {
            val lines: MutableList<Component> = blockEntity.getText(SignTextSlot.FRONT).getMessages(false)
            for (line in lines) {
                val text = line.string.trim { it <= ' ' }
                if (text.isNotEmpty()) {
                    return text
                }
            }
        }
        if (blockEntity is BannerBlockEntity) {
            val custom = blockEntity.customName
            if (custom != null) {
                val text = custom.string.trim { it <= ' ' }
                if (text.isNotEmpty()) {
                    return text
                }
            }
        }
        val pos = blockEntity.blockPos
        return "Точка " + pos.x + " " + pos.y + " " + pos.z
    }

    private fun displayName(state: BlockWaypointState, blockEntity: BlockEntity): String? {
        return if (state.name.isNullOrEmpty()) resolveName(blockEntity) else state.name
    }
}