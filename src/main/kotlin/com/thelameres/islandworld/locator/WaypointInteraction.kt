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
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.phys.BlockHitResult

/**
 * Клик компасом по табличке или флагу — включение/выключение waypoint-метки.
 *
 * Логика:
 * - требуется компас в руке, которой кликнули;
 * - обычный ПКМ по табличке без Shift не перехватывается, чтобы не ломать ванильное редактирование;
 * - повторный Shift+ПКМ по той же точке снимает метку.
 *
 * Источник истины о том, включена ли метка, — [WaypointStore]: наличие позиции блока
 * в хранилище. В блок-энтitи ничего не пишется.
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

        toggle(level, blockEntity, player)
        return InteractionResult.SUCCESS_SERVER
    }

    private fun toggle(level: ServerLevel, blockEntity: BlockEntity, player: ServerPlayer) {
        val store = WaypointStore.of(level)
        val pos = blockEntity.blockPos

        if (store.contains(pos)) {
            // Снимаем с хранилища (чтобы точка не воскресла после выгрузки/перезагрузки)
            // и с менеджера, чтобы у игроков сразу пропала метка
            store.remove(pos)?.let { removed -> level.waypointManager.untrackWaypoint(removed) }
            player.sendSystemMessage(
                Component.translatable(KEY_REMOVED, displayName(blockEntity))
                    .withStyle(ChatFormatting.GRAY)
            )
            return
        }

        val name = WaypointNames.resolve(blockEntity)?.string
        val waypoint = store.upsert(pos, name)
        level.waypointManager.trackWaypoint(waypoint)

        player.sendSystemMessage(
            Component.translatable(
                KEY_ADDED,
                displayName(blockEntity),
                pos.x,
                pos.y,
                pos.z
            ).withStyle(ChatFormatting.GREEN)
        )
    }

    /**
     * Имя точки для чата: строка таблички либо кастомное имя флага,
     * иначе переводимая заглушка с координатами.
     */
    private fun displayName(blockEntity: BlockEntity): Component {
        WaypointNames.resolve(blockEntity)?.let { return it }

        val pos = blockEntity.blockPos
        return Component.translatable(KEY_DEFAULT_NAME, pos.x, pos.y, pos.z)
    }
}
