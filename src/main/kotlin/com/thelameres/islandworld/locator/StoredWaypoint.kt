package com.thelameres.islandworld.locator

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.waypoints.Waypoint
import net.minecraft.world.waypoints.WaypointStyleAssets
import net.minecraft.world.waypoints.WaypointTransmitter
import java.util.*


/**
 * Waypoint-передатчик, живущий в [WaypointStore], а не в блок-энтitи.
 *
 * Зачем отдельный объект: при выгрузке чанка BlockEntity уничтожается
 * (ServerLevel.unload → LevelChunk.clearAllBlockEntities), поэтому табличка не может
 * оставаться передатчиком. Хранилище живёт в SavedData уровня, значит точка продолжает
 * висеть в WaypointManager и после выгрузки чанка — игрок видит метку на компасе,
 * даже если блок сейчас не загружен.
 *
 * Позиция неизменяема: она же служит ключом в хранилище.
 */
class StoredWaypoint internal constructor(
    private val store: WaypointStore,
    private val pointId: UUID,
    val position: BlockPos,
    @Volatile internal var name: String?,
) : WaypointTransmitter {

    private val icon: Waypoint.Icon = Waypoint.Icon()

    init {
        this.icon.style = WaypointStyleAssets.DEFAULT
    }

    fun id(): UUID {
        return pointId
    }

    fun name(): String? {
        return name
    }

    /** Обновление имени точки (игрок переименовал табличку и включил метку заново). */
    internal fun updateName(newName: String?) {
        this.name = newName
    }

    /** Метка «передаётся», пока она есть в хранилище: отключение снимает её у всех игроков. */
    override fun isTransmittingWaypoint(): Boolean {
        return store.contains(position)
    }

    override fun waypointIcon(): Waypoint.Icon {
        return icon
    }

    override fun makeWaypointConnectionWith(receiver: ServerPlayer): Optional<WaypointTransmitter.Connection> {
        if (!isTransmittingWaypoint()) {
            return Optional.empty()
        }
        // Далёкие чанки не грузим: для незагруженного чанка отправляем метку уровнем чанка,
        // для загруженного — точную позицию блока.
        val precise = WaypointTransmitter.isChunkVisible(ChunkPos.containing(position), receiver)
        return Optional.of(
            BlockWaypointConnection(
                pointId,
                icon,
                position,
                receiver,
                precise
            )
        )
    }
}
