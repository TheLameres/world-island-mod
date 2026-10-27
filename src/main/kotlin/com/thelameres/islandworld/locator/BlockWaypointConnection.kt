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
                    ChunkPos(position.x, position.z)
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
                    ChunkPos(position.x, position.z)
                )
            )
        }
    }

    /** Используется дефолтным isBroken(): метка рвётся, когда блок слишком далеко по манхэттену.  */
    override fun distanceManhattan(): Int {
        val playerPos: BlockPos = receiver.blockPosition()
        return (Math.abs(position.getX() - playerPos.getX())
                + Math.abs(position.getY() - playerPos.getY())
                + Math.abs(position.getZ() - playerPos.getZ()))
    }

    fun id(): UUID {
        return id
    }
}