package com.nanagokyuu.ultrahard.mixin.accessor;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.raid.Raider;

@Mixin(targets = "net.minecraft.world.entity.raid.Raid$RaiderType")
public interface RaidRaiderTypeAccessor {
	@Accessor("entityType")
	EntityType<? extends Raider> ultrahard$getEntityType();
}
