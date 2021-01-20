package org.kilocraft.essentials.mixin.patch;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.boss.WitherEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.tag.BlockTags;
import net.minecraft.world.World;
import org.kilocraft.essentials.api.KiloEssentials;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.security.InvalidKeyException;

@Mixin(WitherEntity.class)
public abstract class WitherEntityMixin extends HostileEntity {

    @Shadow public abstract int getInvulnerableTimer();

    protected WitherEntityMixin(EntityType<? extends HostileEntity> entityType, World world) {
        super(entityType, world);
    }

    @Inject(method = "tickMovement", at = @At(value = "HEAD"))
    public void moveIfInBedrock(CallbackInfo ci) {
        try {
            if (this.getInvulnerableTimer() > 0) {
                if (this.getEntityWorld().getBlockState(this.getBlockPos().add(0, KiloEssentials.getInstance().getSettingManager().getInteger("wither_check_distance"), 0)).isIn(BlockTags.WITHER_IMMUNE)
                || this.getEntityWorld().getBlockState(this.getBlockPos().add(0, KiloEssentials.getInstance().getSettingManager().getInteger("wither_check_distance") + 1, 0)).isIn(BlockTags.WITHER_IMMUNE)) {
                    this.updatePosition(this.getPos().x, (this.getPos().y + KiloEssentials.getInstance().getSettingManager().getDouble("wither_tp_distance")), this.getPos().z);
                }
            }
        } catch (InvalidKeyException ignored) {
        }
    }

}