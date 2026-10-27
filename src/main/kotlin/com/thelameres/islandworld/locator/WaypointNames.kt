package com.thelameres.islandworld.locator

import net.minecraft.network.chat.Component
import net.minecraft.world.level.block.entity.BannerBlockEntity
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.SignBlockEntity
import net.minecraft.world.level.block.entity.SignTextSlot


/**
 * Достаёт «человеческое» имя метки из самого блока.
 *
 * Табличка: первая непустая строка лицевой стороны.
 * Флаг: кастомное имя (BannerBlockEntity implements Nameable).
 * null — имени нет, вызывающий подставляет перевод-заглушку.
 *
 * Возвращается Component, а не String: сохраняется форматирование строки таблички
 * и её транслируемость. Раньше значение прогонялось через String и обратно
 * в Component.literal, из-за чего оформление терялось.
 */
object WaypointNames {

    /** @JvmStatic — чтобы миксины на Java могли звать WaypointNames.resolve(...) напрямую. */
    @JvmStatic
    fun resolve(blockEntity: BlockEntity): Component? {
        if (blockEntity is SignBlockEntity) {
            val lines = blockEntity.getText(SignTextSlot.FRONT).getMessages(false)
            for (line in lines) {
                if (line.string.trim { it <= ' ' }.isNotEmpty()) {
                    return line
                }
            }
        }
        if (blockEntity is BannerBlockEntity) {
            val custom = blockEntity.customName
            if (custom != null && custom.string.trim { it <= ' ' }.isNotEmpty()) {
                return custom
            }
        }
        return null
    }
}
