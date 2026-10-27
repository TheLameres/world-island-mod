package com.thelameres.islandworld.locator

import com.mojang.serialization.Codec
import com.thelameres.islandworld.IslandWorldMod
import net.minecraft.core.BlockPos
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.server.level.ServerLevel
import net.minecraft.util.datafix.DataFixTypes
import net.minecraft.world.level.saveddata.SavedData
import net.minecraft.world.level.saveddata.SavedDataType
import java.util.*
import java.util.function.Supplier


/**
 * Постоянное хранилище waypoint-меток уровня.
 *
 * Ключ — [BlockPos]. Именно позиция является идентичностью метки: присутствие
 * координаты в хранилище И ЕСТЬ признак того, что на этом месте стоит метка.
 * Поэтому NBT-теги на табличках/флагах не нужны вовсе.
 *
 * Почему не по UUID: UUID приходилось бы хранить в блок-энтitи, а копирование
 * блока (Ctrl+средняя кнопка в креативе, structure block, /setblock с NBT)
 * переносит block entity data вместе с блоком. Копия получила бы тот же UUID,
 * и `upsert` перенёс бы существующую метку на новое место — исходная исчезла бы.
 * Позиция уникальна по определению, дубликат получить нельзя.
 *
 * Хранится в SavedData (data/islandworld_waypoints.dat), поэтому метки переживают
 * выгрузку чанка, перезагрузку мира и рестарт сервера.
 *
 * Жизненный цикл:
 *  - блок уничтожен (LevelChunk.removeBlockEntity) → remove() + untrackWaypoint;
 *  - чанк выгружен (ServerLevel.unload)          → ничего не делаем, точка остаётся;
 *  - чанк загружен (LevelChunk.setBlockEntity)   → upsert() + trackWaypoint (идемпотентно);
 *  - сервер стартовал                            → attach() возвращает точки в менеджер.
 */
class WaypointStore : SavedData() {

    private val points: MutableMap<BlockPos, StoredWaypoint> = LinkedHashMap()

    fun contains(pos: BlockPos): Boolean {
        return points.containsKey(pos)
    }

    fun get(pos: BlockPos): StoredWaypoint? {
        return points[pos]
    }

    /**
     * Создаёт или обновляет точку на заданной позиции. Возвращает живой передатчик —
     * его и надо отдавать в WaypointManager.trackWaypoint.
     *
     * Идемпотентен: повторный вызов с той же позицией вернёт тот же объект и тот же UUID,
     * поэтому после перезагрузки чанка метка на клиенте не мигает.
     */
    fun upsert(pos: BlockPos, name: String?): StoredWaypoint {
        val existing = points[pos]
        if (existing != null) {
            if (existing.name() != name) {
                existing.updateName(name)
                setDirty()
            }
            return existing
        }

        val created = StoredWaypoint(this, UUID.randomUUID(), pos, name)
        points[pos] = created
        setDirty()
        return created
    }

    /** Удаляет точку; возвращает её передатчик, чтобы вызывающий снял его с менеджера. */
    fun remove(pos: BlockPos): StoredWaypoint? {
        val removed = points.remove(pos)
        if (removed != null) {
            setDirty()
        }
        return removed
    }

    /**
     * Возвращает все точки уровня в WaypointManager. Нужно после старта сервера:
     * менеджер создаётся заново и про точки не знает.
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
            // BlockPos упаковывается в long: компактнее трёх int и не зависит от версии схемы
            entry.putLong("pos", point.position.asLong())
            entry.putString("id", point.id().toString())
            point.name()?.let { entry.putString("name", it) }
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
            IslandWorldMod.id("waypoints"),
            Supplier { WaypointStore() },
            CODEC,
            // DFU-тип нужен только для миграции; своя схема у нас стабильная,
            // поэтому берём нейтральный SAVED_DATA_COMMAND_STORAGE.
            DataFixTypes.SAVED_DATA_COMMAND_STORAGE
        )

        private fun ofTag(tag: CompoundTag): WaypointStore {
            val store = WaypointStore()
            for (entry in tag.getListOrEmpty("points").compoundStream().toList()) {
                val packedPos = entry.getLong("pos").orElse(null) ?: continue
                val pos = BlockPos.of(packedPos)

                // Битые данные не должны ронять загрузку уровня — просто пропускаем точку
                val rawId = entry.getStringOr("id", "")
                val id = if (rawId.isEmpty()) {
                    UUID.randomUUID()
                } else {
                    try {
                        UUID.fromString(rawId)
                    } catch (e: IllegalArgumentException) {
                        UUID.randomUUID()
                    }
                }

                store.points[pos] = StoredWaypoint(
                    store,
                    id,
                    pos,
                    entry.getString("name").orElse(null)
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
