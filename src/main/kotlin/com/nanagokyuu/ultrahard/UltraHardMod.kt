package com.nanagokyuu.ultrahard

import net.fabricmc.fabric.api.loot.v3.LootTableEvents
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents
import net.fabricmc.api.ModInitializer
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

object UltraHardMod : ModInitializer {
	const val MOD_ID = "ultrahard"
	val LOGGER = LoggerFactory.getLogger(MOD_ID)

	override fun onInitialize() {
		UltraHardConfigs.load()
		val ultra = UltraHardDifficulties.ULTRAHARD
		UltraHardEvents.register()
		ServerPlayerEvents.JOIN.register(UltraHardRulesBook::giveTo)
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
