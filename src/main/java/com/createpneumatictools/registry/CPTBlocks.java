package com.createpneumatictools.registry;

import com.createpneumatictools.CPTConfig;
import com.createpneumatictools.CreatePneumaticTools;
import com.createpneumatictools.source.PneumaticSourceBlock;
import com.simibubi.create.api.stress.BlockStressValues;

import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public class CPTBlocks {

	public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(CreatePneumaticTools.ID);

	/**
	 * The Pneumatic Wrench's temporary generator. Not craftable, not obtainable, and it has no
	 * {@code BlockItem} — the wrench is the only thing that ever puts one down.
	 *
	 * <p>{@code noLootTable} matters: without it the game logs a missing-loot-table warning for every
	 * block that has none, and a warning per block placed is a warning per tick of holding the button.
	 */
	public static final DeferredBlock<PneumaticSourceBlock> PNEUMATIC_SOURCE =
		BLOCKS.register("pneumatic_source", () -> new PneumaticSourceBlock(BlockBehaviour.Properties.of()
			.mapColor(MapColor.NONE)
			.noCollission()
			.noOcclusion()
			.noLootTable()
			// Emphatically not .air(): that makes BlockState.isAir() true, and a block that reports
			// itself as air is skipped by half the world -- including, as it turns out, the rotation
			// propagator, which leaves the source sitting in the world connected to nothing.
			.strength(-1.0F, 3600000.0F)
			.sound(SoundType.EMPTY)));

	/**
	 * What Create quotes for the source, and what it actually supplies.
	 *
	 * <p>Capacity only — a generator has no impact. The figure deliberately beats Create's own Hand
	 * Crank rather than matching it: a crank is a handle you turn with your arms, and a pneumatic
	 * wrench is the tool that exists because arms are not enough. At the defaults it is twice a
	 * crank's speed and four times its output, which is the difference between only just turning one
	 * Mechanical Press and running one properly. What it does not have is a crank's permanence — it
	 * lasts as long as you stand there holding the button, and about three and a half minutes of
	 * backtank.
	 */
	public static void registerStressValues(FMLCommonSetupEvent event) {
		event.enqueueWork(() -> {
			BlockStressValues.CAPACITIES.register(PNEUMATIC_SOURCE.get(), CPTConfig::wrenchCapacity);
			// The quoted speed is a whole number and the config is a float, so this is the tooltip's
			// figure rather than the source's: what the source actually turns at is
			// PneumaticSourceBlockEntity.getGeneratedSpeed, read live.
			BlockStressValues.setGeneratorSpeed(Math.round(CPTConfig.wrenchRpm()))
				.accept(PNEUMATIC_SOURCE.get());
		});
	}
}
