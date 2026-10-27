package com.thelameres.islandworld.util

import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.storage.LevelResource
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.*

/**
 * Список игроков, которые уже получили стартовую точку.
 * Файл islandworld_joined.txt в папке мира, по одному UUID на строку.
 */
object FirstJoinStore {

    private const val FILE_NAME = "islandworld_joined.txt"

    private fun file(server: MinecraftServer): Path =
        server.getWorldPath(LevelResource.ROOT).resolve(FILE_NAME)

    fun hasJoined(server: MinecraftServer, uuid: UUID): Boolean {
        val path = file(server)
        return Files.exists(path) && Files.readAllLines(path).any { it.trim() == uuid.toString() }
    }

    fun markJoined(server: MinecraftServer, uuid: UUID) {
        Files.write(
            file(server),
            listOf(uuid.toString()),
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND
        )
    }
}
