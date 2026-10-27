package com.thelameres.islandworld.locator

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.waypoints.Waypoint
import net.minecraft.world.waypoints.WaypointStyleAssets
import net.minecraft.world.waypoints.WaypointTransmitter
import java.util.*


/**
 * Waypoint-передатчик, живущий в [WaypointRegistry], а не в блок-энтити.
 *
 * Зачем отдельный объект: при выгрузке чанка BlockEntity уничтожается
 * (ServerLevel.unload → LevelChunk.clearAllBlockEntities), поэтому табличка не может
 * оставаться передатчиком. Реестр живёт в SavedData уровня, значит точка продолжает
 * висеть в WaypointManager и после выгрузки чанка — игрок видит метку на компасе,
 * даже если блок сейчас не загружен.
 */
class RegistryWaypoint internal constructor(
    private val registry: WaypointRegistry,
    private val pointId: UUID,
    @Volatile internal var position: BlockPos,
    @Volatile internal var name: String?,
    @Volatile internal var colorRgb: Int?,
) : WaypointTransmitter {

    private val icon: Waypoint.Icon = Waypoint.Icon()

    init {
        this.icon.style = WaypointStyleAssets.DEFAULT
        this.colorRgb?.let { rgb -> this.icon.color = Optional.of(rgb) }
    }

    fun id(): UUID {
        return pointId
    }

    fun name(): String? {
        return name
    }

    fun color(): Int? {
        return colorRgb
    }

    /**
     * Обновление данных точки (например, игрок переименовал табличку и включил метку заново).
     */
    internal fun update(newName: String?, newColor: Int?) {
        this.name = newName
        this.colorRgb = newColor
        this.icon.color = Optional.ofNullable(newColor)
    }

    internal fun moveTo(newPosition: BlockPos) {
        this.position = newPosition
    }

    /** Метка «передаётся», пока она есть в реестре: так отключение снимает её у всех игроков. */
    override fun isTransmittingWaypoint(): Boolean {
        return registry.contains(pointId)
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
