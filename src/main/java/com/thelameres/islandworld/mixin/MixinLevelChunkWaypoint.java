package com.thelameres.islandworld.mixin;

import com.thelameres.islandworld.locator.BlockWaypointTransmitter;
import com.thelameres.islandworld.locator.StoredWaypoint;
import com.thelameres.islandworld.locator.WaypointNames;
import com.thelameres.islandworld.locator.WaypointStore;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Синхронизация блок-энтitей с постоянным хранилищем waypoint-меток.
 *
 * Ключевое правило: точка живёт в {@link WaypointStore} (SavedData уровня), а не в
 * BlockEntity. Признак метки — наличие позиции блока в хранилище, NBT не используется.
 * Поэтому:
 *  - уничтожение блока (removeBlockEntity) — точку удаляем и снимаем с менеджера;
 *  - выгрузка чанка (clearAllBlockEntities) — НИЧЕГО не делаем, точка остаётся на карте;
 *  - загрузка чанка (setBlockEntity) — точку восстанавливаем/подтверждаем по её позиции.
 */
@Mixin(LevelChunk.class)
public abstract class MixinLevelChunkWaypoint {

    @Inject(method = "setBlockEntity", at = @At("TAIL"))
    private void islandworld$trackBlockWaypoint(BlockEntity blockEntity, CallbackInfo ci) {
        if (!(blockEntity instanceof BlockWaypointTransmitter)) return;

        LevelChunk self = (LevelChunk) (Object) this;
        if (!(self.getLevel() instanceof ServerLevel serverLevel)) return;

        WaypointStore store = WaypointStore.of(serverLevel);
        BlockPos pos = blockEntity.getBlockPos();
        // Не метка — нечего восстанавливать
        if (!store.contains(pos)) return;

        // Имя перечитываем из блока: табличку могли переименовать, пока чанк был выгружен.
        Component name = WaypointNames.resolve(blockEntity);
        String nameText = name == null ? null : name.getString();

        // upsert идемпотентен: после повторной загрузки чанка вернётся тот же передатчик,
        // и метка на клиенте сохранит свой UUID (без мигания «удалить/добавить»).
        StoredWaypoint waypoint = store.upsert(pos, nameText);
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
        if (!(blockEntity instanceof BlockWaypointTransmitter)) return;

        StoredWaypoint removed = WaypointStore.of(serverLevel).remove(pos);
        if (removed != null) {
            serverLevel.getWaypointManager().untrackWaypoint(removed);
        }
    }
}
