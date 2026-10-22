package com.thelameres.islandworld.portal

import net.minecraft.core.BlockPos
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.storage.LevelResource
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

/**
 * Запоминает, через какой портал Нижнего мира игрок вошёл в Чёрную комнату.
 * При выходе из комнаты он возвращается к этому же порталу.
 *
 * Файл islandworld_return_portals.txt в папке мира, строка: UUID x y z.
 * Хранится на диске, чтобы пережить перезапуск сервера.
 */
object ReturnPortalStore {

	private const val FILE_NAME = "islandworld_return_portals.txt"

	private fun file(server: MinecraftServer): Path =
		server.getWorldPath(LevelResource.ROOT).resolve(FILE_NAME)

	private fun readAll(server: MinecraftServer): MutableMap<UUID, BlockPos> {
		val map = mutableMapOf<UUID, BlockPos>()
		val path = file(server)
		if (!Files.exists(path)) return map
		for (line in Files.readAllLines(path)) {
			val parts = line.trim().split(' ')
			if (parts.size != 4) continue
			runCatching {
				map[UUID.fromString(parts[0])] =
					BlockPos(parts[1].toInt(), parts[2].toInt(), parts[3].toInt())
			}
		}
		return map
	}

	private fun writeAll(server: MinecraftServer, map: Map<UUID, BlockPos>) {
		val lines = map.map { (uuid, p) -> "$uuid ${p.x} ${p.y} ${p.z}" }
		Files.write(file(server), lines)
	}

	fun save(server: MinecraftServer, uuid: UUID, portal: BlockPos) {
		val map = readAll(server)
		map[uuid] = portal
		writeAll(server, map)
	}

	fun get(server: MinecraftServer, uuid: UUID): BlockPos? = readAll(server)[uuid]
}
