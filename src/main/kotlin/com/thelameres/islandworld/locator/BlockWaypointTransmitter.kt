package com.thelameres.islandworld.locator

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.waypoints.Waypoint
import net.minecraft.world.waypoints.WaypointTransmitter
import java.util.*


/**
 * Общий контракт для блок-энтити, которые умеют быть waypoint-передатчиками.
 * Миксины на SignBlockEntity и BannerBlockEntity реализуют этот интерфейс
 * и добавляют @Unique-поле BlockWaypointState.
 */
interface BlockWaypointTransmitter : WaypointTransmitter {
    fun `islandworld$waypointState`(): BlockWaypointState

    override fun isTransmittingWaypoint(): Boolean {
        return `islandworld$waypointState`().isEnabled
    }

    override fun makeWaypointConnectionWith(receiver: ServerPlayer): Optional<WaypointTransmitter.Connection> {
        return `islandworld$waypointState`().connectionFor(receiver)
    }

    override fun waypointIcon(): Waypoint.Icon {
        return `islandworld$waypointState`().icon()
    }
}
