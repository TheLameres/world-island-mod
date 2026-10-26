package com.thelameres.islandworld.client

import com.thelameres.islandworld.client.hud.CompassCoordinatesHud
import com.thelameres.islandworld.client.locator.CompassLocatorClient
import com.thelameres.islandworld.client.locator.LocatorBarHud
import net.fabricmc.api.ClientModInitializer

object IslandWorldClient : ClientModInitializer {
    override fun onInitializeClient() {
        // Координаты на компасе (HUD)
        CompassCoordinatesHud.register()

        // Приём списка игроков для LocatorBar (payload от сервера)
        CompassLocatorClient.register()

        // Отрисовка LocatorBar с игроками (HUD-элемент перед чатом)
        LocatorBarHud.register()

        // Сюда позже: рендерер UnknownEntity, пост-эффекты галлюцинаций, звуки.


    }
}
