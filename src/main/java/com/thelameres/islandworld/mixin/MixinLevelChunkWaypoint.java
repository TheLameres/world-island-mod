package com.thelameres.islandworld.mixin;

import com.thelameres.islandworld.locator.WaypointChunkHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Точки жизненного цикла блок-энтitей в чанке.
 * <p>
 * Здесь нет ни одного решения: обе инъекции только передают управление в
 * {@link WaypointChunkHooks}. Вся логика — в Kotlin.
 * <ul>
 *   <li>{@code setBlockEntity} TAIL — BE попал в чанк (установка, загрузка из NBT);</li>
 *   <li>{@code removeBlockEntity} HEAD — BE вынимается (ломание, взрыв, замена блока).
 *       Именно HEAD: на этот момент BE ещё лежит в map чанка, и хук может его опознать.</li>
 * </ul>
 * Хука на {@code clearAllBlockEntities} намеренно нет: выгрузка чанка не должна снимать
 * метку, иначе точка пропадала бы с карты, пока блок не загружен.
 */
@Mixin(LevelChunk.class)
public abstract class MixinLevelChunkWaypoint {

    @Inject(method = "setBlockEntity", at = @At("TAIL"))
    private void islandworld$onBlockEntityAdded(BlockEntity blockEntity, CallbackInfo ci) {
        WaypointChunkHooks.onBlockEntityAdded((LevelChunk) (Object) this, blockEntity);
    }

    @Inject(method = "removeBlockEntity", at = @At("HEAD"))
    private void islandworld$onBlockEntityRemoved(BlockPos pos, CallbackInfo ci) {
        WaypointChunkHooks.onBlockEntityRemoved((LevelChunk) (Object) this, pos);
    }
}
