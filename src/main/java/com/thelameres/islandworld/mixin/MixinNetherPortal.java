package com.thelameres.islandworld.mixin;

import com.thelameres.islandworld.portal.PortalRouting;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.portal.TeleportTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Перехват порталов Нижнего мира (план, раздел 5.4).
 * <p>
 * Здесь нет ни одного решения: миксин только спрашивает у {@link PortalRouting},
 * куда телепортировать, и подставляет ответ вместо ванильного поведения.
 * Вся логика — в Kotlin.
 */
@Mixin(NetherPortalBlock.class)
public class MixinNetherPortal {

    @Inject(method = "getPortalDestination", at = @At("HEAD"), cancellable = true)
    private void islandworld$onGetDestination(ServerLevel level, Entity entity, BlockPos pos, CallbackInfoReturnable<TeleportTransition> cir) {
        TeleportTransition transition = PortalRouting.resolveDestination(level, entity, pos);
        if (transition != null) {
            cir.setReturnValue(transition);
        }
    }
}
