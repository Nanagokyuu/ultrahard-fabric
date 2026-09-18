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
import java.util.WeakHashMap

import com.nanagokyuu.ultrahard.UltraHardConfigs
import com.nanagokyuu.ultrahard.UltraHardDifficulties

/**
 * 战术模块共用的几何计算、难度检查和运行期状态。
 * 状态表由服务端逻辑访问，不写入实体存档；地图中的时间值是世界游戏刻数。
 * 路径移动交给原版导航系统，计算出目标点不代表实体一定能够抵达。
 */
internal object AiSupport {
	private data class FailedApproach(val targetId: UUID, val origin: Vec3, val retryAt: Long)
	private val failedApproaches = WeakHashMap<Mob, FailedApproach>()
	/** 同一玩家两次被铺网的最短间隔；200 游戏刻在正常刻速下为 10 秒。 */
	internal const val SPIDER_WEB_COOLDOWN_TICKS = 200L
	/** 临时蛛网的存活时间；清理时仍需确认该位置目前是蛛网。 */
	internal const val SPIDER_WEB_DURATION_TICKS = 120L
	/** 以女巫 UUID 累计近身投药阶段；当前实现不会因更换目标或拉开距离而重置。 */
	internal val witchPotionSteps = HashMap<UUID, Int>()
	/** 键为玩家 UUID，值为下一次允许铺网的时刻；不按蜘蛛或维度单独计冷却。 */
	internal val spiderWebCooldowns = HashMap<UUID, Long>()
	/** 先按世界实例分组，再记录坐标与到期时刻，避免不同维度相同坐标互相覆盖。 */
	internal val temporarySpiderWebs = HashMap<ServerLevel, HashMap<BlockPos, Long>>()
	/** 末影人成功主动瞬移后设置下次允许时刻；失败的落点尝试不会消耗冷却。 */
	internal val endermanTeleportCooldowns = HashMap<UUID, Long>()

	/** 同时过滤客户端世界和非超困难难度；返回空值时调用方应停止自定义行为。 */
	internal fun ultraHardLevel(mob: Mob): ServerLevel? {
		val level = mob.level() as? ServerLevel ?: return null
		return level.takeIf(UltraHardDifficulties::isUltraHard)
	}

	/** 混合实体编号错开同批生成怪物的更新时刻，避免搜索集中在同一帧。 */
	internal fun shouldUpdate(mob: Mob): Boolean = isScheduled(mob, UltraHardConfigs.values.aiUpdateIntervalTicks)

	internal fun isScheduled(mob: Mob, interval: Int): Boolean = Math.floorMod(mob.tickCount.toLong() + mob.id, interval.toLong()) == 0L

	/** 本模组的远程兵种共享射距控制，近战单位仍直接接近玩家。 */
	internal fun isRangedCombatant(mob: Mob): Boolean = mob is AbstractSkeleton || mob is Pillager || mob is Witch || mob is Evoker

	internal fun isHostile(entity: Entity): Boolean = entity is Enemy || entity is Monster

	internal fun isWitchHealingPriority(entity: Entity): Boolean =
		entity is Pillager || entity is Vindicator || entity is Evoker || entity is Ravager

	internal fun moveTo(mob: Mob, destination: Vec3, speed: Double) {
		mob.navigation.moveTo(destination.x, destination.y, destination.z, speed)
	}

	internal fun approachOrPressure(mob: Mob, destination: Vec3, speed: Double) {
		// 失败后两秒内不重复搜索包抄点；换目标或移动超过两格可提前重试。
		val target = mob.target ?: return
		val now = mob.level().gameTime
		val failed = failedApproaches[mob]
		if (failed != null && failed.targetId == target.uuid && now < failed.retryAt && mob.position().distanceToSqr(failed.origin) < 4.0) {
			mob.navigation.moveTo(target, speed)
			return
		}
		val path = mob.navigation.createPath(BlockPos.containing(destination), 0)
		if (path != null && path.canReach() && mob.navigation.moveTo(path, speed)) {
			failedApproaches.remove(mob)
			return
		}
		failedApproaches[mob] = FailedApproach(target.uuid, mob.position(), now + 40L)
		mob.navigation.moveTo(target, speed)
	}

	/** 忽略高度差并归一化；水平向量接近零时使用固定方向，避免后续侧向计算退化。 */
	internal fun horizontalDirection(vector: Vec3): Vec3 {
		val horizontal = Vec3(vector.x, 0.0, vector.z)
		return if (horizontal.lengthSqr() < 1.0E-4) Vec3(1.0, 0.0, 0.0) else horizontal.normalize()
	}

	/** 只使用水平转角计算盾牌正面，抬头或低头不会改变左右绕行的参考方向。 */
	internal fun shieldFacing(target: LivingEntity): Vec3 {
		val yaw = Math.toRadians(target.yRot.toDouble())
		return Vec3(-kotlin.math.sin(yaw), 0.0, kotlin.math.cos(yaw))
	}

	internal fun isBlockingTarget(target: LivingEntity): Boolean = target is Player && target.isBlocking

	/** 群体首位负责正面牵制，其余按编号交替分配左右后方位置；单只实体直接走后方。 */
	internal fun swarmApproach(mob: Mob, target: LivingEntity, index: Int, size: Int, radius: Double, speed: Double) {
		val facing = horizontalDirection(target.lookAngle)
		val left = Vec3(-facing.z, 0.0, facing.x)
		val destination = if (size > 1 && index == 0) target.position().add(facing.scale(radius * 0.8)) else {
			val side = if (index % 2 == 0) 1.0 else -1.0
			target.position().subtract(facing.scale(radius)).add(left.scale(side * radius * (0.5 + index / 3.0)))
		}
		approachOrPressure(mob, destination, speed)
	}

	/** 检查三格竖直空气、落点流体、下方支撑和实体碰撞后尝试瞬移，返回是否实际成功。 */
	internal fun tryEndermanTeleport(enderman: EnderMan, destination: Vec3): Boolean {
		val level = ultraHardLevel(enderman) ?: return false
		val pos = BlockPos.containing(destination.x, destination.y, destination.z)
		if (!level.getBlockState(pos).isAir || !level.getBlockState(pos.above()).isAir || !level.getBlockState(pos.above(2)).isAir) return false
		if (!level.getFluidState(pos).isEmpty || !level.getFluidState(pos.above()).isEmpty || level.getBlockState(pos.below()).isAir) return false
		val offset = Vec3.atBottomCenterOf(pos).subtract(enderman.position())
		if (!level.noCollision(enderman, enderman.boundingBox.move(offset.x, offset.y, offset.z))) return false
		return enderman.teleportTo(level, destination.x, pos.y.toDouble(), destination.z, emptySet(), enderman.yRot, enderman.xRot, false)
	}

	internal fun ambushApproach(mob: Mob, target: LivingEntity, radius: Double, speed: Double) {
		val facing = horizontalDirection(target.lookAngle)
		val left = Vec3(-facing.z, 0.0, facing.x)
		val side = if (mob.id % 2 == 0) 1.0 else -1.0
		val destination = target.position().subtract(facing.scale(radius)).add(left.scale(side * radius * 0.45))
		approachOrPressure(mob, destination, speed)
	}
}
