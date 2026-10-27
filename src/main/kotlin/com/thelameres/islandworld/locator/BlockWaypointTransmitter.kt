package com.thelameres.islandworld.locator


/**
 * Контракт для блок-энтитей, которые умеют быть waypoint-метками.
 *
 * ВАЖНО: сам BlockEntity больше НЕ является WaypointTransmitter. При выгрузке чанка
 * BlockEntity уничтожается (ServerLevel.unload → LevelChunk.clearAllBlockEntities),
 * поэтому передатчиком служит [StoredWaypoint] из [WaypointStore], живущий вне чанка.
 * Блок-энтити лишь хранит своё состояние и сообщает о нём хранилищу.
 */
interface BlockWaypointTransmitter {
    fun `islandworld$waypointState`(): BlockWaypointState
}
