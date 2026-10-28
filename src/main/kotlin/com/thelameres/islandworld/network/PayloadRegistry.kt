package com.thelameres.islandworld.network

import com.thelameres.islandworld.core.Registrable
import com.thelameres.islandworld.hallucination.HallucinationPayload
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.network.protocol.common.custom.CustomPacketPayload

/** Направление пакета: в какой реестр PayloadTypeRegistry его регистрировать. */
enum class Direction {
    /** Сервер -> клиент: clientboundPlay. */
    CLIENTBOUND,

    /** Клиент -> сервер: serverboundPlay. */
    SERVERBOUND,
}

/** Одна запись пакета мода: тип, кодек и направление. */
data class PayloadEntry<T : CustomPacketPayload>(
    val type: CustomPacketPayload.Type<T>,
    val codec: StreamCodec<in RegistryFriendlyByteBuf, T>,
    val direction: Direction,
)

/**
 * Список всех CustomPacketPayload мода. Регистрируется как подсистема через Registrable.
 *
 * register() берёт записи, фильтрует их по направлению и регистрирует в соответствующем реестре.
 * Новый пакет добавляется одной строкой в [PAYLOADS].
 */
object PayloadRegistry : Registrable {

    private val PAYLOADS: List<PayloadEntry<*>> = listOf(
        PayloadEntry(HallucinationPayload.TYPE, HallucinationPayload.CODEC, Direction.CLIENTBOUND),
    )

    override fun register() {
        val clientbound = PayloadTypeRegistry.clientboundPlay()
        val serverbound = PayloadTypeRegistry.serverboundPlay()

        PAYLOADS.filter { it.direction == Direction.CLIENTBOUND }
            .forEach { clientbound.add(it) }

        PAYLOADS.filter { it.direction == Direction.SERVERBOUND }
            .forEach { serverbound.add(it) }
    }

    private fun <T : CustomPacketPayload> PayloadTypeRegistry<RegistryFriendlyByteBuf>.add(entry: PayloadEntry<T>) {
        register(entry.type, entry.codec)
    }
}
