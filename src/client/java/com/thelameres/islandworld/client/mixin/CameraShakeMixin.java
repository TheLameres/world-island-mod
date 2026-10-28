package com.thelameres.islandworld.client.mixin;

import com.thelameres.islandworld.client.shake.CameraShake;
import net.minecraft.client.Camera;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Тряска камеры (план 4.4, фаза C).
 *
 * Инжект в {@code Camera.getViewRotationMatrix}: смещение влияет только на матрицу вида,
 * которой рисуется мир. {@code xRot}, {@code yRot} и {@code rotation()} не меняются — ими пользуются
 * игровая логика и прицеливание, поэтому тряска их не сбивает.
 *
 * Математика — в {@link CameraShake} (Kotlin, без Minecraft). Здесь только подстановка результата.
 */
@Mixin(Camera.class)
public abstract class CameraShakeMixin {

    @Inject(method = "getViewRotationMatrix", at = @At("RETURN"))
    private void islandworld$shake(Matrix4f out, CallbackInfoReturnable<Matrix4f> cir) {
        float[] offset = CameraShake.currentOffsetDegrees();
        if (offset[0] == 0f && offset[1] == 0f) {
            return;
        }
        // Поворот на малое смещение: yaw вокруг Y, pitch вокруг X. Умножаем справа, чтобы не трогать базовый поворот.
        Matrix4f shake = new Matrix4f()
                .rotateY((float) Math.toRadians(offset[0]))
                .rotateX((float) Math.toRadians(offset[1]));
        cir.getReturnValue().mul(shake);
    }
}
