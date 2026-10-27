package com.thelameres.islandworld.mixin;

import com.thelameres.islandworld.locator.BlockWaypointTransmitter;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Помечает таблички и флаги как носители waypoint-метки.
 * <p>
 * Миксин намеренно пустой: поля состояния и инъекции в сериализацию не нужны.
 * Источник истины о метке — {@link com.thelameres.islandworld.locator.WaypointStore},
 * который ключуется по {@code BlockPos}. NBT на блоке не хранится, поэтому:
 * <ul>
 *   <li>копирование блока (креатив, structure block, /setblock с NBT) не переносит
 *       метку на копию — дубликат по определению невозможен, позиция уникальна;</li>
 *   <li>нет второго источника истины, который мог бы разойтись с хранилищем;</li>
 *   <li>нет двойной инициализации поля в конструкторах: SignBlockEntity и
 *       BannerBlockEntity делегируют 2-аргументный конструктор в 3-аргументный
 *       через {@code this(...)}, и инициализатор поля скопировался бы в оба.</li>
 * </ul>
 * HangingSignBlockEntity наследует SignBlockEntity, поэтому висячие таблички
 * покрыты автоматически.
 */
@Mixin({SignBlockEntity.class, BannerBlockEntity.class})
public abstract class MixinBlockEntityWaypoint implements BlockWaypointTransmitter {
}
