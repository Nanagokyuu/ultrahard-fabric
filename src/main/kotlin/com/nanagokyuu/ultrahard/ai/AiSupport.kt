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

internal object AiSupport {
	internal const val SPIDER_WEB_COOLDOWN_TICKS = 200L
	internal const val SPIDER_WEB_DURATION_TICKS = 120L
	internal val witchPotionSteps = HashMap<UUID, Int>()
	internal val spiderWebCooldowns = HashMap<UUID, Long>()
	internal val temporarySpiderWebs = HashMap<ServerLevel, HashMap<BlockPos, Long>>()
	internal val endermanTeleportCooldowns = HashMap<UUID, Long>()

	internal fun ultraHardLevel(mob: Mob): ServerLevel? {
		val level = mob.level() as? ServerLevel ?: return null
		return level.takeIf(UltraHardDifficulties::isUltraHard)
	}

	internal fun shouldUpdate(mob: Mob): Boolean = mob.tickCount % UltraHardConfigs.values.aiUpdateIntervalTicks == 0

	internal fun isHostile(entity: Entity): Boolean = entity is Enemy || entity is Monster

	internal fun isWitchHealingPriority(entity: Entity): Boolean =
		entity is Pillager || entity is Vindicator || entity is Evoker || entity is Ravager

	internal fun moveTo(mob: Mob, destination: Vec3, speed: Double) {
		mob.navigation.moveTo(destination.x, destination.y, destination.z, speed)
	}

	internal fun horizontalDirection(vector: Vec3): Vec3 {
		val horizontal = Vec3(vector.x, 0.0, vector.z)
		return if (horizontal.lengthSqr() < 1.0E-4) Vec3(1.0, 0.0, 0.0) else horizontal.normalize()
	}

	internal fun shieldFacing(target: LivingEntity): Vec3 {
		val yaw = Math.toRadians(target.yRot.toDouble())
		return Vec3(-kotlin.math.sin(yaw), 0.0, kotlin.math.cos(yaw))
	}

	internal fun isBlockingTarget(target: LivingEntity): Boolean = target is Player && target.isBlocking

	internal fun swarmApproach(mob: Mob, target: LivingEntity, index: Int, size: Int, radius: Double, speed: Double) {
		val facing = horizontalDirection(target.lookAngle)
		val left = Vec3(-facing.z, 0.0, facing.x)
		val destination = if (size > 1 && index == 0) target.position().add(facing.scale(radius * 0.8)) else {
			val side = if (index % 2 == 0) 1.0 else -1.0
			target.position().subtract(facing.scale(radius)).add(left.scale(side * radius * (0.5 + index / 3.0)))
		}
		mob.navigation.moveTo(destination.x, destination.y, destination.z, speed)
	}

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
		mob.navigation.moveTo(destination.x, destination.y, destination.z, speed)
	}
}
