package com.nanagokyuu.ultrahard

import com.nanagokyuu.ultrahard.ai.CombatAi
import com.nanagokyuu.ultrahard.ai.RangedAi
import com.nanagokyuu.ultrahard.ai.WorldAi
import com.nanagokyuu.ultrahard.ai.TargetingAi
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

/**
 * Java Mixin 调用战术 AI 的统一入口，保持调用方与具体实现模块解耦。
 * 各方法只负责委托：近战交给 CombatAi，仇恨选敌交给 TargetingAi，远程战术交给 RangedAi，
 * 避阳和临时方块维护交给 WorldAi。难度判断及更新节流由对应实现或调用方负责。
 * 保留 @JvmStatic，使 Java 注入代码可以直接调用静态方法，无需访问 Kotlin 单例字段。
 */
object UltraHardAi {
	@JvmStatic
	fun tickZombie(zombie: Zombie): Unit = CombatAi.tickZombie(zombie)

	@JvmStatic
	fun selectCombatTarget(mob: Mob): Unit = TargetingAi.selectTarget(mob)

	@JvmStatic
	fun recordPlayerDamage(mob: Mob, player: ServerPlayer, damage: Float): Unit = TargetingAi.recordPlayerDamage(mob, player, damage)

	@JvmStatic
	fun shouldKeepCombatTarget(mob: Mob, proposed: LivingEntity?): Boolean = TargetingAi.shouldKeepSelectedTarget(mob, proposed)

	@JvmStatic
	fun shouldIgnoreHostileRetaliation(mob: Mob, target: LivingEntity): Boolean = CombatAi.shouldIgnoreHostileRetaliation(mob, target)

	@JvmStatic
	fun tickSkeleton(skeleton: AbstractSkeleton): Unit = CombatAi.tickSkeleton(skeleton)

	@JvmStatic
	fun shouldFlankShield(mob: Mob, target: LivingEntity): Boolean = CombatAi.shouldFlankShield(mob, target)

	@JvmStatic
	fun flankShield(skeleton: AbstractSkeleton, target: LivingEntity): Unit = CombatAi.flankShield(skeleton, target)

	@JvmStatic
	fun flankShield(mob: Mob, target: LivingEntity, speed: Double): Unit = CombatAi.flankShield(mob, target, speed)

	@JvmStatic
	fun tickSpider(spider: Spider): Unit = CombatAi.tickSpider(spider)

	@JvmStatic
	fun tickVindicator(vindicator: Vindicator): Unit = CombatAi.tickVindicator(vindicator)

	@JvmStatic
	fun tickRavager(ravager: Ravager): Unit = CombatAi.tickRavager(ravager)

	@JvmStatic
	fun tickEnderman(enderman: EnderMan): Unit = CombatAi.tickEnderman(enderman)

	@JvmStatic
	fun tickSilverfish(silverfish: Silverfish): Unit = CombatAi.tickSilverfish(silverfish)

	@JvmStatic
	fun tickEndermite(endermite: Endermite): Unit = CombatAi.tickEndermite(endermite)

	@JvmStatic
	fun tickCreeperShield(creeper: Creeper): Unit = RangedAi.tickCreeperShield(creeper)

	@JvmStatic
	fun shouldFlankCreeperShield(creeper: Creeper): Boolean = RangedAi.shouldFlankCreeperShield(creeper)

	@JvmStatic
	fun retreatSkeleton(skeleton: AbstractSkeleton, target: LivingEntity): Unit = CombatAi.retreatSkeleton(skeleton, target)

	@JvmStatic
	fun tickCreeper(creeper: Creeper): Unit = RangedAi.tickCreeper(creeper)

	@JvmStatic
	fun tickWitch(witch: Witch): Unit = RangedAi.tickWitch(witch)

	@JvmStatic
	fun tickPillager(pillager: Pillager): Unit = RangedAi.tickPillager(pillager)

	@JvmStatic
	fun tickEvoker(evoker: Evoker): Unit = RangedAi.tickEvoker(evoker)

	@JvmStatic
	fun shouldCancelShieldedMelee(mob: Mob, target: Entity): Boolean = CombatAi.shouldCancelShieldedMelee(mob, target)

	@JvmStatic
	fun nextWitchPotion(witch: Witch, target: LivingEntity, original: Holder<Potion>): Holder<Potion> = RangedAi.nextWitchPotion(witch, target, original)

	@JvmStatic
	fun performCloseRangeAttack(witch: Witch, target: LivingEntity): Boolean = RangedAi.performCloseRangeAttack(witch, target)

	@JvmStatic
	fun tickUndeadShelter(mob: Mob): Unit = WorldAi.tickUndeadShelter(mob)

	@JvmStatic
	fun placeSpiderWeb(level: ServerLevel, player: ServerPlayer): Unit = WorldAi.placeSpiderWeb(level, player)

	@JvmStatic
	fun tickTemporarySpiderWebs(level: ServerLevel): Unit = WorldAi.tickTemporarySpiderWebs(level)
}
