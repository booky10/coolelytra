package edu.jorbonism.coolelytra.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Vector3f;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class GameRendererMixin {

    @Final @Shadow private Minecraft minecraft;
    @Unique private float previousRollAngle = 0.0f;

    @Inject(at = @At("HEAD"), method = "renderLevel")
    public void renderWorld(float tickDelta, long limitTime, PoseStack poseStack, CallbackInfo info) {
        if (minecraft.player != null && minecraft.player.isFallFlying()) {
            Vec3 facing = minecraft.player.getForward();
            Vec3 velocity = getPlayerInstantaneousVelocity(tickDelta);

            double horizontalFacing2 = facing.horizontalDistanceSqr();
            double horizontalSpeed2 = velocity.horizontalDistanceSqr();

            float rollAngle = 0.0f;
            if (horizontalFacing2 > 0.0D && horizontalSpeed2 > 0.0D) {
                double dot = (velocity.x * facing.x + velocity.z * facing.z) / Math.sqrt(horizontalFacing2 * horizontalSpeed2); // acos(dot) = angle between facing and velocity vectors
                if (dot >= 1.0) dot = 1.0; // hopefully fix world disappearing occasionally which I assume would be due to ^^^ sqrt precision limits
                else if (dot <= -1.0) dot = -1.0;
                double direction = Math.signum(velocity.x * facing.z - velocity.z * facing.x); // = which side laterally each vector is on
                rollAngle = (float) (Math.atan(Math.sqrt(horizontalSpeed2) * Math.acos(dot) * 1.25) * direction * 57.29577951308);
            }

            // smooth changes to the roll angle and remove the bumpy crunchy
            rollAngle = (float) ((0.15) * rollAngle + 0.85 * previousRollAngle);
            previousRollAngle = rollAngle;

            poseStack.mulPose(Vector3f.ZP.rotationDegrees(rollAngle));
        } else {
            previousRollAngle = 0.0f;
        }
    }

    public Vec3 getPlayerInstantaneousVelocity(float tickDelta) {
        // copying over the important bits of elytra flight code and cleaning it up
        // this is to smooth some jitteriness caused by rotation being frame-accurate but velocity only changing each tick

        if (minecraft.player != null) {
            Vec3 velocity = minecraft.player.getDeltaMovement();
            if (tickDelta < 0.01f) {
                return velocity;
            } else {
                double newVelocityX = velocity.x;
                double newVelocityY = velocity.y;
                double newVelocityZ = velocity.z;

                Vec3 facing = minecraft.player.getLookAngle();
                double pitchRadians = Math.toRadians(minecraft.player.getXRot());

                double horizontalFacing2 = facing.horizontalDistanceSqr();
                double horizontalFacing = Math.sqrt(horizontalFacing2);
                double horizontalSpeed = velocity.horizontalDistance();

                newVelocityY += 0.08 * (-1.0 + horizontalFacing2 * 0.75);

                if (horizontalFacing > 0.0) {
                    if (velocity.y < 0.0) { // falling
                        double lift = newVelocityY * -0.1 * horizontalFacing2;
                        newVelocityX += facing.x * lift / horizontalFacing;
                        newVelocityY += lift;
                        newVelocityZ += facing.z * lift / horizontalFacing;
                    }

                    if (pitchRadians < 0.0f) { // facing upwards
                        double lift = horizontalSpeed * -(double) Mth.sin((float) pitchRadians) * 0.04;
                        newVelocityX += -facing.x * lift / horizontalFacing;
                        newVelocityY += lift * 3.2;
                        newVelocityZ += -facing.z * lift / horizontalFacing;
                    }

                    newVelocityX += (facing.x / horizontalFacing * horizontalSpeed - velocity.x) * 0.1;
                    newVelocityZ += (facing.z / horizontalFacing * horizontalSpeed - velocity.z) * 0.1;
                }

                newVelocityX *= 0.9900000095367432;
                newVelocityY *= 0.9800000190734863;
                newVelocityZ *= 0.9900000095367432;

                return new Vec3(Mth.lerp(tickDelta, velocity.x, newVelocityX), Mth.lerp(tickDelta, velocity.y, newVelocityY), Mth.lerp(tickDelta, velocity.z, newVelocityZ));
            }
        } else {
            return new Vec3(0, 0, 0);
        }
    }
}
