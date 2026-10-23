package com.thelameres.islandworld.locator

import com.thelameres.islandworld.IslandWorldMod
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.minecraft.core.UUIDUtil
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.minecraft.resources.Identifier
import java.util.UUID

/**
 * Позиция одного игрока, который виден на LocatorBar у получателя.
 * [dimension] — идентификатор измерения (например `minecraft:overworld`).
 */
data class LocatorEntry(
    val uuid: UUID,
    val name: String,
    val x: Int,
    val y: Int,
    val z: Int,
    val dimension: Identifier,
) {
    companion object {
        val STREAM_CODEC: StreamCodec<io.netty.buffer.ByteBuf, LocatorEntry> = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, LocatorEntry::uuid,
            ByteBufCodecs.STRING_UTF8, LocatorEntry::name,
            ByteBufCodecs.VAR_INT, LocatorEntry::x,
            ByteBufCodecs.VAR_INT, LocatorEntry::y,
            ByteBufCodecs.VAR_INT, LocatorEntry::z,
            Identifier.STREAM_CODEC, LocatorEntry::dimension,
            ::LocatorEntry,
        )
    }
}

/**
 * Серверный → клиентский payload со списком видимых игроков.
 * Пустой список означает «очистить LocatorBar» (например, компас убрали из рук).
 */
data class PlayerPositionsPayload(val entries: List<LocatorEntry>) : CustomPacketPayload {

    override fun type(): CustomPacketPayload.Type<out CustomPacketPayload> = TYPE

    companion object {
        val TYPE: CustomPacketPayload.Type<PlayerPositionsPayload> =
            CustomPacketPayload.Type(IslandWorldMod.id("locator_positions"))

        private val ENTRIES_CODEC: StreamCodec<io.netty.buffer.ByteBuf, List<LocatorEntry>> =
            ByteBufCodecs.collection(::ArrayList, LocatorEntry.STREAM_CODEC)

        val CODEC: StreamCodec<io.netty.buffer.ByteBuf, PlayerPositionsPayload> =
            StreamCodec.composite(ENTRIES_CODEC, PlayerPositionsPayload::entries, ::PlayerPositionsPayload)

        /** Регистрация типа на обеих сторонах. Вызывать в common-инициализаторе до отправки. */
        fun register() {
            PayloadTypeRegistry.clientboundPlay().register(TYPE, CODEC)
        }
    }
}
