package com.nanagokyuu.ultrahard.ai

import com.nanagokyuu.ultrahard.config.UltraHardConfigs
import com.nanagokyuu.ultrahard.mixin.accessor.MobSunProtectionAccessor
import java.util.EnumSet
import java.util.WeakHashMap
import kotlin.math.abs
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.tags.EntityTypeTags
import net.minecraft.tags.FluidTags
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.ai.goal.Goal
import net.minecraft.world.entity.monster.Phantom
import net.minecraft.world.phys.Vec3

/**
 * 负责与世界环境相关的 AI：亡灵寻找避阳位置，以及蜘蛛产生的临时蛛网。
 * 蛛网只写入空气方块，到期只移除仍为蛛网的位置，不覆盖玩家随后放置的其他方块。
 */
internal object WorldAi {
	// 使用弱引用记录避阳状态，实体卸载后不阻止其被回收。
	private val sheltering = WeakHashMap<Mob, Boolean>()

	fun isSheltering(mob: Mob): Boolean = sheltering[mob] == true

	/** 以高优先级占用移动任务；是否需要避阳在每次启动与持续检查时动态决定。 */
	fun registerUndeadShelter(mob: Mob) {
		mob.goalSelector.addGoal(-1, UndeadShelterGoal(mob))
	}

	/** 安全点必须已加载、容纳实体，并且是水域或有支撑且遮住眼部天空的位置。 */
	private fun isShelter(level: ServerLevel, mob: Mob, candidate: BlockPos): Boolean {
		if (!level.isLoaded(candidate)) return false
		val water = level.getFluidState(candidate).`is`(FluidTags.WATER)
		val shade = level.getBlockState(candidate).isAir && level.getBlockState(candidate.below()).isFaceSturdy(level, candidate.below(), Direction.UP)
			&& !level.canSeeSky(BlockPos.containing(candidate.x + 0.5, candidate.y + mob.eyeHeight.toDouble(), candidate.z + 0.5))
		return (water || shade) && level.noCollision(mob, mob.boundingBox.move(Vec3.atBottomCenterOf(candidate).subtract(mob.position())))
	}

	/** 近距离交战优先于避阳，但仅适用于超困难中的敌对亡灵。 */
	fun shouldPrioritizeNearbyPlayer(mob: Mob): Boolean {
		val level = AiSupport.ultraHardLevel(mob) ?: return false
		if (!mob.`is`(EntityTypeTags.UNDEAD) || !AiSupport.isHostile(mob)) return false
		return level.players().any { isNearbyPlayer(mob, it) }
	}

	/** 横向为以生物为中心的16×16方形，高差最多8格，不采用圆形半径。 */
	fun isNearbyPlayer(mob: Mob, player: LivingEntity?): Boolean {
		return player is ServerPlayer && player.level() === mob.level()
			&& player.isAlive && !player.isSpectator && !player.isCreative && mob.canAttack(player)
			&& abs(player.x - mob.x) <= 8.0
			&& abs(player.z - mob.z) <= 8.0
			&& abs(player.y - mob.y) <= 8.0
	}

	/** 在安全处持续持有移动控制权，直到入夜、装备防晒物或附近出现玩家。 */
	private class UndeadShelterGoal(private val mob: Mob) : Goal() {
		private var destination: BlockPos? = null
		private var searchedFrom: BlockPos? = null
		private var searchAfter = 0L
		private var seekingWater = false

		init {
			setFlags(EnumSet.of(Flag.MOVE))
		}

		/** 实时读取防晒槽位与昼夜状态，头饰破损后无需重新生成即可恢复避阳。 */
		private fun eligibleLevel(): ServerLevel? {
			val level = AiSupport.ultraHardLevel(mob) ?: return null
			if (shouldPrioritizeNearbyPlayer(mob)) {
				searchAfter = 0L
				searchedFrom = null
				return null
			}
			if (!mob.isAlive || mob is Phantom || mob.fireImmune()
				|| !mob.`is`(EntityTypeTags.UNDEAD) || !mob.`is`(EntityTypeTags.BURN_IN_DAYLIGHT)) return null
			// 使用原版指定的防晒槽位，兼容僵尸马等使用身体装备而非头饰的亡灵。
			val protectionSlot = (mob as MobSunProtectionAccessor).`ultrahard$getSunProtectionSlot`()
			if (!mob.getItemBySlot(protectionSlot).isEmpty) {
				searchAfter = 0L
				searchedFrom = null
				return null
			}
			val daytime = level.dimensionType().hasSkyLight() && !level.dimensionType().hasFixedTime()
				&& Math.floorMod(level.defaultClockTime, 24_000L) < 13_000L
			return level.takeIf { daytime || mob.isOnFire }
		}

		override fun canUse(): Boolean {
			val level = eligibleLevel() ?: return false
			return findDestination(level)
		}

		override fun canContinueToUse(): Boolean = eligibleLevel() != null && destination != null

		override fun requiresUpdateEveryTick(): Boolean = true

		override fun start() {
			sheltering[mob] = true
			mob.navigation.stop()
		}

		override fun stop() {
			sheltering.remove(mob)
			destination = null
			mob.navigation.stop()
			mob.moveControl.setWait()
		}

		override fun tick() {
			val level = eligibleLevel() ?: return
			val current = mob.blockPosition()
			// 火焰状态变化时立即重新排序目的地，避免继续前往不能灭火的阴影。
			val wantsWater = mob.isOnFire && !mob.isInWater
			if (!wantsWater && isShelter(level, mob, current)) destination = current
			if (wantsWater != seekingWater) {
				searchAfter = 0L
				if (wantsWater) destination = null
				seekingWater = wantsWater
			}
			val target = destination
			if (target == null || !isShelter(level, mob, target)) {
				destination = null
				mob.navigation.stop()
				mob.moveControl.setWait()
				if (!findDestination(level)) return
			}
			val selected = destination ?: return
			if (current == selected && isShelter(level, mob, current)) {
				mob.navigation.stop()
				mob.moveControl.setWait()
				mob.zza = 0.0f
				mob.xxa = 0.0f
				return
			}
			if (!AiSupport.isScheduled(mob, UltraHardConfigs.values.undeadShelterSearchIntervalTicks)) return
			if (!mob.navigation.isDone && mob.navigation.path?.target == selected) return
			val path = mob.navigation.createPath(selected, 0)
			if (path != null && path.canReach() && mob.navigation.moveTo(path, UltraHardConfigs.values.undeadShelterSpeed)) return
			destination = null
			searchAfter = level.gameTime + 100L
			searchedFrom = current
		}

		/** 优先尝试最近水源，找不到可达水源再用阴影；失败后限频以控制寻路成本。 */
		private fun findDestination(level: ServerLevel): Boolean {
			val center = mob.blockPosition()
			val now = level.gameTime
			val wantsWater = mob.isOnFire && !mob.isInWater
			if (seekingWater != wantsWater) searchAfter = 0L
			seekingWater = wantsWater
			if (!wantsWater && isShelter(level, mob, center)) {
				destination = center
				return true
			}
			if (now < searchAfter && searchedFrom?.distSqr(center)?.let { it < 16.0 } == true) return false
			if (!AiSupport.isScheduled(mob, UltraHardConfigs.values.undeadShelterSearchIntervalTicks)) return false
			searchedFrom = center
			searchAfter = now + 100L
			val radius = UltraHardConfigs.values.undeadShelterSearchRadius
			val candidates = ArrayList<BlockPos>()
			for (xOffset in -radius..radius) {
				for (zOffset in -radius..radius) {
					for (yOffset in -1..2) {
						val candidate = center.offset(xOffset, yOffset, zOffset)
						if (isShelter(level, mob, candidate)) candidates.add(candidate)
					}
				}
			}
			val ranked = candidates.sortedWith(compareBy<BlockPos> {
				if (wantsWater && !level.getFluidState(it).`is`(FluidTags.WATER)) 1 else 0
			}.thenBy { it.distSqr(center) })
			for (candidate in ranked.take(8)) {
				val path = mob.navigation.createPath(candidate, 0)
				if (path != null && path.canReach()) {
					destination = candidate
					return true
				}
			}
			return false
		}
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
