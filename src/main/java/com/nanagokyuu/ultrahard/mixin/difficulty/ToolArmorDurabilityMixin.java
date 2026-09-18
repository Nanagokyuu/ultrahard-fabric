package com.nanagokyuu.ultrahard.mixin.difficulty;

import com.llamalad7.mixinextras.sugar.Local;
import com.nanagokyuu.ultrahard.UltraHardConfigs;
import com.nanagokyuu.ultrahard.UltraHardDifficulties;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.BrushItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.FlintAndSteelItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.TridentItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * 在物品栈耐久变化入口按物品类型扩大消耗，不修改最大耐久或维修配方；金制装备保留原始消耗。
 */
@Mixin(ItemStack.class)
public abstract class ToolArmorDurabilityMixin {
	@ModifyVariable(
			method = "processDurabilityChange",
			at = @At("HEAD"),
			argsOnly = true
	)
	private int ultrahard$doubleToolAndArmorDamage(
			int amount,
			@Local(argsOnly = true) ServerLevel level
	) {
		ItemStack stack = (ItemStack) (Object) this;
		return UltraHardDifficulties.isUltraHard(level) && isAffected(stack)
				? amount * UltraHardConfigs.getValues().getDurabilityDamageMultiplier()
				: amount;
	}

	private static boolean isAffected(ItemStack stack) {
		if (isGold(stack)) {
			return false;
		}

		return stack.is(ItemTags.HEAD_ARMOR)
				|| stack.is(ItemTags.CHEST_ARMOR)
				|| stack.is(ItemTags.LEG_ARMOR)
				|| stack.is(ItemTags.FOOT_ARMOR)
				|| stack.has(DataComponents.TOOL)
				|| stack.has(DataComponents.BLOCKS_ATTACKS)
				|| stack.is(ItemTags.SWORDS)
				|| stack.is(ItemTags.AXES)
				|| stack.is(ItemTags.HOES)
				|| stack.is(ItemTags.PICKAXES)
				|| stack.is(ItemTags.SHOVELS)
				|| isDurableTool(stack.getItem());
	}

	private static boolean isGold(ItemStack stack) {
		return stack.is(ItemTags.GOLD_TOOL_MATERIALS)
				|| stack.is(Items.GOLDEN_HELMET)
				|| stack.is(Items.GOLDEN_CHESTPLATE)
				|| stack.is(Items.GOLDEN_LEGGINGS)
				|| stack.is(Items.GOLDEN_BOOTS);
	}

	private static boolean isDurableTool(Item item) {
		return item instanceof BowItem
				|| item instanceof CrossbowItem
				|| item instanceof TridentItem
				|| item instanceof FishingRodItem
				|| item instanceof ShearsItem
				|| item instanceof FlintAndSteelItem
				|| item instanceof BrushItem
				|| item instanceof MaceItem
				|| item instanceof ShieldItem;
	}
}
