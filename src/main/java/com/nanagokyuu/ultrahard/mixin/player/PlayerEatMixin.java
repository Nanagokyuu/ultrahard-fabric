package com.nanagokyuu.ultrahard.mixin.player;

import com.nanagokyuu.ultrahard.UltraHardEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FoodProperties.class)
public abstract class PlayerEatMixin {
	@Inject(method = "onConsume", at = @At("TAIL"))
	private void ultrahard$recordEating(
			Level level,
			LivingEntity entity,
			ItemStack stack,
			Consumable consumable,
			CallbackInfo ci
	) {
		if (entity instanceof Player player) {
			UltraHardEvents.recordEating(player);
		}
	}
}
