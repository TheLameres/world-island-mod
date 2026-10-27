package com.thelameres.islandworld.portal

import com.mojang.serialization.Codec
import com.thelameres.islandworld.IslandWorldMod
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerLevel
import net.minecraft.util.datafix.DataFixTypes
import net.minecraft.world.level.saveddata.SavedData
import net.minecraft.world.level.saveddata.SavedDataType
import java.util.*
import java.util.function.Supplier


/**
 * Запоминает, через какой портал Нижнего мира игрок вошёл в Чёрную комнату.
 * При выходе из комнаты он возвращается к этому же порталу.
 *
 * Хранится в SavedData (`data/islandworld_return_portals.dat`), а не в текстовом файле:
 * сохранение идёт штатным механизмом мира, запись попадает в общий цикл автосейва,
 * а формат описан кодеком, а не ручным парсингом строк.
 *
 * Привязка к Overworld, а не к Нижнему миру: данные читаются из двух измерений
 * (запись — при входе из Незера, чтение — при выходе из Чёрной комнаты), поэтому нужен
 * уровень, который существует гарантированно. `server.overworld()` не бывает null,
 * а `server.getLevel(Level.NETHER)` теоретически может.
 *
 * Ключ — UUID игрока. Значение перезаписывается при каждом новом входе, поэтому
 * набор записей ограничен числом игроков, когда-либо заходивших в комнату.
 */
class ReturnPortalStore : SavedData() {

    private val portals: MutableMap<UUID, BlockPos> = LinkedHashMap()

    /** Сохраняет портал входа. Повторный вызов с той же позицией не помечает данные грязными. */
    fun save(uuid: UUID, portal: BlockPos) {
        if (portals.put(uuid, portal) != portal) {
            setDirty()
        }
    }

    /** Портал, через который игрок вошёл; null — записи нет, пусть решает ваниль. */
    fun get(uuid: UUID): BlockPos? {
        return portals[uuid]
    }

    /** Забывает запись об игроке. Сейчас не вызывается: запись перезаписывается при следующем входе. */
    fun remove(uuid: UUID) {
        if (portals.remove(uuid) != null) {
            setDirty()
        }
    }

    // --- Сериализация -----------------------------------------------------

    private fun toTag(): CompoundTag {
        val list = ListTag()
        for ((uuid, pos) in portals) {
            val entry = CompoundTag()
            entry.putString("uuid", uuid.toString())
            // BlockPos упаковывается в long: компактнее трёх int и не зависит от версии схемы
            entry.putLong("pos", pos.asLong())
            list.addAndUnwrap(entry)
        }
        val root = CompoundTag()
        root.put("portals", list)
        return root
    }

    companion object {
        private val CODEC: Codec<ReturnPortalStore> =
            CompoundTag.CODEC.xmap({ tag -> ofTag(tag) }, { store -> store.toTag() })

        private val TYPE: SavedDataType<ReturnPortalStore> = SavedDataType(
            IslandWorldMod.id("return_portals"),
            ::ReturnPortalStore,
            CODEC,
            // DFU-тип нужен только для миграции; своя схема у нас стабильная,
            // поэтому берём нейтральный SAVED_DATA_COMMAND_STORAGE.
            DataFixTypes.SAVED_DATA_COMMAND_STORAGE
        )

        private fun ofTag(tag: CompoundTag): ReturnPortalStore {
            val store = ReturnPortalStore()
            for (entry in tag.getListOrEmpty("portals").compoundStream().toList()) {
                val rawUuid = entry.getStringOr("uuid", "")
                if (rawUuid.isEmpty()) continue

                // Битые данные не должны ронять загрузку уровня — просто пропускаем запись
                val uuid = try {
                    UUID.fromString(rawUuid)
                } catch (e: IllegalArgumentException) {
                    continue
                }

                val packedPos = entry.getLong("pos").orElse(null) ?: continue
                store.portals[uuid] = BlockPos.of(packedPos)
            }
            return store
        }

        @JvmStatic
        fun of(level: ServerLevel): ReturnPortalStore {
            return level.dataStorage.computeIfAbsent(TYPE)
        }

        /** Хранилище из любого места, где есть только сервер. Overworld существует всегда. */
        @JvmStatic
        fun of(server: MinecraftServer): ReturnPortalStore {
            return of(server.overworld())
        }
    }
}
