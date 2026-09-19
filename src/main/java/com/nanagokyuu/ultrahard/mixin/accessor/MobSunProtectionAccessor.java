package com.nanagokyuu.ultrahard.mixin.accessor;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** 访问原版防晒装备槽，避免将所有亡灵的保护装备硬编码为头部。 */
@Mixin(Mob.class)
public interface MobSunProtectionAccessor {
	@Invoker("sunProtectionSlot")
	EquipmentSlot ultrahard$getSunProtectionSlot();
}
