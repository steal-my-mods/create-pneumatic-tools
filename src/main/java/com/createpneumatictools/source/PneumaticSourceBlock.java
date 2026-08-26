package com.createpneumatictools.source;

import com.createpneumatictools.registry.CPTBlockEntities;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.foundation.block.IBE;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The block the Pneumatic Wrench puts down: an invisible, temporary source of rotation.
 *
 * <p>Create's rotation is a graph of block entities, so a source of it has to <em>be somewhere</em> —
 * there is no way for an item in a hand to join a kinetic network, because a network member is found
 * by its neighbours asking the world what is at a position. So the wrench does the only thing that
 * can work: while you hold the button it keeps a real generator block in the world, against the face
 * you aimed at, and lets it disappear when you stop.
 *
 * <p>Everything about this block is arranged so that a copy left behind cannot become a problem:
 *
 * <ul>
 * <li>It is <b>invisible and has no shape at all</b>, so it cannot be walked into, stood on, targeted,
 *     broken, or picked. There is no item form and it is in no creative tab.
 * <li>It <b>drops nothing</b> and has no loot table.
 * <li>It is <b>tagged non-movable</b>, so a piston or a contraption cannot carry one away and strand
 *     it somewhere its owner will never return to.
 * <li>And it holds a <b>lease</b> rather than a flag — see {@link PneumaticSourceBlockEntity}. Nothing
 *     has to remember to remove it; something has to keep remembering to keep it.
 * </ul>
 */
public class PneumaticSourceBlock extends DirectionalKineticBlock
	implements IBE<PneumaticSourceBlockEntity> {

	public PneumaticSourceBlock(Properties properties) {
		super(properties);
	}

	@Override
	public RenderShape getRenderShape(BlockState state) {
		return RenderShape.INVISIBLE;
	}

	@Override
	public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos,
		CollisionContext context) {
		return Shapes.empty();
	}

	@Override
	public VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos,
		CollisionContext context) {
		return Shapes.empty();
	}

	/** No outline either: a block you cannot see should not glow when you look through it. */
	@Override
	public VoxelShape getVisualShape(BlockState state, BlockGetter world, BlockPos pos,
		CollisionContext context) {
		return Shapes.empty();
	}

	@Override
	public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
		return face == state.getValue(FACING);
	}

	@Override
	public Axis getRotationAxis(BlockState state) {
		return state.getValue(FACING)
			.getAxis();
	}

	/** Its stress is the tank's business, not something to quote on a shaft's tooltip. */
	@Override
	public boolean hideStressImpact() {
		return true;
	}

	@Override
	protected boolean isPathfindable(BlockState state, PathComputationType type) {
		return true;
	}

	@Override
	public PushReaction getPistonPushReaction(BlockState state) {
		return PushReaction.DESTROY;
	}

	@Override
	public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
		return ItemStack.EMPTY;
	}

	@Override
	public Class<PneumaticSourceBlockEntity> getBlockEntityClass() {
		return PneumaticSourceBlockEntity.class;
	}

	@Override
	public BlockEntityType<? extends PneumaticSourceBlockEntity> getBlockEntityType() {
		return CPTBlockEntities.PNEUMATIC_SOURCE.get();
	}
}
