package com.nanagokyuu.ultrahard.ai

import net.minecraft.core.BlockPos
import net.minecraft.core.Holder
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.sounds.SoundEvents
import net.minecraft.tags.EntityTypeTags
import net.minecraft.tags.FluidTags
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.player.Player
import net.minecraft.world.entity.monster.Creeper
import net.minecraft.world.entity.monster.EnderMan
import net.minecraft.world.entity.monster.Endermite
import net.minecraft.world.entity.monster.Monster
import net.minecraft.world.entity.monster.Ravager
import net.minecraft.world.entity.monster.Silverfish
import net.minecraft.world.entity.monster.Witch
import net.minecraft.world.entity.monster.Enemy
import net.minecraft.world.entity.monster.illager.Evoker
import net.minecraft.world.entity.monster.illager.Pillager
import net.minecraft.world.entity.monster.illager.Vindicator
import net.minecraft.world.entity.monster.skeleton.AbstractSkeleton
import net.minecraft.world.entity.monster.zombie.Zombie
import net.minecraft.world.entity.monster.spider.Spider
import net.minecraft.world.entity.projectile.Projectile
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownSplashPotion
import net.minecraft.world.entity.raid.Raider
import net.minecraft.world.item.Items
import net.minecraft.world.item.alchemy.Potion
import net.minecraft.world.item.alchemy.PotionContents
import net.minecraft.world.item.alchemy.Potions
import net.minecraft.world.phys.Vec3
import java.util.UUID

import com.nanagokyuu.ultrahard.UltraHardConfigs
import com.nanagokyuu.ultrahard.UltraHardDifficulties
/**
 * 负责与世界环境相关的 AI：亡灵寻找避阳位置，以及蜘蛛产生的临时蛛网。
 * 蛛网只写入空气方块，到期只移除仍为蛛网的位置，不覆盖玩家随后放置的其他方块。
 */
internal object WorldAi {
	@JvmStatic
	fun tickUndeadShelter(mob: Mob) {
		val level = AiSupport.ultraHardLevel(mob) ?: return
		val config = UltraHardConfigs.values
		if (!mob.type.builtInRegistryHolder().`is`(EntityTypeTags.UNDEAD)
			|| mob.isInWater || !level.isBrightOutside || !level.canSeeSky(mob.blockPosition())) return
		if (mob.tickCount % config.undeadShelterSearchIntervalTicks != 0) return

		// 以最近的水源或不能直视天空的可站立空气方块作为避阳终点。
		val center = mob.blockPosition()
		var best: BlockPos? = null
		var bestDistance = Double.MAX_VALUE
		for (xOffset in -config.undeadShelterSearchRadius..config.undeadShelterSearchRadius) {
			for (zOffset in -config.undeadShelterSearchRadius..config.undeadShelterSearchRadius) {
				for (yOffset in -1..2) {
					val candidate = center.offset(xOffset, yOffset, zOffset)
					if (!level.isLoaded(candidate)) continue
					val isWater = level.getFluidState(candidate).`is`(FluidTags.WATER)
					val isShade = level.getBlockState(candidate).isAir && !level.getBlockState(candidate.below()).isAir && !level.canSeeSky(candidate)
					if (!isWater && !isShade) continue
					val distance = candidate.distSqr(center)
					if (distance < bestDistance) {
						best = candidate
						bestDistance = distance
					}
				}
			}
		}
		best?.let { AiSupport.moveTo(mob, Vec3.atBottomCenterOf(it), config.undeadShelterSpeed) }
	}

	@JvmStatic
	fun placeSpiderWeb(level: ServerLevel, player: ServerPlayer) {
		val now = level.gameTime
		if (AiSupport.spiderWebCooldowns[player.uuid]?.let { now < it } == true) return
		val pos = player.blockPosition()
		if (!level.getBlockState(pos).isAir) return

		// 蜘蛛网只作为短暂控制技存在，并给同一玩家留出明确的脱困窗口。
		level.setBlock(pos, net.minecraft.world.level.block.Blocks.COBWEB.defaultBlockState(), 3)
		AiSupport.spiderWebCooldowns[player.uuid] = now + AiSupport.SPIDER_WEB_COOLDOWN_TICKS
		AiSupport.temporarySpiderWebs.getOrPut(level) { HashMap() }[pos] = now + AiSupport.SPIDER_WEB_DURATION_TICKS
	}

	@JvmStatic
	fun tickTemporarySpiderWebs(level: ServerLevel) {
		val now = level.gameTime
		val webs = AiSupport.temporarySpiderWebs[level] ?: return
		// 先复制过期坐标再删除，避免遍历 Map 时修改集合。
		val expired = webs.filterValues { it <= now }.keys.toList()
		for (pos in expired) {
			if (level.getBlockState(pos).`is`(net.minecraft.world.level.block.Blocks.COBWEB)) {
				level.removeBlock(pos, false)
			}
			webs.remove(pos)
		}
		if (webs.isEmpty()) AiSupport.temporarySpiderWebs.remove(level)
		AiSupport.spiderWebCooldowns.entries.removeIf { it.value <= now - AiSupport.SPIDER_WEB_COOLDOWN_TICKS }
	}
}
