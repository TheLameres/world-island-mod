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
 * Регистрация блочных waypoint-передатчиков.
 *
 * Ваниль трекает только Entity (ServerLevel$EntityCallbacks), поэтому для BlockEntity
 * нужны свои точки. Все три хука — на LevelChunk, где реально происходит жизненный цикл:
 *  - setBlockEntity              — блок-энтити попал в чанк (setLevel + map.put внутри);
 *  - removeBlockEntity           — блок-энтити вынут из чанка;
 *  - clearAllBlockEntities       — чанк выгружается целиком.
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

    @Inject(method = "removeBlockEntity", at = @At("HEAD"))
    private void islandworld$untrackBlockWaypoint(BlockPos pos, CallbackInfo ci) {
        LevelChunk self = (LevelChunk) (Object) this;
        if (!(self.getLevel() instanceof ServerLevel serverLevel)) return;

        BlockEntity blockEntity = self.getBlockEntity(pos);
        if (!(blockEntity instanceof BlockWaypointTransmitter transmitter)) return;

        serverLevel.getWaypointManager().untrackWaypoint(transmitter);
    }

    @Inject(method = "clearAllBlockEntities", at = @At("HEAD"))
    private void islandworld$untrackAllBlockWaypoints(CallbackInfo ci) {
        LevelChunk self = (LevelChunk) (Object) this;
        if (!(self.getLevel() instanceof ServerLevel serverLevel)) return;

        for (BlockEntity blockEntity : self.getBlockEntities().values()) {
            if (blockEntity instanceof BlockWaypointTransmitter transmitter) {
                serverLevel.getWaypointManager().untrackWaypoint(transmitter);
            }
        }
    }
}
