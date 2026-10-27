package com.thelameres.islandworld.mixin;

import com.thelameres.islandworld.locator.BlockWaypointState;
import com.thelameres.islandworld.locator.BlockWaypointTransmitter;
import com.thelameres.islandworld.locator.RegistryWaypoint;
import com.thelameres.islandworld.locator.WaypointRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Синхронизация блок-энтitей с постоянным реестром waypoint-меток.
 *
 * Ключевое правило: точка живёт в {@link WaypointRegistry} (SavedData уровня), а не в
 * BlockEntity. Поэтому:
 *  - уничтожение блока (removeBlockEntity) — точку удаляем и снимаем с менеджера;
 *  - выгрузка чанка (clearAllBlockEntities) — НИЧЕГО не делаем, точка остаётся на карте;
 *  - загрузка чанка (setBlockEntity) — точку восстанавливаем/подтверждаем по её UUID.
 */
@Mixin(LevelChunk.class)
public abstract class MixinLevelChunkWaypoint {

    @Inject(method = "setBlockEntity", at = @At("TAIL"))
    private void islandworld$trackBlockWaypoint(BlockEntity blockEntity, CallbackInfo ci) {
        if (!(blockEntity instanceof BlockWaypointTransmitter transmitter)) return;

        BlockWaypointState state = transmitter.islandworld$waypointState();
        if (!state.isEnabled()) return;

        LevelChunk self = (LevelChunk) (Object) this;
        if (!(self.getLevel() instanceof ServerLevel serverLevel)) return;

        // upsert идемпотентен: после повторной загрузки чанка вернётся тот же передатчик,
        // и метка на клиенте сохранит свой UUID (без мигания «удалить/добавить»).
        WaypointRegistry registry = WaypointRegistry.of(serverLevel);
        RegistryWaypoint waypoint = registry.upsert(
                state.id(),
                blockEntity.getBlockPos(),
                state.nameOrNull(),
                state.getColorRgb()
        );
        serverLevel.getWaypointManager().trackWaypoint(waypoint);
    }

    /**
     * Блок уничтожен — метка должна исчезнуть у всех игроков и не воскреснуть.
     * Инъекция в HEAD: на этот момент BE ещё лежит в map, поэтому getBlockEntity его находит.
     */
    @Inject(method = "removeBlockEntity", at = @At("HEAD"))
    private void islandworld$untrackBlockWaypoint(BlockPos pos, CallbackInfo ci) {
        LevelChunk self = (LevelChunk) (Object) this;
        if (!(self.getLevel() instanceof ServerLevel serverLevel)) return;

        BlockEntity blockEntity = self.getBlockEntity(pos);
        if (!(blockEntity instanceof BlockWaypointTransmitter transmitter)) return;

        BlockWaypointState state = transmitter.islandworld$waypointState();
        if (!state.isEnabled()) return;

        WaypointRegistry registry = WaypointRegistry.of(serverLevel);
        RegistryWaypoint removed = registry.remove(state.id());
        if (removed != null) {
            serverLevel.getWaypointManager().untrackWaypoint(removed);
        }
    }
}
