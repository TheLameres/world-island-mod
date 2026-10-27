package com.thelameres.islandworld.locator

import net.minecraft.network.chat.Component
import net.minecraft.world.level.block.entity.BannerBlockEntity
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.entity.SignBlockEntity
import net.minecraft.world.level.block.entity.SignTextSlot


/**
 * Имя waypoint-метки, извлечённое из самого блока.
 *
 * Табличка: первая непустая строка лицевой стороны.
 * Флаг: кастомное имя (BannerBlockEntity implements Nameable).
 * null — имени нет, вызывающий подставляет перевод-заглушку.
 *
 * Возвращается Component, а не String: сохраняется форматирование строки таблички
 * и её транслируемость. Раньше значение прогонялось через String и обратно
 * в Component.literal, из-за чего оформление терялось.
 */

/** Имя метки из блока: строка таблички или кастомное имя флага. null — имени нет. */
fun BlockEntity.waypointName(): Component? {
    if (this is SignBlockEntity) {
        val lines = getText(SignTextSlot.FRONT).getMessages(false)
        for (line in lines) {
            if (line.string.trim { it <= ' ' }.isNotEmpty()) {
                return line
            }
        }
    }
    if (this is BannerBlockEntity) {
        val custom = customName
        if (custom != null && custom.string.trim { it <= ' ' }.isNotEmpty()) {
            return custom
        }
    }
    return null
}
