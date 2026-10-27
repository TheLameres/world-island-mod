package com.thelameres.islandworld.locator

import net.minecraft.core.BlockPos
import net.minecraft.core.Vec3i
import net.minecraft.network.protocol.game.ClientboundTrackedWaypointPacket
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.waypoints.Waypoint
import net.minecraft.world.waypoints.WaypointTransmitter
import java.util.*


class BlockWaypointConnection(
    private val id: UUID,
    private val icon: Waypoint.Icon,
    private val position: Vec3i,
    private val receiver: ServerPlayer,
    private val precise: Boolean,
) : WaypointTransmitter.BlockConnection {
    override fun connect() {
        if (precise) {
            receiver.connection.send(ClientboundTrackedWaypointPacket.addWaypointPosition(id, icon, position))
        } else {
            // Чанк не загружен — показываем точку на уровне чанка, как это делает ванила
            receiver.connection.send(
                ClientboundTrackedWaypointPacket.addWaypointChunk(
                    id, icon,
                    ChunkPos(position.x shr 4, position.z shr 4)
                )
            )
        }
    }

    override fun disconnect() {
        receiver.connection.send(ClientboundTrackedWaypointPacket.removeWaypoint(id))
    }

    override fun update() {
        if (precise) {
            receiver.connection.send(ClientboundTrackedWaypointPacket.updateWaypointPosition(id, icon, position))
        } else {
            receiver.connection.send(
                ClientboundTrackedWaypointPacket.updateWaypointChunk(
                    id, icon,
                    ChunkPos(position.x shr 4, position.z shr 4)
                )
            )
        }
    }

    /**
     * Соединение считается «порванным», когда сменился режим видимости чанка:
     * игрок подошёл (нужна точная позиция) или отошёл (нужен уровень чанка).
     * Ванильный default рвёт связь при манхэттенском расстоянии > 1, что для
     * статичной точки означало бы пересоздание соединения каждый тик.
     */
    override fun isBroken(): Boolean {
        val chunkVisible = WaypointTransmitter.isChunkVisible(
            ChunkPos(position.x shr 4, position.z shr 4), receiver
        )
        return chunkVisible != precise
    }

    /** Расстояние до игрока по манхэттену.  */
    override fun distanceManhattan(): Int {
        val playerPos: BlockPos = receiver.blockPosition()
        return (Math.abs(position.getX() - playerPos.getX())
                + Math.abs(position.getY() - playerPos.getY())
                + Math.abs(position.getZ() - playerPos.getZ()))
    }
}