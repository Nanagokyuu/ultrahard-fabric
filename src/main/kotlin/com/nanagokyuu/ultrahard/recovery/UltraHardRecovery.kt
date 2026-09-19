package com.nanagokyuu.ultrahard.recovery

import com.nanagokyuu.ultrahard.UltraHardMod
import com.nanagokyuu.ultrahard.UltraHardPlayerState
import com.nanagokyuu.ultrahard.difficulty.UltraHardDifficulties
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents
import net.minecraft.core.Registry
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.effect.MobEffect
import net.minecraft.world.effect.MobEffectCategory
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.entity.LivingEntity

/** 两种补给共用互斥判定，持续治疗交给原版效果系统保存和同步。 */
object UltraHardRecovery {
	const val USE_TICKS = 60
	const val BANDAGE_TICKS = 160
	const val SOUP_TICKS = 240
	const val SOUP_COOLDOWN_TICKS = 1200

	val BANDAGED: net.minecraft.core.Holder.Reference<MobEffect> = Registry.registerForHolder<MobEffect, MobEffect>(
		BuiltInRegistries.MOB_EFFECT, UltraHardMod.id("bandaged"), RecoveryEffect(0.5f, 20),
	)
	val NOURISHING: net.minecraft.core.Holder.Reference<MobEffect> = Registry.registerForHolder<MobEffect, MobEffect>(
		BuiltInRegistries.MOB_EFFECT, UltraHardMod.id("nourishing"), RecoveryEffect(1.0f, 30),
	)

	fun register() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register { entity, _, _, damage, blocked ->
			// 完全格挡或未生效的攻击不打断包扎；完成前中断不会扣除物品。
			if (entity !is ServerPlayer || blocked || damage <= 0.0f) return@register
			val interruptedUse = entity.isUsingItem && entity.useItem.`is`(UltraHardRecoveryItems.SIMPLE_BANDAGE)
			if (interruptedUse) entity.stopUsingItem()
			val interruptedHealing = entity.removeEffect(BANDAGED) || entity.removeEffect(NOURISHING)
			if (interruptedUse || interruptedHealing) {
				entity.sendOverlayMessage(Component.translatable("message.ultrahard.bandage_interrupted"))
			}
		}
	}

	fun isRecovering(entity: LivingEntity): Boolean = entity.hasEffect(BANDAGED) || entity.hasEffect(NOURISHING)

	/** 服务端在开始和完成使用时各检查一次，防止双手交替或中途状态变化导致重复治疗。 */
	fun rejection(player: ServerPlayer, soup: Boolean): String? = when {
		!UltraHardDifficulties.isUltraHard(player.level()) -> "message.ultrahard.recovery_difficulty"
		!player.isAlive || player.isSpectator -> "message.ultrahard.recovery_unavailable"
		isRecovering(player) -> "message.ultrahard.already_recovering"
		player.health >= player.maxHealth -> "message.ultrahard.health_full"
		soup && soupCooldownRemaining(player) > 0 -> "message.ultrahard.soup_cooldown"
		else -> null
	}

	fun begin(player: ServerPlayer, soup: Boolean): Boolean {
		if (rejection(player, soup) != null) return false
		val effect = if (soup) NOURISHING else BANDAGED
		val duration = if (soup) SOUP_TICKS else BANDAGE_TICKS
		if (!player.addEffect(MobEffectInstance(effect, duration, 0, false, false, true))) return false
		if (soup) {
			// 使用世界运行刻而非现实时间，重登、重启和重生均不能刷新这次冷却。
			(player as UltraHardPlayerState).ultrahardSetSoupCooldownUntil(
				player.level().server.overworld().gameTime + SOUP_COOLDOWN_TICKS,
			)
			syncSoupCooldown(player)
		}
		player.sendOverlayMessage(Component.translatable(
			if (soup) "message.ultrahard.soup_started" else "message.ultrahard.bandage_started",
		))
		return true
	}

	private fun soupCooldownRemaining(player: ServerPlayer): Int {
		val until = (player as UltraHardPlayerState).ultrahardGetSoupCooldownUntil()
		return (until - player.level().server.overworld().gameTime).coerceIn(0L, SOUP_COOLDOWN_TICKS.toLong()).toInt()
	}

	/** 原版物品冷却负责客户端遮罩，持久字段负责权威判定与重新上线后的恢复。 */
	fun syncSoupCooldown(player: ServerPlayer) {
		val remaining = soupCooldownRemaining(player)
		if (remaining > 0) player.cooldowns.addCooldown(UltraHardMod.id("nourishing_soup"), remaining)
	}

	private class RecoveryEffect(private val healing: Float, private val interval: Int) :
		MobEffect(MobEffectCategory.BENEFICIAL, 0xD9C99A) {
		override fun shouldApplyEffectTickThisTick(duration: Int, amplifier: Int): Boolean = true

		override fun applyEffectTick(level: ServerLevel, entity: LivingEntity, amplifier: Int): Boolean {
			if (!UltraHardDifficulties.isUltraHard(level) || !entity.isAlive || entity.isSpectator) return false
			val duration = entity.getEffect(BuiltInRegistries.MOB_EFFECT.wrapAsHolder(this))?.duration ?: return false
			// 最后一个脉冲在剩余一刻时结算：绷带八次各0.5点，汤八次各1点，避免小数累计误差。
			if (duration % interval == 1) entity.heal(healing)
			return true
		}
	}
}
