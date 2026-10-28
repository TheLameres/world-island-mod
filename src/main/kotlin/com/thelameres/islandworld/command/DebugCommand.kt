package com.thelameres.islandworld.command

import com.mojang.brigadier.CommandDispatcher
import com.thelameres.islandworld.core.Registrable
import com.thelameres.islandworld.locator.CompassLocator
import com.thelameres.islandworld.locator.WaypointStore
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.network.chat.Component
import net.minecraft.server.MinecraftServer

/**
 * Диагностические команды мода. Только чтение состояния, ничего не меняют.
 *
 *   /islandworld debug waypoints  — точки WaypointStore по измерениям
 *   /islandworld debug locator    — записи кэша CompassLocator.lastAttributeState
 *
 * Доступ: только администраторы (LEVEL_ADMINS).
 *
 * Все тексты вынесены в lang-файлы и выводятся через Component.translatable.
 * Исключение — пользовательские данные (имя метки, имя игрока): они не переводятся
 * и выводятся как есть через Component.literal.
 */
object DebugCommand : Registrable {

    private const val KEY_WAYPOINTS_EMPTY = "islandworld.debug.waypoints.empty"
    private const val KEY_WAYPOINTS_TOTAL = "islandworld.debug.waypoints.total"
    private const val KEY_WAYPOINTS_DIMENSION = "islandworld.debug.waypoints.dimension"
    private const val KEY_WAYPOINTS_ENTRY = "islandworld.debug.waypoints.entry"
    private const val KEY_WAYPOINTS_UNNAMED = "islandworld.debug.waypoints.unnamed"

    private const val KEY_LOCATOR_EMPTY = "islandworld.debug.locator.empty"
    private const val KEY_LOCATOR_TOTAL = "islandworld.debug.locator.total"
    private const val KEY_LOCATOR_ENTRY = "islandworld.debug.locator.entry"

    private const val KEY_PLAYER_OFFLINE = "islandworld.debug.player.offline"

    override fun register() {
        CommandRegistrationCallback.EVENT.register { dispatcher, _, _ ->
            register(dispatcher)
        }
    }

    private fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        dispatcher.register(
            Commands.literal("islandworld")
                .requires(Commands.hasPermission(Commands.LEVEL_ADMINS))
                .then(
                    Commands.literal("debug")
                        .then(
                            Commands.literal("waypoints")
                                .executes { ctx -> showWaypoints(ctx.source) }
                        )
                        .then(
                            Commands.literal("locator")
                                .executes { ctx -> showLocator(ctx.source) }
                        )
                )
        )
    }

    private fun showWaypoints(source: CommandSourceStack): Int {
        val server: MinecraftServer = source.server
        var total = 0
        val lines = mutableListOf<Component>()

        for (level in server.allLevels) {
            // find, а не of: не создаём SavedData в измерениях без меток
            val store = WaypointStore.find(level) ?: continue
            val points = store.snapshot()
            if (points.isEmpty()) continue

            lines += Component.translatable(
                KEY_WAYPOINTS_DIMENSION,
                level.dimension().identifier().toString(),
                points.size,
            )
            for (point in points) {
                val pos = point.position
                // Имя метки — пользовательские данные, не переводим
                val name: Component = point.name()?.let { Component.literal(it) }
                    ?: Component.translatable(KEY_WAYPOINTS_UNNAMED)
                lines += Component.translatable(
                    KEY_WAYPOINTS_ENTRY,
                    point.id().toString(),
                    pos.x,
                    pos.y,
                    pos.z,
                    name,
                )
            }
            total += points.size
        }

        if (total == 0) {
            source.sendSuccess({ Component.translatable(KEY_WAYPOINTS_EMPTY) }, false)
            return 0
        }

        source.sendSuccess({ Component.translatable(KEY_WAYPOINTS_TOTAL, total) }, false)
        lines.forEach { line -> source.sendSuccess({ line }, false) }
        return total
    }

    private fun showLocator(source: CommandSourceStack): Int {
        val snapshot = CompassLocator.attributeSnapshot()
        if (snapshot.isEmpty()) {
            source.sendSuccess({ Component.translatable(KEY_LOCATOR_EMPTY) }, false)
            return 0
        }

        val players = source.server.playerList.playersByUUID
        source.sendSuccess({ Component.translatable(KEY_LOCATOR_TOTAL, snapshot.size) }, false)

        for ((uuid, state) in snapshot) {
            val (receive, transmit) = state
            // Имя игрока — пользовательские данные, берём из текущего списка онлайн
            val who: Component = players[uuid]?.name ?: Component.translatable(KEY_PLAYER_OFFLINE)
            source.sendSuccess({
                Component.translatable(KEY_LOCATOR_ENTRY, who, uuid.toString(), receive, transmit)
            }, false)
        }
        return snapshot.size
    }
}
