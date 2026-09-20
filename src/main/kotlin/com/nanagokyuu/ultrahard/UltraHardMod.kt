package com.nanagokyuu.ultrahard

import com.nanagokyuu.ultrahard.config.UltraHardConfigs
import com.nanagokyuu.ultrahard.difficulty.UltraHardDifficulties
import com.nanagokyuu.ultrahard.enchantment.UltraHardEnchantments
import com.nanagokyuu.ultrahard.events.UltraHardEvents
import com.nanagokyuu.ultrahard.events.UltraHardKillStreak
import com.nanagokyuu.ultrahard.elite.EliteMob
import com.nanagokyuu.ultrahard.event.BloodMoon
import com.nanagokyuu.ultrahard.raid.UltraHardRaidRewards
import com.nanagokyuu.ultrahard.rules.UltraHardRulesBook
import com.nanagokyuu.ultrahard.network.CombatExperiencePayload
import com.nanagokyuu.ultrahard.recovery.UltraHardRecovery
import com.nanagokyuu.ultrahard.recovery.UltraHardRecoveryItems
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents
import net.fabricmc.fabric.api.loot.v3.LootTableEvents
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry
import net.minecraft.core.registries.Registries
import net.minecraft.resources.Identifier
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.storage.loot.BuiltInLootTables
import net.minecraft.world.level.storage.loot.LootPool
import net.minecraft.world.level.storage.loot.entries.EmptyLootItem
import net.minecraft.world.level.storage.loot.entries.LootItem
import net.minecraft.world.level.storage.loot.entries.LootPoolSingletonContainer
import net.minecraft.world.level.storage.loot.functions.SetEnchantmentsFunction
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue
import org.slf4j.LoggerFactory

/** 模组初始化入口：先加载数值配置，再注册规则事件、玩家状态迁移及战利品修改。 */
object UltraHardMod : ModInitializer {
	const val MOD_ID = "ultrahard"
	val LOGGER = LoggerFactory.getLogger(MOD_ID)

	override fun onInitialize() {
		PayloadTypeRegistry.clientboundPlay().register(CombatExperiencePayload.TYPE, CombatExperiencePayload.CODEC)
		UltraHardConfigs.load()
		val ultra = UltraHardDifficulties.ULTRAHARD
		UltraHardRecoveryItems.register()
		UltraHardRecovery.register()
		EliteMob.register()
		BloodMoon.register()
		UltraHardEvents.register()
		ServerPlayerEvents.JOIN.register {
			UltraHardKillStreak.syncCombatExperience(it)
			UltraHardRecovery.syncSoupCooldown(it)
			UltraHardRaidRewards.deliver(it)
			UltraHardRulesBook.giveTo(it)
		}
		ServerPlayerEvents.COPY_FROM.register { oldPlayer, newPlayer, _ ->
			UltraHardEvents.copyPlayerState(oldPlayer, newPlayer)
		}
		registerLootTableChanges()
		LOGGER.info(
			"Ultra Hard initialized as Difficulty.{} (id={}, key={})",
			ultra.name,
			ultra.id,
			ultra.serializedName,
		)
	}

	/**
	 * 只为内置沙漠神殿战利品表追加一次抽取，不覆盖原有战利品池。
	 * 三种等级与空奖共享权重；修改权重后概率应按总权重重新计算，并非固定百分比。
	 */
	private fun registerLootTableChanges() {
		LootTableEvents.MODIFY.register { key, table, source, lookup ->
			if (key != BuiltInLootTables.DESERT_PYRAMID || !source.isBuiltin) return@register

			val enchantment = lookup.lookupOrThrow(Registries.ENCHANTMENT)
				.getOrThrow(UltraHardEnchantments.LIFESTEAL)
			val pool = LootPool.lootPool().setRolls(ConstantValue.exactly(1.0f))
			val config = UltraHardConfigs.values
			pool.add(enchantedBook(enchantment, 1, config.desertPyramidLifestealOneWeight))
			pool.add(enchantedBook(enchantment, 2, config.desertPyramidLifestealTwoWeight))
			pool.add(enchantedBook(enchantment, 3, config.desertPyramidLifestealThreeWeight))
			pool.add(EmptyLootItem.emptyItem().setWeight(config.desertPyramidNoBookWeight))
			table.withPool(pool)
		}
	}

	/** 从当前世界注册表获取附魔，供袭击奖励创建指定等级的附魔书。 */
	@JvmStatic
	fun createLifestealBook(level: ServerLevel, levelValue: Int): ItemStack {
		val enchantment = level.registryAccess()
			.lookupOrThrow(Registries.ENCHANTMENT)
			.getOrThrow(UltraHardEnchantments.LIFESTEAL)
		return ItemStack(Items.ENCHANTED_BOOK).also { it.enchant(enchantment, levelValue) }
	}

	private fun enchantedBook(
		enchantment: net.minecraft.core.Holder<net.minecraft.world.item.enchantment.Enchantment>,
		level: Int,
		weight: Int,
	): LootPoolSingletonContainer.Builder<*> {
		val entry = LootItem.lootTableItem(Items.ENCHANTED_BOOK).setWeight(weight)
		entry.apply(
			SetEnchantmentsFunction.Builder()
				.withEnchantment(enchantment, ConstantValue.exactly(level.toFloat()))
		)
		return entry
	}

	fun id(path: String): Identifier = Identifier.fromNamespaceAndPath(MOD_ID, path)
}
