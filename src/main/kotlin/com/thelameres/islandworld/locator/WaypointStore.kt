package com.thelameres.islandworld.locator

import com.mojang.serialization.Codec
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerLevel
import net.minecraft.util.datafix.DataFixTypes
import net.minecraft.world.level.saveddata.SavedData
import net.minecraft.world.level.saveddata.SavedDataType
import java.util.*
import java.util.function.Supplier


/**
 * Постоянное хранилище waypoint-меток уровня.
 *
 * Хранится в SavedData (data/islandworld_waypoints.dat), поэтому метки переживают
 * выгрузку чанка, перезагрузку мира и рестарт сервера. Именно хранилище держит
 * [StoredWaypoint] в WaypointManager — блок-энтити при выгрузке чанка уничтожается
 * и передатчиком быть не может.
 *
 * Жизненный цикл:
 *  - блок уничтожен (LevelChunk.removeBlockEntity) → remove() + untrackWaypoint;
 *  - чанк выгружен (ServerLevel.unload)          → ничего не делаем, точка остаётся;
 *  - чанк загружен (LevelChunk.setBlockEntity)   → upsert() + trackWaypoint (идемпотентно);
 *  - сервер стартовал                            → attach() возвращает точки в менеджер.
 */
class WaypointStore : SavedData() {

    private val points: MutableMap<UUID, StoredWaypoint> = LinkedHashMap()

    fun contains(id: UUID): Boolean {
        return points.containsKey(id)
    }

    /**
     * Создаёт или обновляет точку. Возвращает живой передатчик — его и надо отдавать
     * в WaypointManager.trackWaypoint.
     */
    fun upsert(id: UUID, pos: BlockPos, name: String?, colorRgb: Int?): StoredWaypoint {
        val existing = points[id]
        if (existing != null) {
            val moved = existing.position != pos
            existing.moveTo(pos)
            existing.update(name, colorRgb)
            if (moved || existing.name() != name || existing.color() != colorRgb) {
                setDirty()
            }
            return existing
        }

        val created = StoredWaypoint(this, id, pos, name, colorRgb)
        points[id] = created
        setDirty()
        return created
    }

    /** Удаляет точку; возвращает её передатчик, чтобы вызывающий снял его с менеджера. */
    fun remove(id: UUID): StoredWaypoint? {
        val removed = points.remove(id)
        if (removed != null) {
            setDirty()
        }
        return removed
    }

    /**
     * Возвращает все точки уровня в WaypointManager. Нужно после старта сервера и
     * после загрузки хранилища с диска: менеджер создаётся заново и про точки не знает.
     */
    fun attach(level: ServerLevel) {
        for (point in points.values) {
            level.waypointManager.trackWaypoint(point)
        }
    }

    // --- Сериализация -----------------------------------------------------

    private fun toTag(): CompoundTag {
        val list = ListTag()
        for (point in points.values) {
            val entry = CompoundTag()
            entry.putString("id", point.id().toString())
            // BlockPos упаковывается в long: компактнее трёх int и не зависит от версии схемы
            entry.putLong("pos", point.position.asLong())
            point.name()?.let { entry.putString("name", it) }
            point.color()?.let { entry.putInt("color", it) }
            list.addAndUnwrap(entry)
        }
        val root = CompoundTag()
        root.put("points", list)
        return root
    }

    companion object {
        private val CODEC: Codec<WaypointStore> =
            CompoundTag.CODEC.xmap({ tag -> ofTag(tag) }, { store -> store.toTag() })

        private val TYPE: SavedDataType<WaypointStore> = SavedDataType(
            Identifier.fromNamespaceAndPath("islandworld", "waypoints"),
            Supplier { WaypointStore() },
            CODEC,
            // DFU-тип нужен только для миграции; своя схема у нас стабильная,
            // поэтому берём нейтральный SAVED_DATA_COMMAND_STORAGE.
            DataFixTypes.SAVED_DATA_COMMAND_STORAGE
        )

        private fun ofTag(tag: CompoundTag): WaypointStore {
            val store = WaypointStore()
            for (entry in tag.getListOrEmpty("points").compoundStream().toList()) {
                val rawId = entry.getStringOr("id", "")
                if (rawId.isEmpty()) continue

                val id = try {
                    UUID.fromString(rawId)
                } catch (e: IllegalArgumentException) {
                    // Битые данные не должны ронять загрузку уровня — просто пропускаем точку
                    continue
                }

                val packedPos = entry.getLong("pos").orElse(null) ?: continue

                store.points[id] = StoredWaypoint(
                    store,
                    id,
                    BlockPos.of(packedPos),
                    entry.getString("name").orElse(null),
                    entry.getInt("color").orElse(null)
                )
            }
            return store
        }

        @JvmStatic
        fun of(level: ServerLevel): WaypointStore {
            return level.dataStorage.computeIfAbsent(TYPE)
        }
    }
}
