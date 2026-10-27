package com.thelameres.islandworld.locator

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.chunk.LevelChunk

/**
 * Вся логика синхронизации блок-энтitей с [WaypointStore].
 *
 * Вызывается из MixinLevelChunkWaypoint, который сам ничего не решает — только
 * передаёт управление сюда. Так вся предметная логика живёт в Kotlin, где её
 * видно, можно прочитать и покрыть тестами, а Java-миксин остаётся клеем.
 *
 * Ключевое правило: точка живёт в [WaypointStore] (SavedData уровня), а не в
 * BlockEntity. Признак метки — наличие позиции блока в хранилище, NBT не используется.
 */
object WaypointChunkHooks {

    /**
     * Блок-энтitи попал в чанк: при первичной установке, при загрузке из NBT
     * или при setBlock. Если позиция уже известна хранилищу — восстанавливаем метку.
     *
     * Идемпотентно: повторный вызов для той же позиции возвращает тот же передатчик
     * с тем же UUID, поэтому метка на клиенте не мигает при перезагрузке чанка.
     */
    @JvmStatic
    fun onBlockEntityAdded(chunk: LevelChunk, blockEntity: BlockEntity) {
        if (blockEntity !is BlockWaypointTransmitter) return

        val level = chunk.level as? ServerLevel ?: return
        val pos = blockEntity.blockPos

        val store = WaypointStore.of(level)
        // Не метка — восстанавливать нечего
        if (!store.contains(pos)) return

        // Имя перечитываем из блока: табличку могли переименовать, пока чанк был выгружен
        val waypoint = store.upsert(pos, blockEntity.waypointName()?.string)
        level.waypointManager.trackWaypoint(waypoint)
    }

    /**
     * Блок-энтitи вынимается из чанка: игрок сломал блок, взрыв, замена блока.
     * Метку удаляем из хранилища и снимаем с менеджера, чтобы она не воскресла.
     */
    @JvmStatic
    fun onBlockEntityRemoved(chunk: LevelChunk, pos: BlockPos) {
        val level = chunk.level as? ServerLevel ?: return

        // На момент вызова BE ещё лежит в map чанка, поэтому getBlockEntity его находит.
        // Через него проверяем, что удаляют именно носитель метки, а не любой блок.
        if (chunk.getBlockEntity(pos) !is BlockWaypointTransmitter) return

        val removed = WaypointStore.of(level).remove(pos) ?: return
        level.waypointManager.untrackWaypoint(removed)
    }
}
