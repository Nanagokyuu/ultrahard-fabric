package com.nanagokyuu.ultrahard.mixin.accessor;

import java.util.Set;
import java.util.UUID;
import net.minecraft.world.entity.raid.Raid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * 暴露袭击记录的村庄英雄 UUID 集合，供胜利奖励逻辑查找在线玩家；不自行修改英雄名单。
 */
@Mixin(Raid.class)
public interface RaidAccessor {
	@Accessor("heroesOfTheVillage")
	Set<UUID> ultrahard$getHeroesOfTheVillage();
}
