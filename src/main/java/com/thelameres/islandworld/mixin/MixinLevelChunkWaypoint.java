package com.thelameres.islandworld.mixin;

import com.thelameres.islandworld.locator.BlockWaypointTransmitter;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Регистрация и снятие блочных waypoint-передатчиков.
 *
 * Ваниль трекает только Entity (ServerLevel$EntityCallbacks), поэтому для BlockEntity
 * нужны свои точки. Все хуки — на LevelChunk, где реально происходит жизненный цикл:
 *  - setBlockEntity        — блок-энтити попал в чанк (в т.ч. при загрузке из NBT);
 *  - removeBlockEntity     — блок-энтити вынут из чанка (ломание блока, взрыв, замена блока);
 *  - clearAllBlockEntities — чанк выгружается целиком.
 *
 * Именно removeBlockEntity, а не BlockEntity.setRemoved(): последний объявлен в BlockEntity,
 * и инъекция в SignBlockEntity/BannerBlockEntity по этому имени цель не находит.
 */
@Mixin(LevelChunk.class)
public abstract class MixinLevelChunkWaypoint {

    @Inject(method = "setBlockEntity", at = @At("TAIL"))
    private void islandworld$trackBlockWaypoint(BlockEntity blockEntity, CallbackInfo ci) {
        if (!(blockEntity instanceof BlockWaypointTransmitter transmitter)) return;

        // Позицию привязываем всегда: она нужна и для connection, и после перезагрузки чанка
        transmitter.islandworld$waypointState().bind(blockEntity.getBlockPos());

        LevelChunk self = (LevelChunk) (Object) this;
        if (!(self.getLevel() instanceof ServerLevel serverLevel)) return;
        if (!transmitter.isTransmittingWaypoint()) return;

        serverLevel.getWaypointManager().trackWaypoint(transmitter);
    }

    /**
     * Блок уничтожен — снимаем метку с WaypointManager, чтобы клиенты перестали получать обновления.
     * Инъекция в HEAD: на этот момент BE ещё лежит в map, поэтому getBlockEntity его возвращает.
     */
    @Inject(method = "removeBlockEntity", at = @At("HEAD"))
    private void islandworld$untrackBlockWaypoint(BlockPos pos, CallbackInfo ci) {
        LevelChunk self = (LevelChunk) (Object) this;
        if (!(self.getLevel() instanceof ServerLevel serverLevel)) return;

        BlockEntity blockEntity = self.getBlockEntity(pos);
        if (!(blockEntity instanceof BlockWaypointTransmitter transmitter)) return;
        if (!transmitter.isTransmittingWaypoint()) return;

        serverLevel.getWaypointManager().untrackWaypoint(transmitter);
    }

    /**
     * Чанк выгружается целиком — снимаем все метки этого чанка.
     * Состояние при этом остаётся в NBT (saveAdditional отрабатывает отдельно), поэтому
     * после повторной загрузки чанка метки восстановятся через setBlockEntity.
     */
    @Inject(method = "clearAllBlockEntities", at = @At("HEAD"))
    private void islandworld$untrackAllBlockWaypoints(CallbackInfo ci) {
        LevelChunk self = (LevelChunk) (Object) this;
        if (!(self.getLevel() instanceof ServerLevel serverLevel)) return;

        for (BlockEntity blockEntity : self.getBlockEntities().values()) {
            if (blockEntity instanceof BlockWaypointTransmitter transmitter
                    && transmitter.isTransmittingWaypoint()) {
                serverLevel.getWaypointManager().untrackWaypoint(transmitter);
            }
        }
    }
}
