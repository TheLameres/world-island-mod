package com.thelameres.islandworld.core

import com.thelameres.islandworld.border.BoundaryManager
import com.thelameres.islandworld.border.EdgeRespawn
import com.thelameres.islandworld.locator.CompassLocator
import com.thelameres.islandworld.locator.WaypointStore
import com.thelameres.islandworld.util.SpawnUtil
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents

/**
 * Общие подписки на события жизненного цикла.
 *
 * Раньше каждая подсистема подписывалась сама, и одна и та же очистка дублировалась
 * в трёх местах под тремя именами: `BoundaryManager.onDisconnect`, `EdgeRespawn.clear`
 * и `lastAttributeState.remove(...)` прямо в лямбде. Здесь это один список и одна подписка.
 *
 * Списки ниже — единственное место, где перечислено, кто держит состояние по игроку
 * и кто живёт одну сессию. Новая подсистема добавляется одной строкой, и её нельзя
 * забыть очистить: интерфейс не даст скомпилироваться без реализации метода.
 *
 * Регистрация обработчиков AFTER_RESPAWN и событий тика остаётся в самих подсистемах:
 * там важен порядок относительно соседей, и он должен читаться на месте.
 *
 * SERVER_STARTED тоже здесь: восстановление сохранённых меток — такая же общая операция
 * над жизненным циклом, как сброс на остановке.
 */
object Lifecycle : Registrable {

    /** Всё, что помнит состояние по UUID игрока и обязано его забыть на выходе. */
    private val playerScoped: List<PlayerScoped> = listOf(
        BoundaryManager,
        CompassLocator,
        EdgeRespawn,
    )

    /** Всё, что копит состояние на сессию сервера и обязано его сбросить на остановке. */
    private val sessionScoped: List<SessionScoped> = listOf(
        SpawnUtil,
        EdgeRespawn,
    )

    private val restoreScoped: List<RestoreScoped> = listOf(
        WaypointStore,
    )

    override fun register() {
        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ ->
            val uuid = handler.player.uuid
            playerScoped.forEach { it.forget(uuid) }
        }

        ServerLifecycleEvents.SERVER_STARTED.register { server ->
            restoreScoped.forEach { it.restore(server) }
        }

        ServerLifecycleEvents.SERVER_STOPPED.register { server ->
            sessionScoped.forEach { it.reset(server) }
        }
    }
}
