package com.nanagokyuu.ultrahard

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.monster.Enemy
import net.minecraft.world.entity.monster.Monster
import net.minecraft.world.entity.monster.ElderGuardian
import net.minecraft.world.entity.boss.enderdragon.EnderDragon
import net.minecraft.world.entity.boss.wither.WitherBoss
import net.minecraft.world.entity.monster.warden.Warden
import net.minecraft.world.item.ShieldItem

object UltraHardEvents {
	private val bypassCustomDamage = ThreadLocal.withInitial { false }

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

			// Shields are handled by vanilla first, including their durability damage.
			if (entity is ServerPlayer && isHostile(attacker)) {
				if (isBlockingWithShield(entity, source)) {
					return@register true
				}
				if (attacker is LivingEntity) {
					if (isSpecialHostile(attacker)) {
						reflectDamage(attacker, level, source, amount * 2.0f)
						return@register true
					} else {
						oneShot(attacker, level, source)
						return@register false
					}
				}
				return@register true
			}

			// Preserve vanilla attack calculation, but apply a five-fold damage multiplier.
			if (attacker is ServerPlayer && entity !== attacker) {
				multiplyPlayerDamage(entity, level, source, amount)
				return@register false
			}

			true
		}
	}

	private fun isHostile(entity: Entity?): Boolean {
		return entity is Enemy || entity is Monster
	}

	private fun isSpecialHostile(attacker: Entity?): Boolean {
		return attacker is EnderDragon ||
			attacker is WitherBoss ||
			attacker is Warden ||
			attacker is ElderGuardian
	}

	private fun isBlockingWithShield(
		player: ServerPlayer,
		source: net.minecraft.world.damagesource.DamageSource,
	): Boolean {
		return player.isUsingItem &&
			player.getUseItem().item is ShieldItem &&
			!source.`is`(net.minecraft.tags.DamageTypeTags.BYPASSES_SHIELD)
	}

	private fun oneShot(
		entity: LivingEntity,
		level: ServerLevel,
		source: net.minecraft.world.damagesource.DamageSource,
	) {
		if (!entity.isAlive) return
		bypassCustomDamage.set(true)
		try {
			entity.hurtServer(level, source, Float.MAX_VALUE)
		} finally {
			bypassCustomDamage.remove()
		}
		// Totem may have triggered inside hurtServer; ensure it is gone, then finish the kill.
		clearDeathProtectionItems(entity)
		if (entity.isAlive) {
			entity.setHealth(0f)
			entity.die(source)
		}
	}

	private fun clearDeathProtectionItems(entity: LivingEntity) {
		for (hand in net.minecraft.world.InteractionHand.entries) {
			val stack = entity.getItemInHand(hand)
			if (stack.isEmpty) continue
			val hasProtection =
				stack.has(net.minecraft.core.component.DataComponents.DEATH_PROTECTION) ||
					stack.`is`(net.minecraft.world.item.Items.TOTEM_OF_UNDYING)
			if (hasProtection) {
				entity.setItemInHand(hand, net.minecraft.world.item.ItemStack.EMPTY)
			}
		}
	}

	private fun reflectDamage(
		entity: LivingEntity,
		level: ServerLevel,
		source: net.minecraft.world.damagesource.DamageSource,
		amount: Float,
	) {
		if (!entity.isAlive) return
		bypassCustomDamage.set(true)
		try {
			entity.hurtServer(level, source, amount)
		} finally {
			bypassCustomDamage.remove()
		}
	}

	private fun multiplyPlayerDamage(
		entity: LivingEntity,
		level: ServerLevel,
		source: net.minecraft.world.damagesource.DamageSource,
		amount: Float,
	) {
		if (!entity.isAlive) return
		bypassCustomDamage.set(true)
		try {
			entity.hurtServer(level, source, amount * UltraHardDifficulties.PLAYER_ATTACK_DAMAGE_MULTIPLIER)
		} finally {
			bypassCustomDamage.remove()
		}
	}
}
