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


}
