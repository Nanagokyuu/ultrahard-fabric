package com.nanagokyuu.ultrahard.mixin;

import net.minecraft.world.Difficulty;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * Adds {@code ULTRAHARD} to {@link Difficulty} via Fabric enum extension.
 * Constructor args: id=4, key="ultrahard" (matches StringRepresentable /difficulty).
 */
@Mixin(Difficulty.class)
public enum DifficultyMixin {
	ULTRAHARD(4, "ultrahard");

	@Shadow
	DifficultyMixin(int id, String key) {
	}
}
