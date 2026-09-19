package com.nanagokyuu.ultrahard.ai

import com.nanagokyuu.ultrahard.config.UltraHardConfigs
import java.util.WeakHashMap
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.tags.EntityTypeTags
import net.minecraft.tags.FluidTags
import net.minecraft.world.entity.Mob
import net.minecraft.world.phys.Vec3

/**
 * 负责与世界环境相关的 AI：亡灵寻找避阳位置，以及蜘蛛产生的临时蛛网。
 * 蛛网只写入空气方块，到期只移除仍为蛛网的位置，不覆盖玩家随后放置的其他方块。
 */
internal object WorldAi {
	private data class Shelter(val origin: BlockPos, var destination: BlockPos?, var expiresAt: Long)
	private val shelters = WeakHashMap<Mob, Shelter>()

	/** 缓存落点仍需确认区块已加载、遮阳条件和完整实体碰撞体积。 */
	private fun isShelter(level: ServerLevel, mob: Mob, candidate: BlockPos): Boolean {
		if (!level.isLoaded(candidate)) return false
		val water = level.getFluidState(candidate).`is`(FluidTags.WATER)
		val shade = level.getBlockState(candidate).isAir && !level.getBlockState(candidate.below()).isAir && !level.canSeeSky(candidate)
		return (water || shade) && level.noCollision(mob, mob.boundingBox.move(Vec3.atBottomCenterOf(candidate).subtract(mob.position())))
	}

	@JvmStatic
	fun tickUndeadShelter(mob: Mob) {
		val level = AiSupport.ultraHardLevel(mob) ?: return
		val config = UltraHardConfigs.values
		if (!mob.type.builtInRegistryHolder().`is`(EntityTypeTags.UNDEAD)
			|| mob.isInWater || !level.isBrightOutside || !level.canSeeSky(mob.blockPosition())) {
			shelters.remove(mob)
			return
		}
		if (!AiSupport.isScheduled(mob, config.undeadShelterSearchIntervalTicks)) return

		// 成功和失败都缓存五秒；实体移动较远或落点失效时才提前重新扫描。
		val center = mob.blockPosition()
		val now = level.gameTime
		val cached = shelters[mob]
		if (cached != null && now < cached.expiresAt && center.distSqr(cached.origin) < 16.0) {
			val destination = cached.destination ?: return
			if (isShelter(level, mob, destination)) {
				if (!mob.navigation.isDone && mob.navigation.path?.target == destination) return
				val path = mob.navigation.createPath(destination, 0)
				if (path != null && path.canReach() && mob.navigation.moveTo(path, config.undeadShelterSpeed)) return
				cached.destination = null
				cached.expiresAt = now + 100L
				return
			}
		}
		val candidates = ArrayList<BlockPos>()
		for (xOffset in -config.undeadShelterSearchRadius..config.undeadShelterSearchRadius) {
			for (zOffset in -config.undeadShelterSearchRadius..config.undeadShelterSearchRadius) {
				for (yOffset in -1..2) {
					val candidate = center.offset(xOffset, yOffset, zOffset)
					if (isShelter(level, mob, candidate)) candidates.add(candidate)
				}
			}
		}
		// 只尝试最近的八个落点，给每次完整搜索设置明确的寻路预算。
		for (destination in candidates.sortedBy { it.distSqr(center) }.take(8)) {
			val path = mob.navigation.createPath(destination, 0)
			if (path != null && path.canReach() && mob.navigation.moveTo(path, config.undeadShelterSpeed)) {
				shelters[mob] = Shelter(center, destination, now + 100L)
				return
			}
		}
		shelters[mob] = Shelter(center, null, now + 100L)
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
