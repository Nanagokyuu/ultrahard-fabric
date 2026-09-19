package com.nanagokyuu.ultrahard

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import java.util.UUID

/** 暂存袭击胜利奖励，允许参与者在胜利时离线并在下次上线领取。 */
object UltraHardRaidRewards {
	@JvmStatic
	fun queue(level: net.minecraft.server.level.ServerLevel, player: UUID, giveApple: Boolean) {
		UltraHardRaidRewardState.queue(level, player, giveApple)
	}

	@JvmStatic
	fun deliver(player: ServerPlayer) {
		if (!UltraHardDifficulties.isUltraHard(player.level())) return
		val reward = UltraHardRaidRewardState.take(player.level(), player.uuid) ?: return
		repeat(reward.books) {
			give(player, UltraHardMod.createLifestealBook(player.level(), UltraHardConfigs.values.eighthWaveLifestealBookLevel))
		}
		repeat(reward.apples) { give(player, ItemStack(Items.ENCHANTED_GOLDEN_APPLE)) }
		repeat(reward.totems) { give(player, ItemStack(Items.TOTEM_OF_UNDYING)) }
	}

	private fun give(player: ServerPlayer, stack: ItemStack) {
		if (!player.inventory.add(stack)) player.drop(stack, false)
	}
}
