package com.nanagokyuu.ultrahard.mixin;

import com.nanagokyuu.ultrahard.UltraHardConfigs;
import com.nanagokyuu.ultrahard.UltraHardDifficulties;
import com.nanagokyuu.ultrahard.UltraHardMod;
import net.minecraft.world.Difficulty;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Raid.class)
public abstract class RaidMixin {
	@Shadow
	@Final
	private int numGroups;
	@Unique
	private boolean ultrahard$eighthWaveRewardsGranted;

	/** 超困难为 8 波；直接返回可以避免新增枚举值导致序号分支匹配失败。 */
	@Inject(method = "getNumGroups", at = @At("HEAD"), cancellable = true)
	private void ultrahard$hardGroups(Difficulty difficulty, CallbackInfoReturnable<Integer> cir) {
		if (UltraHardDifficulties.isUltraHard(difficulty)) {
			cir.setReturnValue(UltraHardConfigs.getValues().getRaidGroups());
		}
	}

	@Inject(method = "getDefaultNumSpawns", at = @At("HEAD"), cancellable = true)
	private void ultrahard$customEighthWave(
			@Coerce Object raiderType,
			int wav,
			boolean isBonusWave,
			CallbackInfoReturnable<Integer> cir
	) {
		if (isBonusWave) {
			cir.setReturnValue(0);
			return;
		}
		int finalWave = isBonusWave ? this.numGroups : wav;
		if (this.numGroups < UltraHardConfigs.getValues().getRaidGroups()
				|| finalWave != UltraHardConfigs.getValues().getRaidGroups()) {
			return;
		}

		var type = ((RaidRaiderTypeAccessor) raiderType).ultrahard$getEntityType();
		var config = UltraHardConfigs.getValues();
		if (type == EntityTypes.PILLAGER) cir.setReturnValue(config.getEighthWavePillagers());
		else if (type == EntityTypes.VINDICATOR) cir.setReturnValue(config.getEighthWaveVindicators());
		else if (type == EntityTypes.WITCH) cir.setReturnValue(config.getEighthWaveWitches());
		else if (type == EntityTypes.EVOKER) cir.setReturnValue(config.getEighthWaveEvokers());
		else if (type == EntityTypes.RAVAGER) cir.setReturnValue(config.getEighthWaveRavagers());
		else cir.setReturnValue(0);
	}

	@Inject(method = "getPotentialBonusSpawns", at = @At("HEAD"), cancellable = true)
	private void ultrahard$disableFinalWaveBonusSpawns(
			@Coerce Object raiderType,
			net.minecraft.util.RandomSource random,
			int wav,
			net.minecraft.world.DifficultyInstance difficulty,
			boolean isBonusWave,
			CallbackInfoReturnable<Integer> cir
	) {
		if (this.numGroups >= UltraHardConfigs.getValues().getRaidGroups()
				&& wav >= UltraHardConfigs.getValues().getRaidGroups()) {
			cir.setReturnValue(0);
		}
	}

	@Inject(method = "spawnGroup", at = @At("TAIL"))
	private void ultrahard$addPillagerRider(ServerLevel level, BlockPos pos, CallbackInfo ci) {
		if (!UltraHardDifficulties.isUltraHard(level)
				|| this.numGroups != UltraHardConfigs.getValues().getRaidGroups()) {
			return;
		}

		Raid raid = (Raid) (Object) this;
		Ravager mount = raid.getAllRaiders().stream()
				.filter(raider -> raider instanceof Ravager && raider.getWave() == this.numGroups)
				.map(raider -> (Ravager) raider)
				.filter(ravager -> ravager.getPassengers().stream()
						.anyMatch(passenger -> passenger.getType() == EntityTypes.VINDICATOR))
				.findFirst()
				.orElse(null);
		if (mount == null) return;
		mount.getPassengers().stream()
				.filter(passenger -> passenger.getType() == EntityTypes.VINDICATOR)
				.findFirst()
				.ifPresent(passenger -> passenger.discard());

		Raider rider = EntityTypes.PILLAGER.create(level, EntitySpawnReason.EVENT);
		if (rider == null) return;
		raid.joinRaid(level, this.numGroups, rider, pos, false);
		rider.snapTo(pos, 0.0F, 0.0F);
		rider.startRiding(mount, false, false);
		raid.updateBossbar();
	}

	@Inject(method = "tick", at = @At("TAIL"))
	private void ultrahard$rewardEighthWaveVictory(ServerLevel level, CallbackInfo ci) {
		Raid raid = (Raid) (Object) this;
		if (ultrahard$eighthWaveRewardsGranted
				|| !UltraHardDifficulties.isUltraHard(level)
				|| this.numGroups < UltraHardConfigs.getValues().getRaidGroups()
				|| !raid.isVictory()) {
			return;
		}

		ultrahard$eighthWaveRewardsGranted = true;
		for (java.util.UUID uuid : ((RaidAccessor) raid).ultrahard$getHeroesOfTheVillage()) {
			ServerPlayer player = level.getServer().getPlayerList().getPlayer(uuid);
			if (player == null) continue;
			giveReward(player, UltraHardMod.createLifestealBook(
					level,
					UltraHardConfigs.getValues().getEighthWaveLifestealBookLevel()
			));
			ItemStack bonus = level.getRandom().nextFloat()
					< UltraHardConfigs.getValues().getEighthWaveEnchantedGoldenAppleChance()
					? new ItemStack(Items.ENCHANTED_GOLDEN_APPLE)
					: new ItemStack(Items.TOTEM_OF_UNDYING);
			giveReward(player, bonus);
		}
	}

	@Unique
	private static void giveReward(ServerPlayer player, ItemStack reward) {
		if (!player.getInventory().add(reward)) {
			player.drop(reward, false);
		}
	}
}
