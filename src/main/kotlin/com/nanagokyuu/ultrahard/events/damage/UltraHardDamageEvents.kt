package com.nanagokyuu.ultrahard.events.damage

import com.nanagokyuu.ultrahard.UltraHardAi
import com.nanagokyuu.ultrahard.config.UltraHardConfigs
import com.nanagokyuu.ultrahard.difficulty.UltraHardDifficulties
import com.nanagokyuu.ultrahard.equipment.UltraHardEquipment
import com.nanagokyuu.ultrahard.events.UltraHardLifesteal
import kotlin.math.floor
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents
import net.minecraft.core.particles.ParticleTypes
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.boss.enderdragon.EnderDragon
import net.minecraft.world.entity.boss.wither.WitherBoss
import net.minecraft.world.entity.monster.Enemy
import net.minecraft.world.entity.monster.Monster
import net.minecraft.world.entity.monster.warden.Warden

/** 注册超困难的敌人伤害倍率、玩家输出软上限和吸血伤害记录。 */
internal object UltraHardDamageEvents {
	private val bypassCustomDamage = ThreadLocal.withInitial { false }

	fun register() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register { entity, source, amount ->
			if (bypassCustomDamage.get()) return@register true

			val level = entity.level()
			if (level !is ServerLevel || !UltraHardDifficulties.isUltraHard(level)) return@register true

			val attacker = source.entity
			if (entity is ServerPlayer && isHostile(attacker) && !isBoss(attacker)) {
				val multiplier = UltraHardEquipment.enemyDamageMultiplier(entity)
				if (multiplier == 1.0f) return@register true
				applyDamage(entity, level, source, amount * multiplier)
				return@register false
			}

			if (attacker is ServerPlayer && entity !== attacker && isHostile(entity)) {
				val cappedAmount = cappedPlayerDamage(amount, entity.maxHealth)
				val healthBefore = entity.health
				val absorptionBefore = entity.absorptionAmount
				if (applyDamage(entity, level, source, cappedAmount)) {
					val healthDamage = (healthBefore - entity.health).coerceAtLeast(0.0f)
					val absorptionDamage = (absorptionBefore - entity.absorptionAmount).coerceAtLeast(0.0f)
					if (entity is Mob) UltraHardAi.recordPlayerDamage(entity, attacker, healthDamage + absorptionDamage)
					UltraHardLifesteal.queueLifesteal(attacker, level, source, healthDamage + absorptionDamage)
					if (cappedAmount < amount) spawnDamageCapParticles(level, entity)
				}
				return@register false
			}

			true
		}
	}

	private fun isHostile(entity: Entity?): Boolean = entity is Enemy || entity is Monster

	private fun isBoss(entity: Entity?): Boolean =
		entity is EnderDragon || entity is WitherBoss || entity is Warden

	private fun cappedPlayerDamage(amount: Float, maxHealth: Float): Float {
		val cap = maxHealth * UltraHardConfigs.values.playerAttackCapFraction
		if (amount <= cap) return amount
		val overflow = (amount - cap) * UltraHardConfigs.values.playerAttackOverflowMultiplier
		return floor(cap + overflow)
	}

	private fun applyDamage(
		entity: LivingEntity,
		level: ServerLevel,
		source: net.minecraft.world.damagesource.DamageSource,
		amount: Float,
	): Boolean {
		if (!entity.isAlive) return false
		bypassCustomDamage.set(true)
		try {
			return entity.hurtServer(level, source, amount)
		} finally {
			bypassCustomDamage.remove()
		}
	}

	private fun spawnDamageCapParticles(level: ServerLevel, entity: LivingEntity) {
		level.sendParticles(
			ParticleTypes.ENCHANTED_HIT,
			entity.x,
			entity.y + entity.bbHeight * 0.5,
			entity.z,
			UltraHardConfigs.values.damageCapParticleCount,
			0.25,
			0.25,
			0.25,
			0.0,
		)
	}
}
