package com.nanagokyuu.ultrahard.equipment

import com.nanagokyuu.ultrahard.UltraHardPlayerState
import com.nanagokyuu.ultrahard.config.UltraHardConfigs
import java.util.WeakHashMap
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.Items

/** 只读取实际护甲槽和手持物品；背包内的装备不参与伤害倍率或仇恨评分。 */
object UltraHardEquipment {
	private val armorSlots = listOf(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)
	private val ironArmor = setOf(Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS)
	private val diamondArmor = setOf(Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS)
	private val netheriteArmor = setOf(Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE, Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS)
	private data class CachedScore(val tick: Long, val score: Int)
	private val scoreCache = WeakHashMap<Player, CachedScore>()
	private val armorScores: Map<Item, Int> = buildMap {
		listOf(Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS).forEach { put(it, 1) }
		listOf(Items.CHAINMAIL_HELMET, Items.CHAINMAIL_CHESTPLATE, Items.CHAINMAIL_LEGGINGS, Items.CHAINMAIL_BOOTS).forEach { put(it, 2) }
		listOf(Items.GOLDEN_HELMET, Items.GOLDEN_CHESTPLATE, Items.GOLDEN_LEGGINGS, Items.GOLDEN_BOOTS).forEach { put(it, 2) }
		ironArmor.forEach { put(it, 3) }
		diamondArmor.forEach { put(it, 4) }
		netheriteArmor.forEach { put(it, 5) }
	}

	/** 将当前护甲倍率与本次生命的击杀倍率相乘；护甲额外倍率默认最多为 80%。 */
	fun enemyDamageMultiplier(player: Player): Float {
		val config = UltraHardConfigs.values
		// 只使用护甲值，不使用护甲韧性，因此满钻石甲和满下界合金甲均为 1.8 倍。
		val armor = player.getArmorValue().toFloat().coerceAtMost(20.0f)
		val armorBonus = (armor * config.armorDamageScaling)
			.coerceIn(0.0f, config.maximumArmorDamageBonus)
		val killMultiplier = killDamageMultiplier(player)
		return (1.0f + armorBonus) * killMultiplier
	}

	/** 根据当前生命的击杀数降低敌人伤害，最多降低 25%。 */
	fun killDamageMultiplier(player: Player): Float {
		val state = player as? UltraHardPlayerState ?: return 1.0f
		val config = UltraHardConfigs.values
		val kills = state.ultrahardGetLifeKills().coerceAtMost(config.killDamageReductionMaximumKills)
		val reduction = kills * config.killDamageReductionPerKill
		return (1.0f - reduction).coerceAtLeast(config.killDamageMinimumMultiplier)
	}

	/** 盾牌只在主手或副手持有时加三分，双持不重复计分，也不要求正在举盾。 */
	fun defenseScore(player: Player): Int {
		// 同一玩家的装备分缓存五刻，供附近怪物复用；弱引用避免保留离线玩家。
		val now = player.level().gameTime
		scoreCache[player]?.takeIf { now >= it.tick && now - it.tick < 5L }?.let { return it.score }
		val armorScore = armorSlots.sumOf { armorScores[player.getItemBySlot(it).item] ?: 0 }
		val shieldScore = if (player.mainHandItem.`is`(Items.SHIELD) || player.offhandItem.`is`(Items.SHIELD)) 3 else 0
		return (armorScore + shieldScore).also { scoreCache[player] = CachedScore(now, it) }
	}
}
