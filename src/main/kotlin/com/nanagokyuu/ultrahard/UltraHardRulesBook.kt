package com.nanagokyuu.ultrahard

import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.network.Filterable
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.component.WrittenBookContent

/**
 * 在玩家加入超困难世界时发放玩法说明，领取标记与玩家存档一起保存。
 * 书页在发放时读取配置并生成文本快照；之后修改配置不会更新已经发出的书。
 */
object UltraHardRulesBook {
	/** 背包满时掉落书籍，随后仍标记已领取，避免玩家反复重登刷取规则书。 */
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
					Filterable.passThrough(Component.literal("超困难规则\n\n无自然回血。药水、金苹果、睡眠和吸血可治疗。\n\n按主世界昼夜日期，每昼夜最多睡眠治疗一次。连续睡满5秒后恢复最大生命值的 ${config.sleepHealingFraction * 100}%（${config.sleepHealingMinimum}～${config.sleepHealingMaximum}点）。睡眠治疗不影响正常跳过夜晚。中途起床重新计时。")),
					Filterable.passThrough(Component.literal("睡眠与跳夜\n\n睡眠治疗与跳夜相互独立。连续睡满5秒后结算每日治疗；提前起床不会消耗次数。跳夜完全沿用原版睡眠人数规则，每天最多睡眠治疗一次。")),
					Filterable.passThrough(Component.literal("饥饿与耐久\n\n饥饿消耗为 ${config.hungerExhaustionMultiplier} 倍，连续 ${config.starvingAfterDays} 天未进食会提高到 ${config.starvingExhaustionMultiplier} 倍。\n\n工具、盔甲、盾牌和其它耐久装备的耐久消耗为 ${config.durabilityDamageMultiplier} 倍；金制工具和金制盔甲不受影响。")),
					Filterable.passThrough(Component.literal("战斗与装备\n\n攻击敌对生物时，伤害超过其最大生命值 ${config.playerAttackCapFraction * 100}% 的部分保留 ${config.playerAttackOverflowMultiplier * 100}%。友好生物和玩家互殴不受影响。\n\n敌人伤害会根据当前护甲值连续增加，20点护甲时达到 ${1.0f + config.maximumArmorDamageBonus} 倍；钻石甲和下界合金甲均按20点护甲计算。当前生命每击杀 ${config.killDamageMilestoneInterval} 个敌对生物会降低敌人伤害，最高降低 ${(1.0f - config.killDamageMinimumMultiplier) * 100}%；死亡、创造或旁观会清空击杀连击。")),
					Filterable.passThrough(Component.literal("吸血与掉落\n\n吸血可附加在剑和斧上，正常最高 III 级；I/II/III 级分别恢复最终伤害的 20%/30%/40%。治疗保留小数，没有最低一血保底。只对敌对生物的满蓄力直接近战生效，每秒至多一次。\n\n沙漠神殿会生成 I/II/III 级吸血书；袭击胜利奖励吸血 ${config.eighthWaveLifestealBookLevel}。")),
					Filterable.passThrough(Component.literal("生存与袭击\n\n敌人护甲生成概率为原版 ${config.mobArmorSpawnMultiplier} 倍。\n\n第八波为 ${config.eighthWavePillagers} 个掠夺者、${config.eighthWaveVindicators} 个卫道士、${config.eighthWaveWitches} 个女巫、${config.eighthWaveEvokers} 个唤魔者和 ${config.eighthWaveRavagers} 个劫掠兽。持续参与第八波并在袭击区域内停留至少5秒的玩家，胜利后均可获得吸血 ${config.eighthWaveLifestealBookLevel}，并获得附魔金苹果或不死图腾。")),
					Filterable.passThrough(Component.literal("装备与仇恨\n\n每件皮甲1、锁链甲2、铁甲3、金甲2、钻石甲4、下界合金甲5分。主手或副手持盾加3分，双持不重复，背包不计。\n\n敌人不只看装备：造成实际伤害会积累仇恨，越远优先级越低，近身攻击会引发反击。新目标需可见，创造和旁观玩家不参与。")),
					Filterable.passThrough(Component.literal("仇恨规则\n\n每点实际伤害增加 ${config.threatPerDamage} 仇恨，上限 ${config.threatMaximum}，每秒衰减 ${config.threatDecayPerSecond}。远程伤害也计入；30秒未攻击、死亡、离线或换维度后清理。\n\n目标默认锁定 ${config.targetLockTicks / 20.0} 秒；失效时提前重选。主动输出可吸引火力，近战不会执着追逐不可达目标。")),
					Filterable.passThrough(Component.literal("敌人战术 I\n\n僵尸前后包抄，持武器者优先前排；敌人遇盾优先绕行。\n\n绕行不可达、3秒无进展或累计6秒未成功时，正面施压5秒。近战逼近，远程保持射距，退路受阻时原地射击或施法；苦力怕正常引爆。\n\n蜘蛛命中铺网，蠹虫和末影螨侧面包围。")),
					Filterable.passThrough(Component.literal("敌人战术 II\n\n末影人在玩家直视或正面举盾时会尝试瞬移到盲区。女巫优先治疗受伤的袭击单位；玩家近身时依次投掷虚弱、缓慢、伤害药水。\n\n亡灵白天会寻找水或阴凉处；敌人之间不会因误伤互相转移仇恨。具体距离、速度和冷却时间可在配置中调整。")),
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
