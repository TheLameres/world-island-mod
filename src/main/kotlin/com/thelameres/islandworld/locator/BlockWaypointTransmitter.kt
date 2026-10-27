package com.thelameres.islandworld.locator


/**
 * Маркерный контракт: «этот блок-энтitи может нести waypoint-метку».
 *
 * Сам BlockEntity не является WaypointTransmitter: при выгрузке чанка он уничтожается
 * (ServerLevel.unload → LevelChunk.clearAllBlockEntities), поэтому передатчиком служит
 * [StoredWaypoint] из [WaypointStore], живущий вне чанка.
 *
 * Состояние метки в блок-энтitи не хранится вообще — ни полем, ни в NBT. Источник
 * истины один: наличие координаты блока в [WaypointStore]. Интерфейс нужен только
 * чтобы отличить подходящий блок от прочих: у SignBlockEntity и BannerBlockEntity
 * нет общего предка кроме BlockEntity, и HangingSignBlockEntity наследует
 * SignBlockEntity, так что проверка покрывает и висячие таблички.
 */
interface BlockWaypointTransmitter
