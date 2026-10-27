package com.thelameres.islandworld.locator

import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.waypoints.Waypoint
import net.minecraft.world.waypoints.WaypointStyleAssets
import net.minecraft.world.waypoints.WaypointTransmitter
import java.util.*


/**
 * Состояние waypoint-метки для блок-энтити (табличка, флаг).
 * Живёт в @Unique-поле миксина; здесь только данные и логика.
 */
class BlockWaypointState(
    var isEnabled: Boolean = false,
    private var id: UUID = UUID.randomUUID(),
    private var position: BlockPos? = null,
    private val icon: Waypoint.Icon = Waypoint.Icon(),
    var name: String? = null,
) {
    /** Метка включается вручную (командой/взаимодействием), чтобы не каждая табличка была точкой.  */

    init {
        this.icon.style = WaypointStyleAssets.DEFAULT
    }

    fun id(): UUID {
        return id
    }

    fun position(): BlockPos? {
        return position
    }

    fun icon(): Waypoint.Icon {
        return icon
    }

    fun bind(pos: BlockPos?) {
        this.position = pos
    }

    /**
     * Включает/выключает метку. Вызывающий сам делает
     * trackWaypoint/untrackWaypoint на ServerWaypointManager.
     */
    fun setEnabled(value: Boolean): Boolean {
        if (this.isEnabled == value) {
            return false
        }
        this.isEnabled = value
        if (value) {
            this.id = UUID.randomUUID()
        }
        return true
    }

    fun setColor(rgb: Int?) {
        this.icon.color = Optional.ofNullable<Int?>(rgb)
    }

    /** Соединение с конкретным игроком; null, если метка выключена.  */
    fun connectionFor(receiver: ServerPlayer): Optional<WaypointTransmitter.Connection> {
        if (!this.isEnabled || position == null) {
            return Optional.empty<WaypointTransmitter.Connection?>()
        }
        // Далёкие чанки не грузим: ванильный LocatorBar сам шлёт «далеко» вариант для LivingEntity,
        // для блока достаточно не показывать метку, пока чанк не загружен у игрока.
        if (!WaypointTransmitter.isChunkVisible(ChunkPos(position!!.x, position!!.z), receiver)) {
            return Optional.of<WaypointTransmitter.Connection>(
                BlockWaypointConnection(
                    id,
                    icon,
                    position!!,
                    receiver,
                    false
                )
            )
        }
        return Optional.of<WaypointTransmitter.Connection?>(
            BlockWaypointConnection(
                id,
                icon,
                position!!,
                receiver,
                true
            )
        )
    }
}
