package com.nanagokyuu.ultrahard.mixin.accessor;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.raid.Raider;

/**
 * 读取原版袭击兵种内部类型对应的实体类型，避免依赖兵种枚举序号来配置最终波阵容。
 */
@Mixin(targets = "net.minecraft.world.entity.raid.Raid$RaiderType")
public interface RaidRaiderTypeAccessor {
	@Accessor("entityType")
	EntityType<? extends Raider> ultrahard$getEntityType();
}
