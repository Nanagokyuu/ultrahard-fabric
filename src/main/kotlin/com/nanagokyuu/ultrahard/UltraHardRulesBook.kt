package com.nanagokyuu.ultrahard

import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.network.Filterable
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.WrittenBookContent

object UltraHardRulesBook {
	fun giveTo(player: ServerPlayer) {
		if (!UltraHardDifficulties.isUltraHard(player.level())) return
		val state = player as UltraHardPlayerState
		if (state.ultrahardHasReceivedRulesBook()) return
		val config = UltraHardConfigs.values

		val book = ItemStack(Items.WRITTEN_BOOK)
		book.set(
			DataComponents.WRITTEN_BOOK_CONTENT,
			WrittenBookContent(
				Filterable.passThrough("超困难规则"),
				"Ultra Hard",
				0,
				listOf(
					Filterable.passThrough(Component.literal("超困难规则\n\n无自然回血。药水、金苹果、睡眠和吸血可治疗。\n\n按主世界昼夜日期，每昼夜最多治疗一次。入睡前生命≥10点且连续睡满5秒则回满；入睡前不足10点则恢复 ${config.sleepHealingAmount} 点且不能跳夜。中途起床重新计时。")),
					Filterable.passThrough(Component.literal("睡眠与跳夜\n\n全部睡眠玩家都睡满5秒且当前生命≥10点时，可按原版人数规则跳夜并清除天气。\n\n跳夜不额外治疗。治疗按跳夜前的日期结算，次日刷新；同昼夜反复睡眠不会重复治疗。")),
					Filterable.passThrough(Component.literal("战斗与进度\n\n友好生物不受限伤和吸血影响。敌人受到的超额伤害保留 ${config.playerAttackOverflowMultiplier * 100}%。\n\n获得任意铁盔甲后敌人伤害为 ${config.enemyDamageAfterIronMultiplier} 倍；获得任意钻石盔甲后为 ${config.enemyDamageAfterDiamondMultiplier} 倍。背包或穿戴均可。龙、凋灵、监守者伤害不变。")),
					Filterable.passThrough(Component.literal("生存与袭击\n\n饥饿消耗为 ${config.hungerExhaustionMultiplier} 倍，连续 ${config.starvingAfterDays} 天未进食会提高到 ${config.starvingExhaustionMultiplier} 倍。\n\n第八波为 ${config.eighthWavePillagers} 个掠夺者、${config.eighthWaveVindicators} 个卫道士、${config.eighthWaveWitches} 个女巫、${config.eighthWaveEvokers} 个唤魔者和 ${config.eighthWaveRavagers} 个劫掠兽。击败后，每位村庄英雄必得吸血 ${config.eighthWaveLifestealBookLevel}，并获得附魔金苹果或不死图腾。")),
					Filterable.passThrough(Component.literal("敌人战术\n\n所有敌对生物优先攻击未举盾的玩家。僵尸会前后包抄，持武器者优先前排；骷髅遇盾会从侧面绕背，绕到背后才射击。苦力怕引爆时敌人会疏散，蜘蛛命中会铺设持续数秒的临时蛛网，并对同一玩家进入冷却。\n\n女巫优先治疗受伤的袭击单位；近身依次投掷虚弱、缓慢、伤害药水。亡灵白天避阳，敌人之间不会因误伤互相仇恨。")),
				),
				false,
			),
		)
		if (!player.inventory.add(book)) {
			player.drop(book, false)
		}
		state.ultrahardSetReceivedRulesBook(true)
	}
}
