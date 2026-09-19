package com.nanagokyuu.ultrahard.rules

import com.nanagokyuu.ultrahard.config.UltraHardConfig
import net.minecraft.network.chat.Component
import net.minecraft.server.network.Filterable
import java.math.BigDecimal

/** 按主题生成短书页；显示当前配置而非写死默认值，避免长段文字挤在同一页。 */
internal object UltraHardRulePages {
	fun create(config: UltraHardConfig): List<Filterable<Component>> = listOf(
		page("超困难规则",
			"基于困难模式，取消食物和饱和度自然回血。药水、恢复道具、金苹果、睡眠和吸血仍可治疗。",
			"本书是领取时的配置快照；修改配置后重启生效，但已发出的书不会自动更新。"),
		page("睡眠治疗",
			"连续睡满5秒，每主世界昼夜最多治疗一次。恢复最大生命值的 ${percent(config.sleepHealingFraction)}%，限制在 ${number(config.sleepHealingMinimum)}～${number(config.sleepHealingMaximum)} 点。",
			"提前起床重新计时；死亡和重登不刷新当日治疗次数。"),
		page("睡眠与跳夜",
			"睡眠不再因重伤锁住夜晚。跳夜人数和条件沿用原版；睡够5秒的玩家在跳夜前结算治疗。",
			"若队友提前满足跳夜条件，未睡够5秒者不会获得本次治疗。"),
		page("前期恢复道具",
			"简易绷带：三张纸横排，羊毛放在中间纸的上方或下方，合成三个。使用3秒后，在8秒内恢复4点生命值。",
			"滋补汤：碗、任意熟肉、任意两个蔬菜合成。使用3秒后，在12秒内恢复8点生命值；不可堆叠，每分钟最多成功使用一次。"),
		page("恢复限制",
			"绷带和滋补汤仅在超困难生效，不能在满血时使用，持续治疗不能互相叠加。受到有效伤害会中断包扎和剩余治疗。",
			"绷带读条完成前中断不扣物品；完成后已消耗，治疗被打断不返还。滋补汤治疗被打断不返还材料，也不重置冷却。"),
		page("补给与使用",
			"简易绷带也会出现在多种原版探索宝箱中，开局奖励箱固定追加3个。绷带最多堆叠16个。",
			"滋补汤成功使用后返还空碗，并补充饥饿值。重登、重启和重生不能刷新汤的冷却；完全格挡不打断持续治疗。"),
		page("饥饿与耐久",
			"饥饿消耗 ${number(config.hungerExhaustionMultiplier)} 倍；超过 ${config.starvingAfterDays} 游戏日未进食后为 ${number(config.starvingExhaustionMultiplier)} 倍。",
			"工具、武器、护甲、盾牌等适用装备耐久消耗 ${config.durabilityDamageMultiplier} 倍；金制工具和金制护甲豁免。"),
		page("护甲动态倍率",
			"每点当前护甲增加 ${percent(config.armorDamageScaling)}% 输入伤害，最多计20点；额外倍率上限 ${percent(config.maximumArmorDamageBonus)}%。",
			"不计护甲韧性。满钻石甲与下界合金甲相同；背包不计，换装即时生效。"),
		page("伤害如何计算",
			"敌人输入伤害 × 护甲倍率 × 战斗经验倍率，再进入原版护甲等减伤流程。",
			"默认满钻石甲为1.8倍，2000战斗经验后为1.35倍。龙、凋灵、监守者不受这两项倍率修正；PvP不受影响。"),
		page("输出软上限",
			"攻击敌对生物时，超过其最大生命值 ${percent(config.playerAttackCapFraction)}% 的伤害部分保留 ${percent(config.playerAttackOverflowMultiplier)}%，折算后向下取整。",
			"上限在护甲减伤前计算。触发时显示特殊粒子，友好生物和PvP不受影响。"),
		page("当前生命连击",
			"当前生命累计战斗经验。普通敌对生物按最大生命值计入，每点生命值对应1点经验，单次最多100点；龙、凋灵和监守者每次固定获得1000点。",
			"经验最多累计 ${config.killDamageExperienceCap} 点，每点降低 ${percent(config.killDamageReductionPerExperience)}% 输入伤害，倍率不低于 ${number(config.killDamageMinimumMultiplier)}。仅本人或本人投射物击杀计入。"),
		page("连击提示",
			"每 ${config.killDamageExperienceMilestone} 点战斗经验播放粒子，并在物品栏上方显示经验和修正，不刷聊天框。",
			"经验达到 ${config.killDamageExperienceCap} 点后停止累计，不再产生新的节点提示。提示仅指战斗经验倍率，不包含护甲倍率，也不适用于龙、凋灵和监守者的攻击。"),
		page("连击清空",
			"死亡时显示战斗经验总结并清零；进入创造、旁观或离开超困难时也清零。",
			"重登和正常换维度保留。不死图腾救回不算死亡。清零后战斗经验倍率回到1.0，护甲倍率仍单独计算。"),
		page("吸血条件",
			"吸血适用于剑和斧，正常最高III级。仅玩家满蓄力直接近战命中敌对生物生效。",
			"冷却 ${number(config.lifestealCooldownTicks / 20.0f)} 秒；同一游戏刻多次命中只取最大治疗量。远程和宠物攻击不能吸血。"),
		page("吸血数值",
			"本服I/II/III级分别回复有效伤害的 ${percent(lifestealRatio(config, 1))}% / ${percent(lifestealRatio(config, 2))}% / ${percent(lifestealRatio(config, 3))}%。",
			"按护甲等减免后的生命和吸收值损失计算，保留小数，无固定最低治疗。攻击仍受输出软上限限制。"),
		page("沙漠神殿掉落",
			"额外奖池每箱抽取一次。I/II/III级吸血书与空奖权重依次为 ${config.desertPyramidLifestealOneWeight}/${config.desertPyramidLifestealTwoWeight}/${config.desertPyramidLifestealThreeWeight}/${config.desertPyramidNoBookWeight}。",
			"概率为各项权重除以总权重；默认分别为5%、4%、3%和88%。"),
		page("袭击阵容",
			"本服共 ${config.raidGroups} 波。最终波基础阵容：掠夺者 ${config.eighthWavePillagers}、卫道士 ${config.eighthWaveVindicators}、女巫 ${config.eighthWaveWitches}、唤魔者 ${config.eighthWaveEvokers}、劫掠兽 ${config.eighthWaveRavagers}。",
			"默认骑乘单位混合出场。女巫会治疗受伤袭击单位，优先处理支援。"),
		page("袭击奖励资格",
			"最终波存活单位64格内累计停留100游戏刻（5秒）获得资格，不要求最后一击。创造和旁观不计。",
			"胜利后奖励所有合格参与者，而非只按村庄英雄名单。参与记录目前仅在本次运行中保留。"),
		page("袭击奖励领取",
			"每人获得吸血 ${config.eighthWaveLifestealBookLevel} 级书，另以 ${percent(config.eighthWaveEnchantedGoldenAppleChance)}% 概率得附魔金苹果，否则得不死图腾。",
			"已结算的离线奖励存入世界，下次在超困难上线领取；背包满则掉落脚下。"),
		page("装备与仇恨",
			"护甲评分：皮1、锁链2、铁3、金2、钻石4、下界合金5；任一手持盾加3，双持不重复，背包不计。",
			"这与护甲伤害倍率不同。每点有效伤害增加 ${number(config.threatPerDamage)} 仇恨，上限 ${number(config.threatMaximum)}，每秒衰减 ${number(config.threatDecayPerSecond)}。"),
		page("合作与目标",
			"敌人综合仇恨、装备和距离选目标，锁定 ${number(config.targetLockTicks / 20.0f)} 秒，失效时提前重选。新目标需可见，近战检查可达性。",
			"主动输出可保护队友。仇恨在30秒未攻击、死亡、离线或换维度后清理。"),
		page("近战战术",
			"僵尸前后包抄，未持武器者给持武器者让位；蜘蛛命中产生临时蛛网，蠹虫和末影螨侧翼骚扰。",
			"绕盾失败、3秒无进展或累计6秒超时后，正面施压5秒；盾牌不是绝对安全。"),
		page("远程与环境",
			"骷髅保持射距；末影人尝试绕到视线后方，女巫保持后排支援。",
			"敌人不因互伤转移仇恨。护甲生成概率为原版 ${number(config.mobArmorSpawnMultiplier)} 倍。"),
		page("亡灵避阳",
			"仅白天会燃烧且不免疫火焰的亡灵参与避阳，幻翼除外；不怕阳光的亡灵不主动寻找水源或阴凉处。",
			"附近没有可攻击玩家时，着火优先寻找可达水源；白天到达安全处后停留，夜晚解除。近距离战斗优先于避阳和寻水。"),
		page("附近玩家与射击",
			"敌对亡灵周围横向16×16格内（每侧8格、高差不超过8格）出现可攻击玩家后恢复出击，创造和旁观不计；溺尸也可追击附近岸上的玩家。",
			"持弓骷髅主动避开玩家正前方，从可达且有射界的侧方射击，不要求举盾；狭窄地形或绕行超时恢复正面射击。"),
		page("怪物生成",
			"超困难下蜘蛛骑士判定3%；幼体僵尸寻找已有鸡、生成新鸡的条件分支各为15%，最终骑乘率受年龄和附近鸡影响。",
			"僵尸随机幼体判定由5%提高到10%，沿用该判定的变种同样生效。不重新随机已指定年龄的个体，沿用原版乘骑条件。"),
		page("大型骑士",
			"超困难下，尸壳生成骆驼尸壳坐骑的判定为15%，溺尸生成僵尸鹦鹉螺坐骑的判定为75%。",
			"僵尸马自然生成时固定配备僵尸骑手，概率已达100%。骑士沿用原版生成环境与乘骑条件。"),
		page("防晒装备",
			"佩戴头盔或其他原版认可的防晒头饰时，不启用本模组的寻水、避阳与原地停留行为。装备后可恢复正常行动。",
			"实时检查原版防晒装备槽：头饰损坏或摘下后恢复避阳，不必重新生成生物。特殊亡灵使用其原版防晒槽位，例如僵尸马的身体装备槽。"),
	)

	/** 统一书页格式，并显式使用 Component 以满足原版书籍的数据类型。 */
	private fun page(title: String, vararg paragraphs: String): Filterable<Component> =
		Filterable.passThrough(Component.literal((listOf(title) + paragraphs).joinToString("\n\n")))

	private fun lifestealRatio(config: UltraHardConfig, level: Int): Float =
		if (level >= config.lifestealMaximumRatioLevel) config.lifestealMaximumRatio
		else config.lifestealBaseRatio * (level + 1)

	/** 去掉无意义的小数尾零，避免默认配置在书中显示浮点误差。 */
	private fun number(value: Number): String = BigDecimal(value.toString()).stripTrailingZeros().toPlainString()
	private fun percent(value: Float): String = BigDecimal(value.toString()).multiply(BigDecimal(100)).stripTrailingZeros().toPlainString()
}
