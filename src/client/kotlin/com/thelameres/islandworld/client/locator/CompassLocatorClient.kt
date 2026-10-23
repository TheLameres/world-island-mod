package com.thelameres.islandworld.client.locator

import com.thelameres.islandworld.locator.LocatorEntry
import com.thelameres.islandworld.locator.PlayerPositionsPayload
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking

/**
 * Клиентский приёмник [PlayerPositionsPayload].
 * Хранит последний полученный список игроков; отрисовка LocatorBar читает его через [currentEntries].
 * Сам CompassCoordinatesHud не затрагивается.
 */
object CompassLocatorClient {

    @Volatile
    private var entries: List<LocatorEntry> = emptyList()

    fun register() {
        ClientPlayNetworking.registerGlobalReceiver(PlayerPositionsPayload.TYPE) { payload, context ->
            // Сетевой поток → поток рендера/клиента, как того требует Minecraft
            context.client().execute {
                entries = payload.entries
            }
        }

        // При выходе с сервера список не должен пережить сессию
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ ->
            entries = emptyList()
        }
    }

    /** Актуальный список видимых игроков (пустой, если компаса нет или нет соединения). */
    fun currentEntries(): List<LocatorEntry> = entries
}
