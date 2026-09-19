package com.nanagokyuu.ultrahard.events

import com.nanagokyuu.ultrahard.config.UltraHardConfigs
import com.nanagokyuu.ultrahard.enchantment.UltraHardEnchantments
import java.util.UUID
import net.minecraft.core.registries.Registries
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.enchantment.EnchantmentHelper

/** 缓存有效近战治疗量，并在服务端帧末按冷却统一结算，避免同帧群攻重复回血。 */
internal object UltraHardLifesteal {
	/** 记录每名玩家的待结算吸血和上次成功触发时间。 */
	private val lifestealStates = HashMap<UUID, LifestealState>()

	private data class LifestealState(
		/** 同一 tick 内多次命中只保留最高一次回血量。 */
		var pendingHealing: Float = 0.0f,
		/** 上次实际治疗发生的服务器 tick，而不是上次攻击发生的 tick。 */
		var lastTriggerTick: Long = Long.MIN_VALUE,
	)

	fun queueLifesteal(
		player: ServerPlayer,
		level: ServerLevel,
		source: net.minecraft.world.damagesource.DamageSource,
		damage: Float,
	) {
		if (damage <= 0.0f) return
		// 只有玩家本人的近战直接命中才允许吸血；远程、投射物和其他实体伤害一律排除。
		if (source.directEntity !== player || player.getAttackStrengthScale(0.5f) < 1.0f) return

		val enchantment = level.registryAccess()
			.lookupOrThrow(Registries.ENCHANTMENT)
			.getOrThrow(UltraHardEnchantments.LIFESTEAL)
		val levelValue = EnchantmentHelper.getItemEnchantmentLevel(enchantment, player.mainHandItem)
		if (levelValue <= 0) return

		val healingRatio = when {
			levelValue >= UltraHardConfigs.values.lifestealMaximumRatioLevel -> UltraHardConfigs.values.lifestealMaximumRatio
			else -> UltraHardConfigs.values.lifestealBaseRatio * (levelValue + 1)
		}
		// 按实际伤害保留小数治疗，不取整，也不设置最低一血保底。
		val healing = damage * healingRatio
		val state = lifestealStates.getOrPut(player.uuid) { LifestealState() }
		val currentTick = level.gameTime
		// 冷却期间的攻击不进入 pendingHealing，冷却结束后也不会补算。
		if (state.lastTriggerTick != Long.MIN_VALUE
			&& currentTick - state.lastTriggerTick < UltraHardConfigs.values.lifestealCooldownTicks) return
		state.pendingHealing = maxOf(state.pendingHealing, healing)
	}

	/** 保留原有死亡清理和治疗冷却逻辑，不在冷却结束后补算攻击。 */
	fun tick(player: ServerPlayer, currentTick: Long) {
		val state = lifestealStates[player.uuid]
		if (state == null) return
		if (!player.isAlive) {
			// 死亡时清除未结算的治疗，避免复活后凭空获得上一条攻击的回血。
			lifestealStates.remove(player.uuid)
			return
		}
		if (state.pendingHealing <= 0) return
		if (state.lastTriggerTick != Long.MIN_VALUE && currentTick - state.lastTriggerTick < UltraHardConfigs.values.lifestealCooldownTicks) return

		val healing = state.pendingHealing
		player.heal(healing)
		state.pendingHealing = 0.0f
		state.lastTriggerTick = currentTick
	}

	fun clear(player: ServerPlayer) {
		lifestealStates.remove(player.uuid)
	}

	fun clearOffline(playerIds: Set<UUID>) {
		lifestealStates.keys.removeIf { it !in playerIds }
	}
}
