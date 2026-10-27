package com.thelameres.islandworld.portal

import com.thelameres.islandworld.worldgen.blackroom.BlackRoom
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.Level
import net.minecraft.world.level.portal.TeleportTransition
import net.minecraft.world.phys.Vec3
import kotlin.math.sqrt

/**
 * Маршрутизация порталов Нижнего мира (план, раздел 5.4).
 *
 * Вся логика вынесена из MixinNetherPortal: миксин только спрашивает «куда телепортировать»
 * и, если ответ не null, подставляет его вместо ванильного поведения.
 *
 * Правила:
 *  - выход из Чёрной комнаты  → возврат в Нижний мир к сохранённому порталу;
 *  - вход из Нижнего мира     → точка назначения дальше 2000 блоков от центра Overworld
 *                               уводит игрока в Чёрную комнату;
 *  - всё остальное            → null, работает ваниль.
 */
object PortalRouting {

    /** Радиус острова: дальше этого от центра Overworld игрок уходит в Чёрную комнату. */
    private const val ISLAND_RADIUS = 2000.0

    /** Блоков Overworld на блок Нижнего мира. */
    private const val NETHER_SCALE = 8.0

    /** Точка входа в Чёрную комнату — центр (8, 64, 8) по плану. */
    private const val BLACK_ROOM_ENTRY_X = 8.5
    private const val BLACK_ROOM_ENTRY_Z = 8.5

    /**
     * Возвращает нестандартный переход либо null, если должна отработать ваниль.
     */
    @JvmStatic
    fun resolveDestination(level: ServerLevel, entity: Entity, pos: BlockPos): TeleportTransition? {
        val player = entity as? ServerPlayer ?: return null
        val server = level.server

        // Выход из Чёрной комнаты — возвращаем к порталу, через который вошли
        if (level.dimension() == BlackRoom.DIMENSION) {
            return exitToNether(level, player)
        }

        // Вход в Чёрную комнату — только из Нижнего мира и только в дальней точке
        if (level.dimension() != Level.NETHER) return null
        if (!isBeyondIsland(pos)) return null

        val blackRoom = server.getLevel(BlackRoom.DIMENSION) ?: return null

        // Запоминаем портал, через который игрок вошёл, чтобы вернуть его сюда же
        ReturnPortalStore.save(server, player.uuid, pos)

        val spawn = Vec3(BLACK_ROOM_ENTRY_X, BlackRoom.SPAWN_Y, BLACK_ROOM_ENTRY_Z)
        return TeleportTransition(blackRoom, spawn, Vec3.ZERO, 0f, 0f, TeleportTransition.DO_NOTHING)
    }

    /**
     * Точка в Нижнем мире «далеко», если соответствующие ей координаты Overworld
     * выходят за радиус острова. Координата Overworld = pos * 8.
     */
    private fun isBeyondIsland(pos: BlockPos): Boolean {
        val targetX = pos.x * NETHER_SCALE
        val targetZ = pos.z * NETHER_SCALE
        return sqrt(targetX * targetX + targetZ * targetZ) > ISLAND_RADIUS
    }

    /**
     * Возврат из Чёрной комнаты в Нижний мир к сохранённому порталу.
     * null — записи нет или мира Нижнего мира не существует, тогда пусть решает ваниль.
     */
    private fun exitToNether(level: ServerLevel, player: ServerPlayer): TeleportTransition? {
        val saved = ReturnPortalStore.get(level.server, player.uuid) ?: return null
        val nether = level.server.getLevel(Level.NETHER) ?: return null

        // Ставим игрока по центру портала, на уровне входа
        val target = Vec3(saved.x + 0.5, saved.y.toDouble(), saved.z + 0.5)
        return TeleportTransition(nether, target, Vec3.ZERO, 0f, 0f, TeleportTransition.DO_NOTHING)
    }
}
