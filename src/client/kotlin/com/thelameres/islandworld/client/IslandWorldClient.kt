package com.thelameres.islandworld.client

import com.thelameres.islandworld.client.hallucination.HallucinationClient
import com.thelameres.islandworld.client.hud.CompassCoordinatesHud
import net.fabricmc.api.ClientModInitializer

object IslandWorldClient : ClientModInitializer {
    override fun onInitializeClient() {
        // Координаты на компасе (HUD)
        CompassCoordinatesHud.register()

        // Галлюцинации (план 4.4): приёмник дескриптора и пост-эффекты
        HallucinationClient.register()

        // Сюда позже: рендерер UnknownEntity, пост-эффекты галлюцинаций, звуки.


    }
}
