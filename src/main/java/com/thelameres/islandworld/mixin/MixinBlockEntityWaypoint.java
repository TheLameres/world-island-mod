package com.thelameres.islandworld.mixin;

import com.thelameres.islandworld.locator.BlockWaypointState;
import com.thelameres.islandworld.locator.BlockWaypointTransmitter;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Добавляет Waypoint-возможность табличкам и флагам.
 * <p>
 * ВАЖНО про цели инъекций: берём только методы, которые объявлены НЕПОСРЕДСТВЕННО в
 * SignBlockEntity и BannerBlockEntity — иначе Mixin не найдёт цель и уронит загрузку.
 * <ul>
 *   <li>{@code saveAdditional(ValueOutput)} и {@code loadAdditional(ValueInput)} в обоих классах
 *       переопределены, поэтому инъекция по ним валидна. Сигнатуры именно такие: в 26.3
 *       сериализация идёт через ValueOutput/ValueInput, а не через CompoundTag + HolderLookup.Provider.</li>
 *   <li>{@code setRemoved()} объявлен в BlockEntity, а НЕ в целевых классах — инъекция сюда
 *       не встанет. Снятие метки при уничтожении живёт в MixinLevelChunkWaypoint, на
 *       LevelChunk.removeBlockEntity / clearAllBlockEntities.</li>
 * </ul>
 */
@Mixin({SignBlockEntity.class, BannerBlockEntity.class})
public abstract class MixinBlockEntityWaypoint implements BlockWaypointTransmitter {

    @Unique
    private final BlockWaypointState islandworld$waypointState = new BlockWaypointState();

    @Override
    public BlockWaypointState islandworld$waypointState() {
        return this.islandworld$waypointState;
    }

    /**
     * Сохранение состояния waypoint в NBT чанка. Вызывается при выгрузке чанка и при сохранении мира.
     * Пишем дочерний тег только для включённой метки, чтобы обычные таблички не раздували NBT.
     */
    @Inject(method = "saveAdditional", at = @At("TAIL"))
    private void islandworld$saveWaypoint(ValueOutput output, CallbackInfo ci) {
        if (!this.islandworld$waypointState.isEnabled()) return;
        this.islandworld$waypointState.writeTo(output.child("islandworld_waypoint"));
    }

    /**
     * Загрузка состояния waypoint из NBT. Вызывается при загрузке чанка.
     * <p>
     * Порядок важен и он благоприятный: BlockEntity.loadStatic() вызывает loadAdditional ДО того,
     * как LevelChunk.setBlockEntity() привяжет позицию и зарегистрирует передатчик в WaypointManager.
     * То есть isEnabled восстанавливается раньше, чем происходит trackWaypoint.
     */
    @Inject(method = "loadAdditional", at = @At("TAIL"))
    private void islandworld$loadWaypoint(ValueInput input, CallbackInfo ci) {
        input.child("islandworld_waypoint").ifPresent(
                child -> this.islandworld$waypointState.readFrom(child)
        );
    }
}
