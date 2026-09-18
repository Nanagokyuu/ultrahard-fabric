package com.nanagokyuu.ultrahard

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents
import net.minecraft.core.registries.Registries
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.monster.Enemy
import net.minecraft.world.entity.monster.Monster
import net.minecraft.world.item.enchantment.EnchantmentHelper
import kotlin.math.round
import java.util.UUID

object UltraHardEvents {
	private val bypassCustomDamage = ThreadLocal.withInitial { false }
	private val lifestealStates = HashMap<UUID, LifestealState>()

	private data class LifestealState(
		var pendingHealing: Int = 0,
		var lastTriggerTick: Long = Long.MIN_VALUE,
	)

	fun register() {
		ServerLivingEntityEvents.ALLOW_DAMAGE.register { entity, source, amount ->
			if (bypassCustomDamage.get()) {
				return@register true
			}

			val level = entity.level()
			if (level !is ServerLevel || !UltraHardDifficulties.isUltraHard(level)) {
				return@register true
			}

			val attacker = source.entity

			// 保留原版困难模式的伤害计算，并将敌对生物对玩家造成的伤害翻倍。
			if (entity is ServerPlayer && isHostile(attacker)) {
				applyDamage(entity, level, source, amount * UltraHardDifficulties.ENEMY_DAMAGE_MULTIPLIER)
				return@register false
			}

			// 保留原版玩家伤害，但将单次攻击限制为目标最大生命值的 25%。
			if (attacker is ServerPlayer && entity !== attacker) {
				val cappedAmount = minOf(
					amount * UltraHardDifficulties.PLAYER_ATTACK_DAMAGE_MULTIPLIER,
					entity.maxHealth * UltraHardDifficulties.MAX_ATTACK_DAMAGE_FRACTION,
				)
				val healthBefore = entity.health
				val absorptionBefore = entity.absorptionAmount
				if (applyDamage(entity, level, source, cappedAmount)) {
					val healthDamage = (healthBefore - entity.health).coerceAtLeast(0.0f)
					val absorptionDamage = (absorptionBefore - entity.absorptionAmount).coerceAtLeast(0.0f)
					queueLifesteal(attacker, level, healthDamage + absorptionDamage)
				}
				return@register false
			}

			true
		}

		ServerTickEvents.END_SERVER_TICK.register { server ->
			val currentTick = server.overworld().gameTime
			for (player in server.playerList.players) {
				val state = lifestealStates[player.uuid] ?: continue
				if (state.pendingHealing <= 0) continue
				if (state.lastTriggerTick != Long.MIN_VALUE && currentTick - state.lastTriggerTick < 20L) continue

				player.heal(state.pendingHealing.toFloat())
				state.pendingHealing = 0
				state.lastTriggerTick = currentTick
			}
			lifestealStates.keys.removeIf { uuid -> server.playerList.getPlayer(uuid) == null }
		}
	}

	private fun isHostile(entity: Entity?): Boolean {
		return entity is Enemy || entity is Monster
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

	private fun queueLifesteal(player: ServerPlayer, level: ServerLevel, damage: Float) {
		if (damage <= 0.0f) return

		val enchantment = level.registryAccess()
			.lookupOrThrow(Registries.ENCHANTMENT)
			.getOrThrow(UltraHardEnchantments.LIFESTEAL)
		val levelValue = EnchantmentHelper.getItemEnchantmentLevel(enchantment, player.mainHandItem)
		if (levelValue <= 0) return

		val healingRatio = when {
			levelValue >= 9 -> 1.0f
			else -> 0.1f * (levelValue + 1)
		}
		val healing = round(damage * healingRatio).toInt().coerceAtLeast(1)
		val state = lifestealStates.getOrPut(player.uuid) { LifestealState() }
		state.pendingHealing = maxOf(state.pendingHealing, healing)
	}

}
