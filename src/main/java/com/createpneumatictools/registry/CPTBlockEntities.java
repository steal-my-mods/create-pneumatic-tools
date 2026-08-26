package com.createpneumatictools.registry;

import java.util.function.Supplier;

import com.createpneumatictools.CreatePneumaticTools;
import com.createpneumatictools.source.PneumaticSourceBlockEntity;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredRegister;

public class CPTBlockEntities {

	public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
		DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, CreatePneumaticTools.ID);

	public static final Supplier<BlockEntityType<PneumaticSourceBlockEntity>> PNEUMATIC_SOURCE =
		BLOCK_ENTITIES.register("pneumatic_source",
			() -> BlockEntityType.Builder
				.of((pos, state) -> new PneumaticSourceBlockEntity(CPTBlockEntities.PNEUMATIC_SOURCE.get(),
					pos, state), CPTBlocks.PNEUMATIC_SOURCE.get())
				.build(null));
}
