package com.nanagokyuu.ultrahard.recovery

import com.nanagokyuu.ultrahard.UltraHardMod
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents
import net.fabricmc.fabric.api.loot.v3.LootTableEvents
import net.minecraft.core.Registry
import net.minecraft.core.component.DataComponents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.resources.Identifier
import net.minecraft.resources.ResourceKey
import net.minecraft.world.food.FoodProperties
import net.minecraft.world.item.Item
import net.minecraft.world.item.component.UseEffects
import net.minecraft.world.level.storage.loot.LootPool
import net.minecraft.world.level.storage.loot.entries.LootItem
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction
import net.minecraft.world.level.storage.loot.predicates.LootItemRandomChanceCondition
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator

/** 注册补给、创造栏入口和追加战利品池，不覆盖原版宝箱内容。 */
object UltraHardRecoveryItems {
	val SIMPLE_BANDAGE: Item = register("simple_bandage", false)
	val NOURISHING_SOUP: Item = register("nourishing_soup", true)

	private fun register(name: String, soup: Boolean): Item {
		val key = ResourceKey.create(Registries.ITEM, UltraHardMod.id(name))
		val properties = Item.Properties().setId(key).stacksTo(if (soup) 1 else 16)
			.component(DataComponents.USE_EFFECTS, UseEffects(false, true, if (soup) 0.2f else 0.35f))
		if (soup) {
			// 仅提供食物属性，不挂原版自动消耗组件，避免绕过治疗互斥和完成检查。
			properties.component(DataComponents.FOOD, FoodProperties.Builder()
				.nutrition(8).saturationModifier(0.6f).alwaysEdible().build())
		}
		return Registry.register(BuiltInRegistries.ITEM, key, RecoveryItem(properties, soup))
	}

	fun register() {
		val tab = ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier.withDefaultNamespace("food_and_drinks"))
		CreativeModeTabEvents.modifyOutputEvent(tab).register { output ->
			output.accept(SIMPLE_BANDAGE)
			output.accept(NOURISHING_SOUP)
		}
		LootTableEvents.MODIFY.register { key, table, source, _ ->
			val id = key.identifier()
			if (!source.isBuiltin || id.namespace != "minecraft") return@register
			val path = id.path
			if (!path.startsWith("chests/village/") && path !in supplyChests) return@register
			val bonusChest = path == "chests/spawn_bonus_chest"
			// 常见探索箱75%追加2～4份；开局奖励箱保证提供3份，已生成的战利品不会被追溯改写。
			val count = if (bonusChest) ConstantValue.exactly(3.0f) else UniformGenerator.between(2.0f, 4.0f)
			table.withPool(LootPool.lootPool().setRolls(ConstantValue.exactly(1.0f))
				.`when`(LootItemRandomChanceCondition.randomChance(if (bonusChest) 1.0f else 0.75f))
				.add(LootItem.lootTableItem(SIMPLE_BANDAGE).apply(SetItemCountFunction.setCount(count))))
		}
	}

	private val supplyChests = setOf(
		"chests/spawn_bonus_chest", "chests/simple_dungeon", "chests/abandoned_mineshaft",
		"chests/desert_pyramid", "chests/jungle_temple", "chests/igloo_chest", "chests/pillager_outpost",
		"chests/shipwreck_supply", "chests/shipwreck_map", "chests/shipwreck_treasure",
		"chests/underwater_ruin_small", "chests/underwater_ruin_big", "chests/buried_treasure",
		"chests/ruined_portal", "chests/stronghold_corridor", "chests/stronghold_crossing", "chests/stronghold_library",
	)
}
