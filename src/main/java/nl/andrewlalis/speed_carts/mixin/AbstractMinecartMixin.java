package nl.andrewlalis.speed_carts.mixin;

import com.mojang.datafixers.util.Pair;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.SignBlockEntity;
import net.minecraft.block.enums.RailShape;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Mixin which overrides the default minecart behavior so that we can define a
 * changeable speed, and check for updates.
 */
@Mixin(AbstractMinecartEntity.class)
public abstract class AbstractMinecartMixin extends Entity {
	private static final double DEFAULT_SPEED = 8.0;
	private static final double MIN_SPEED = 1.0;
	private static final double MAX_SPEED = 128.0;
	private static final long SPEED_UPDATE_COOLDOWN = 20 * 3;

	/**
	 * The currently-set maximum speed, in blocks per second.
	 */
	private double maxSpeedBps = DEFAULT_SPEED;

	/**
	 * The last time (in game ticks) that the speed was updated.
	 */
	private long lastSpeedUpdate = 0;

	/**
	 * The position of the block which was last responsible for updating the
	 * cart's speed.
	 */
	private BlockPos lastUpdatedFrom = null;

	@Shadow
	public abstract Vec3d snapPositionToRail(double x, double y, double z);

	@Shadow
	private static Pair<Vec3i, Vec3i> getAdjacentRailPositionsByShape(RailShape shape) {
		return null;
	}

	@Shadow
	protected abstract double getMaxOffRailSpeed();

	@Inject(at = @At("HEAD"), method = "getMaxOffRailSpeed", cancellable = true)
	public void getMaxOffRailSpeedOverwrite(CallbackInfoReturnable<Double> cir) {
		cir.setReturnValue(this.maxSpeedBps / 20.0);
	}

	@Shadow
	protected abstract void applySlowdown();

	@Shadow
	protected abstract boolean willHitBlockAt(BlockPos pos);

	public AbstractMinecartMixin(EntityType<?> type, World world) {
		super(type, world);
	}

	@Shadow
	protected abstract void moveOnRail(BlockPos pos, BlockState state);

	@Shadow public abstract Direction getMovementDirection();

	@Inject(at = @At("HEAD"), method = "moveOnRail", cancellable = true)
	public void moveOnRailOverwrite(BlockPos pos, BlockState state, CallbackInfo ci) {
		this.updateForSpeedModifiers(pos);
	}

	/**
	 * Checks for any speed modifiers (signs, blocks, etc.) and attempts to
	 * apply their effects to the cart. It does this by iterating over a list of
	 * possible positions for blocks which are considered speed modifiers, and
	 * then if a valid speed modifier is found, the cart's speed is updated.
	 * @param pos The cart's current position.
	 */
	private void updateForSpeedModifiers(BlockPos pos) {
		// Quit if the cart is not moving, and set its speed to default.
		if (this.getVelocity().length() == 0) {
			this.maxSpeedBps = DEFAULT_SPEED;
			return;
		}

		for (var position : this.getPositionsToCheck(pos)) {
			if (
				this.world.getBlockEntity(position) instanceof SignBlockEntity sign &&
				(!sign.getPos().equals(this.lastUpdatedFrom) || this.world.getTime() > this.lastSpeedUpdate + SPEED_UPDATE_COOLDOWN)
			) {
				var state = this.world.getBlockState(position);
				var dir = (Direction) state.getEntries().get(Properties.HORIZONTAL_FACING);
				// Only allow free-standing signs or those facing the cart.
				if (dir == null || dir.equals(this.getMovementDirection().getOpposite())) {
					if (this.updateSpeedForSign(sign)) return;
				}
			}
		}
	}

	/**
	 * Attempts to update the cart's speed according to a sign.
	 * @param sign The sign that contains speed information.
	 * @return True if the cart's speed was updated, or false otherwise.
	 */
	private boolean updateSpeedForSign(SignBlockEntity sign) {
		Text text = sign.getTextOnRow(0, false);
		String s = text.asString();
		try {
			double speed = Double.parseDouble(s);
			if (speed >= MIN_SPEED && speed <= MAX_SPEED) {
				this.maxSpeedBps = speed;
				this.lastSpeedUpdate = this.world.getTime();
				this.lastUpdatedFrom = sign.getPos();
				if (this.hasPlayerRider()) {
					PlayerEntity player = (PlayerEntity) this.getFirstPassenger();
					if (player != null) {
						player.playSound(new SoundEvent(new Identifier("block.note_block.bell")), SoundCategory.PLAYERS, 1.0f, 1.0f);
					}
				}
				return true;
			} else {
				sign.setTextOnRow(0, Text.of("Invalid speed!"));
				sign.setTextOnRow(1, Text.of("Min: " + MIN_SPEED));
				sign.setTextOnRow(2, Text.of("Max: " + MAX_SPEED));
				sign.setGlowingText(true);
				sign.setTextColor(DyeColor.RED);
			}
		} catch (NumberFormatException e) {
			// Do nothing if no value could be parsed.
		}
		return false;
	}

	/**
	 * Gathers a list of all block positions to check for signs that may affect
	 * the cart's speed.
	 * @param pos The cart's block position.
	 * @return A collection of positions to check.
	 */
	private Collection<BlockPos> getPositionsToCheck(BlockPos pos) {
		// Compute the number of blocks we have to check ahead.
		// This accounts for speeds greater than 1 block per tick.
		int blockRange = Math.max(1, (int) Math.ceil(this.maxSpeedBps / 20));
		List<BlockPos> positionsToCheck = new ArrayList<>(6 * blockRange);
		for (int i = 0; i < blockRange; i++) {
			positionsToCheck.add(pos.north());
			positionsToCheck.add(pos.south());
			positionsToCheck.add(pos.east());
			positionsToCheck.add(pos.west());
			positionsToCheck.add(pos.up());
			positionsToCheck.add(pos.down());
			pos = pos.add(this.getMovementDirection().getVector());
		}
		return positionsToCheck;
	}
}
