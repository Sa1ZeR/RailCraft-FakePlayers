/*
 * Copyright (c) CovertJaguar, 2014 http://railcraft.info
 *
 * This code is the property of CovertJaguar
 * and may only be used with explicit written
 * permission unless otherwise specified on the
 * license page at http://railcraft.info/wiki/info:license.
 */
package mods.railcraft.common.carts;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import mods.railcraft.api.carts.CartTools;
import mods.railcraft.api.carts.ILinkableCart;
import mods.railcraft.api.carts.ILinkageManager;
import mods.railcraft.api.carts.bore.IBoreHead;
import mods.railcraft.api.carts.bore.IMineable;
import mods.railcraft.api.tracks.RailTools;
import mods.railcraft.common.blocks.tracks.EnumTrackMeta;
import mods.railcraft.common.blocks.tracks.TrackTools;
import mods.railcraft.common.carts.Train.TrainState;
import mods.railcraft.common.core.RailcraftConfig;
import mods.railcraft.common.gui.EnumGui;
import mods.railcraft.common.gui.GuiHandler;
import mods.railcraft.common.plugins.forge.FuelPlugin;
import mods.railcraft.common.plugins.forge.HarvestPlugin;
import mods.railcraft.common.util.collections.BlockKey;
import mods.railcraft.common.util.collections.BlockSet;
import mods.railcraft.common.util.inventory.InvTools;
import mods.railcraft.common.util.inventory.filters.StackFilter;
import mods.railcraft.common.util.inventory.wrappers.InventoryMapper;
import mods.railcraft.common.util.misc.BallastRegistry;
import mods.railcraft.common.util.misc.Game;
import mods.railcraft.common.util.misc.MiscTools;
import mods.railcraft.common.util.misc.RailcraftDamageSource;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityMinecart;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.DamageSource;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

public class EntityTunnelBore extends CartContainerBase implements IInventory, ILinkableCart
{
	public static final float SPEED = 0.03F;
	public static final float LENGTH = 6.2f;
	public static final int MAX_FILL_DEPTH = 10;
	public static final int FAIL_DELAY = 200;
	public static final int STANDARD_DELAY = 5;
	public static final int LAYER_DELAY = 40;
	public static final int BALLAST_DELAY = 10;
	public static final int FUEL_CONSUMPTION = 12;
	public static final float HARDNESS_MULTIPLER = 8;
	public static final BlockSet mineableBlocks = new BlockSet();
	public static final Set<Block> replaceableBlocks = new HashSet<Block>();
	protected static final int WATCHER_ID_FUEL = 16;
	protected static final int WATCHER_ID_MOVING = 25;
	protected static final int WATCHER_ID_BORE_HEAD = 26;
	protected static final int WATCHER_ID_FACING = 5;
	private static final Block[] mineable = { Blocks.clay, Blocks.snow_layer, Blocks.cactus, Blocks.carrots, Blocks.cobblestone, Blocks.mossy_cobblestone, Blocks.cocoa, Blocks.wheat, Blocks.deadbush, Blocks.dirt, Blocks.fire, Blocks.glowstone, Blocks.grass, Blocks.gravel, Blocks.ice, Blocks.leaves, Blocks.melon_block, Blocks.melon_stem, Blocks.brown_mushroom, Blocks.brown_mushroom_block, Blocks.red_mushroom, Blocks.red_mushroom_block, Blocks.mycelium, Blocks.nether_wart, Blocks.netherrack, Blocks.obsidian, Blocks.coal_ore, Blocks.diamond_ore, Blocks.emerald_ore, Blocks.gold_ore, Blocks.iron_ore, Blocks.lapis_ore, Blocks.redstone_ore, Blocks.lit_redstone_ore, Blocks.red_flower, Blocks.yellow_flower, Blocks.potatoes, Blocks.pumpkin, Blocks.pumpkin_stem, Blocks.reeds, Blocks.sand, Blocks.sandstone, Blocks.sapling, Blocks.soul_sand, Blocks.snow, Blocks.stone, Blocks.tallgrass, Blocks.farmland, Blocks.torch, Blocks.vine, Blocks.waterlily, Blocks.web, Blocks.end_stone, Blocks.log, Blocks.log2, };
	private static final Block[] replaceable = { Blocks.torch, Blocks.tallgrass, Blocks.deadbush, Blocks.vine, Blocks.brown_mushroom, Blocks.red_mushroom, Blocks.yellow_flower, Blocks.red_flower, Blocks.double_plant };

	static
	{
		for (Block block : mineable)
			addMineableBlock(block);
		replaceableBlocks.addAll(Arrays.asList(replaceable));
	}

	protected final IInventory invFuel = new InventoryMapper(this, 1, 6);
	protected final IInventory invBallast = new InventoryMapper(this, 7, 9);
	protected final IInventory invRails = new InventoryMapper(this, 16, 9);
	//    protected static final int WATCHER_ID_BURN_TIME = 22;
	protected boolean degreeCalc = false;
	protected int delay = 0;
	protected boolean placeRail = false;
	protected boolean placeBallast = false;
	protected boolean boreLayer = false;
	protected int boreRotationAngle = 0;
	private boolean active;
	private int clock = MiscTools.getRand().nextInt();
	private int burnTime;
	private int fuel;
	private boolean hasInit;
	private EntityTunnelBorePart[] partArray;
	private EntityTunnelBorePart partHead1;
	private EntityTunnelBorePart partHead2;
	private EntityTunnelBorePart partBody;
	private EntityTunnelBorePart partTail1;
	private EntityTunnelBorePart partTail2;

	public EntityTunnelBore(World world, double i, double j, double k)
	{
		this(world, i, j, k, ForgeDirection.SOUTH);
	}

	public EntityTunnelBore(World world, double i, double j, double k, ForgeDirection f)
	{
		super(world);
		this.partArray = new EntityTunnelBorePart[] {
				// ------------------------------------- width, height, forwardOffset, sideOffset
				this.partHead1 = new EntityTunnelBorePart(this, "head1", 1.9F, 2.6F, 2F, -0.6F), this.partHead2 = new EntityTunnelBorePart(this, "head2", 1.9F, 2.6F, 2F, 0.6F), this.partBody = new EntityTunnelBorePart(this, "body", 2.0F, 1.9F, 0.6F), this.partTail1 = new EntityTunnelBorePart(this, "tail1", 1.6F, 1.4F, -1F), this.partTail2 = new EntityTunnelBorePart(this, "tail2", 1.6F, 1.4F, -2.2F), };
		this.hasInit = true;
		this.setPosition(i, j + this.yOffset, k);
		this.motionX = 0.0D;
		this.motionY = 0.0D;
		this.motionZ = 0.0D;
		this.prevPosX = i;
		this.prevPosY = j;
		this.prevPosZ = k;
		//        cargoItems = new ItemStack[25];
		this.setFacing(f);
		this.setSize(LENGTH, 4F);
	}

	public EntityTunnelBore(World world)
	{
		this(world, 0, 0, 0, ForgeDirection.SOUTH);
	}

	public static void addMineableBlock(Block block)
	{
		addMineableBlock(block, -1);
	}

	public static void addMineableBlock(Block block, int meta)
	{
		mineableBlocks.add(new BlockKey(block, meta));
	}

	public static boolean canHeadHarvestBlock(ItemStack head, Block block, int meta)
	{
		if (head == null)
			return false;

		if (head.getItem() instanceof IBoreHead)
		{
			IBoreHead boreHead = (IBoreHead) head.getItem();

			boolean mappingExists = false;

			int blockHarvestLevel = HarvestPlugin.getBlockHarvestLevel(block, meta, "pickaxe");
			if (blockHarvestLevel > -1)
			{
				if (boreHead.getHarvestLevel() >= blockHarvestLevel)
					return true;
				mappingExists = true;
			}

			blockHarvestLevel = HarvestPlugin.getBlockHarvestLevel(block, meta, "axe");
			if (blockHarvestLevel > -1)
			{
				if (boreHead.getHarvestLevel() >= blockHarvestLevel)
					return true;
				mappingExists = true;
			}

			blockHarvestLevel = HarvestPlugin.getBlockHarvestLevel(block, meta, "shovel");
			if (blockHarvestLevel > -1)
			{
				if (boreHead.getHarvestLevel() >= blockHarvestLevel)
					return true;
				mappingExists = true;
			}

			if (mappingExists)
				return false;
		}

		return true;
	}

	@Override
	public ICartType getCartType()
	{
		return EnumCart.BORE;
	}

	private boolean isMinableBlock(Block block, int meta)
	{
		if (RailcraftConfig.boreMinesAllBlocks())
			return true;
		return mineableBlocks.contains(block, meta);
	}

	@Override
	protected void entityInit()
	{
		super.entityInit();
		this.dataWatcher.addObject(WATCHER_ID_FUEL, (byte) 0);
		this.dataWatcher.addObject(WATCHER_ID_MOVING, (byte) 0);
		this.dataWatcher.addObjectByDataType(WATCHER_ID_BORE_HEAD, 5);
		this.dataWatcher.addObject(WATCHER_ID_FACING, (byte) 0);
		//        dataWatcher.addObject(WATCHER_ID_BURN_TIME, Integer.valueOf(0));
	}

	public boolean isMinecartPowered()
	{
		return this.dataWatcher.getWatchableObjectByte(WATCHER_ID_FUEL) != 0;
	}

	public void setMinecartPowered(boolean powered)
	{
		this.dataWatcher.updateObject(WATCHER_ID_FUEL, (byte) (powered ? 1 : 0));
	}

	@Override
	public boolean attackEntityFrom(DamageSource source, float damage)
	{
		if (!this.worldObj.isRemote && !this.isDead)
			if (this.isEntityInvulnerable())
				return false;
			else
			{
				this.setRollingDirection(-this.getRollingDirection());
				this.setRollingAmplitude(10);
				this.setBeenAttacked();
				this.setDamage(this.getDamage() + damage * 10);
				boolean flag = source.getEntity() instanceof EntityPlayer && ((EntityPlayer) source.getEntity()).capabilities.isCreativeMode;

				if (flag || this.getDamage() > 120)
				{
					if (this.riddenByEntity != null)
						this.riddenByEntity.mountEntity(this);

					if (flag && !this.hasCustomInventoryName())
						this.setDead();
					else
						this.killMinecart(source);
				}

				return true;
			}
		else
			return true;
	}

	@SuppressWarnings("incomplete-switch")
	private void setYaw()
	{
		float yaw = 0;
		switch (this.getFacing())
		{
			case NORTH:
				yaw = 90;
				break;
			case EAST:
				yaw = 0;
				break;
			case SOUTH:
				yaw = 270;
				break;
			case WEST:
				yaw = 180;
				break;
		}
		this.setRotation(yaw, this.rotationPitch);
	}

	@Override
	public int getSizeInventory()
	{
		return 25;
	}

	@Override
	public boolean canBePushed()
	{
		return false;
	}

	@Override
	public void setPosition(double i, double j, double k)
	{
		if (!this.hasInit)
		{
			super.setPosition(i, j, k);
			return;
		}

		this.posX = i;
		this.posY = j;
		this.posZ = k;
		double w = 2.7 / 2.0;
		double h = 2.7;
		double l = LENGTH / 2.0;
		double x1 = i;
		double x2 = i;
		double z1 = k;
		double z2 = k;
		if (this.getFacing() == ForgeDirection.WEST || this.getFacing() == ForgeDirection.EAST)
		{
			x1 -= l;
			x2 += l;
			z1 -= w;
			z2 += w;
		}
		else
		{
			x1 -= w;
			x2 += w;
			z1 -= l;
			z2 += l;
		}

		this.boundingBox.setBounds(x1, j - this.yOffset + this.ySize, z1, x2, j - this.yOffset + this.ySize + h, z2);
	}

	@Override
	public void onUpdate()
	{
		this.clock++;

		if (Game.isHost(this.worldObj))
		{
			if (this.clock % 64 == 0)
			{
				this.forceUpdateBoreHead();
				this.setMinecartPowered(false);
				this.setMoving(false);
			}

			this.stockBallast();
			this.stockTracks();
		}

		super.onUpdate();

		for (Entity part : this.partArray)
			part.onUpdate();

		if (Game.isHost(this.worldObj))
		{
			this.updateFuel();
			//            if(update % 64 == 0){
			//                System.out.println("bore tick");
			//            }

			if (this.hasFuel() && this.getDelay() == 0)
			{
				this.setActive(true);
				//            System.out.println("Yaw = " + MathHelper.floor_double(rotationYaw));

				int x;
				int y = MathHelper.floor_double(this.posY);
				int z;
				EnumTrackMeta dir = EnumTrackMeta.NORTH_SOUTH;
				if (this.getFacing() == ForgeDirection.WEST || this.getFacing() == ForgeDirection.EAST)
					dir = EnumTrackMeta.EAST_WEST;

				if (this.getDelay() == 0)
				{
					float offset = 1.5f;
					x = MathHelper.floor_double(this.getXAhead(this.posX, offset));
					z = MathHelper.floor_double(this.getZAhead(this.posZ, offset));

					if (this.placeBallast)
					{
						boolean placed = this.placeBallast(x, y - 1, z);
						if (placed)
							this.setDelay(STANDARD_DELAY);
						else
						{
							this.setDelay(FAIL_DELAY);
							this.setActive(false);
						}
						this.placeBallast = false;
					}
					else if (!this.worldObj.isSideSolid(x, y - 1, z, ForgeDirection.UP))
					{
						this.placeBallast = true;
						this.setDelay(BALLAST_DELAY);
					}
				}

				if (this.getDelay() == 0)
				{
					float offset = 0.8f;
					x = MathHelper.floor_double(this.getXAhead(this.posX, offset));
					z = MathHelper.floor_double(this.getZAhead(this.posZ, offset));

					if (this.placeRail)
					{
						boolean placed = this.placeTrack(x, y, z, dir);
						if (placed)
							this.setDelay(STANDARD_DELAY);
						else
						{
							this.setDelay(FAIL_DELAY);
							this.setActive(false);
						}
						this.placeRail = false;
					}
					else if (TrackTools.isRailBlockAt(this.worldObj, x, y, z))
					{
						if (!dir.isEqual(TrackTools.getTrackMeta(this.worldObj, this, x, y, z)))
						{
							this.worldObj.setBlockMetadataWithNotify(x, y, z, dir.ordinal(), 3);
							this.setDelay(STANDARD_DELAY);
						}
					}
					else if (this.worldObj.isAirBlock(x, y, z) || replaceableBlocks.contains(this.worldObj.getBlock(x, y, z)))
					{
						this.placeRail = true;
						this.setDelay(STANDARD_DELAY);
					}
					else
					{
						this.setDelay(FAIL_DELAY);
						this.setActive(false);
					}
				}

				if (this.getDelay() == 0)
				{
					float offset = 3.3f;
					x = MathHelper.floor_double(this.getXAhead(this.posX, offset));
					z = MathHelper.floor_double(this.getZAhead(this.posZ, offset));

					if (this.boreLayer)
					{
						boolean bored = this.boreLayer(x, y, z, dir);
						if (bored)
							this.setDelay(LAYER_DELAY);
						else
						{
							this.setDelay(FAIL_DELAY);
							this.setActive(false);
						}
						this.boreLayer = false;
					}
					else if (this.checkForLava(x, y, z, dir))
					{
						this.setDelay(FAIL_DELAY);
						this.setActive(false);
					}
					else
					{
						this.setDelay((int) Math.ceil(this.getLayerHardness(x, y, z, dir)));
						if (this.getDelay() != 0)
							this.boreLayer = true;
					}
				}
			}

			if (this.isMinecartPowered())
			{
				double i = this.getXAhead(this.posX, 3.3);
				double k = this.getZAhead(this.posZ, 3.3);
				double size = 0.8;
				List entities = this.worldObj.getEntitiesWithinAABBExcludingEntity(this, AxisAlignedBB.getBoundingBox(i - size, this.posY, k - size, i + size, this.posY + 2, k + size));
				for (Object e : entities)
					if (e instanceof EntityLivingBase)
					{
						EntityLivingBase ent = (EntityLivingBase) e;
						ent.attackEntityFrom(RailcraftDamageSource.BORE, 2);
					}
			}

			this.setMoving(this.hasFuel() && this.getDelay() == 0);

			if (this.getDelay() > 0)
				this.setDelay(this.getDelay() - 1);
		}

		if (this.isMoving())
		{
			float factorX = MathHelper.cos((float) Math.toRadians(this.rotationYaw));
			float factorZ = -MathHelper.sin((float) Math.toRadians(this.rotationYaw));
			this.motionX = SPEED * factorX;
			this.motionZ = SPEED * factorZ;
		}
		else
		{
			this.motionX = 0.0D;
			this.motionZ = 0.0D;
		}

		this.emitParticles();

		if (this.isMinecartPowered())
			this.boreRotationAngle += 5;
	}

	@Override
	public float getMaxCartSpeedOnRail()
	{
		return SPEED;
	}

	private void updateFuel()
	{
		if (Game.isHost(this.worldObj))
		{
			if (this.isMinecartPowered())
				this.spendFuel();
			this.stockFuel();
			if (this.outOfFuel())
				this.addFuel();
			this.setMinecartPowered(this.hasFuel() && this.isActive());
		}
	}

	protected double getXAhead(double x, double offset)
	{
		if (this.getFacing() == ForgeDirection.EAST)
			x += offset;
		else if (this.getFacing() == ForgeDirection.WEST)
			x -= offset;
		return x;
	}

	protected double getZAhead(double z, double offset)
	{
		if (this.getFacing() == ForgeDirection.NORTH)
			z -= offset;
		else if (this.getFacing() == ForgeDirection.SOUTH)
			z += offset;
		return z;
	}

	@SuppressWarnings("incomplete-switch")
	protected double getOffsetX(double x, double forwardOffset, double sideOffset)
	{
		switch (this.getFacing())
		{
			case NORTH:
				return x + sideOffset;
			case SOUTH:
				return x - sideOffset;
			case EAST:
				return x + forwardOffset;
			case WEST:
				return x - forwardOffset;
		}
		return x;
	}

	@SuppressWarnings("incomplete-switch")
	protected double getOffsetZ(double z, double forwardOffset, double sideOffset)
	{
		switch (this.getFacing())
		{
			case NORTH:
				return z - forwardOffset;
			case SOUTH:
				return z + forwardOffset;
			case EAST:
				return z - sideOffset;
			case WEST:
				return z + sideOffset;
		}
		return z;
	}

	protected void emitParticles()
	{
		if (this.isMinecartPowered())
		{
			double randomFactor = 0.125;

			double forwardOffset = -0.35;
			double smokeYOffset = 2.4;
			double flameYOffset = 0.7;
			double smokeSideOffset = 0.92;
			double flameSideOffset = 1.14;
			double smokeX1 = this.posX;
			double smokeX2 = this.posX;
			double smokeZ1 = this.posZ;
			double smokeZ2 = this.posZ;

			double flameX1 = this.posX;
			double flameX2 = this.posX;
			double flameZ1 = this.posZ;
			double flameZ2 = this.posZ;
			if (this.getFacing() == ForgeDirection.NORTH)
			{
				smokeX1 += smokeSideOffset;
				smokeX2 -= smokeSideOffset;
				smokeZ1 += forwardOffset;
				smokeZ2 += forwardOffset;

				flameX1 += flameSideOffset;
				flameX2 -= flameSideOffset;
				flameZ1 += forwardOffset + this.rand.nextGaussian() * randomFactor;
				flameZ2 += forwardOffset + this.rand.nextGaussian() * randomFactor;
			}
			else if (this.getFacing() == ForgeDirection.EAST)
			{
				smokeX1 -= forwardOffset;
				smokeX2 -= forwardOffset;
				smokeZ1 += smokeSideOffset;
				smokeZ2 -= smokeSideOffset;

				flameX1 -= forwardOffset + this.rand.nextGaussian() * randomFactor;
				flameX2 -= forwardOffset + this.rand.nextGaussian() * randomFactor;
				flameZ1 += flameSideOffset;
				flameZ2 -= flameSideOffset;
			}
			else if (this.getFacing() == ForgeDirection.SOUTH)
			{
				smokeX1 += smokeSideOffset;
				smokeX2 -= smokeSideOffset;
				smokeZ1 -= forwardOffset;
				smokeZ2 -= forwardOffset;

				flameX1 += flameSideOffset;
				flameX2 -= flameSideOffset;
				flameZ1 -= forwardOffset + this.rand.nextGaussian() * randomFactor;
				flameZ2 -= forwardOffset + this.rand.nextGaussian() * randomFactor;
			}
			else if (this.getFacing() == ForgeDirection.WEST)
			{
				smokeX1 += forwardOffset;
				smokeX2 += forwardOffset;
				smokeZ1 += smokeSideOffset;
				smokeZ2 -= smokeSideOffset;

				flameX1 += forwardOffset + this.rand.nextGaussian() * randomFactor;
				flameX2 += forwardOffset + this.rand.nextGaussian() * randomFactor;
				flameZ1 += flameSideOffset;
				flameZ2 -= flameSideOffset;
			}

			if (this.rand.nextInt(4) == 0)
			{
				this.worldObj.spawnParticle("largesmoke", smokeX1, this.posY + smokeYOffset, smokeZ1, 0.0D, 0.0D, 0.0D);
				this.worldObj.spawnParticle("flame", flameX1, this.posY + flameYOffset + this.rand.nextGaussian() * randomFactor, flameZ1, 0.0D, 0.0D, 0.0D);
			}
			if (this.rand.nextInt(4) == 0)
			{
				this.worldObj.spawnParticle("largesmoke", smokeX2, this.posY + smokeYOffset, smokeZ2, 0.0D, 0.0D, 0.0D);
				this.worldObj.spawnParticle("flame", flameX2, this.posY + flameYOffset + this.rand.nextGaussian() * randomFactor, flameZ2, 0.0D, 0.0D, 0.0D);
			}
		}
	}

	protected void stockBallast()
	{
		if (InvTools.isEmptySlot(this.invBallast))
		{
			ItemStack stack = CartTools.transferHelper.pullStack(this, StackFilter.BALLAST);
			if (stack != null)
				InvTools.moveItemStack(stack, this.invBallast);
		}
	}

	protected boolean placeBallast(int i, int j, int k)
	{
		if (!this.worldObj.isSideSolid(i, j, k, ForgeDirection.UP))
			for (int inv = 0; inv < this.invBallast.getSizeInventory(); inv++)
			{
				ItemStack stack = this.invBallast.getStackInSlot(inv);
				if (stack != null && BallastRegistry.isItemBallast(stack))
				{
					for (int y = j; y > j - MAX_FILL_DEPTH; y--)
						if (this.worldObj.isSideSolid(i, y, k, ForgeDirection.UP))
						{
							// TODO gamerforEA code start
							if (this.fake.cantBreak(i, j, k))
								return false;
							// TODO gamerforEA code end

							this.invBallast.decrStackSize(inv, 1);
							this.worldObj.setBlock(i, j, k, InvTools.getBlockFromStack(stack), stack.getItemDamage(), 3);
							return true;
						}
					return false;
				}
			}
		return false;
	}

	protected void stockTracks()
	{
		if (InvTools.isEmptySlot(this.invRails))
		{
			ItemStack stack = CartTools.transferHelper.pullStack(this, StackFilter.TRACK);
			if (stack != null)
				InvTools.moveItemStack(stack, this.invRails);
		}
	}

	protected boolean placeTrack(int x, int y, int z, EnumTrackMeta meta)
	{
		// TODO gamerforEA code start
		if (this.fake.cantBreak(x, y, z))
			return false;
		// TODO gamerforEA code end

		Block block = this.worldObj.getBlock(x, y, z);
		if (replaceableBlocks.contains(block))
			this.worldObj.func_147480_a(x, y, z, true);

		if (this.worldObj.isAirBlock(x, y, z) && this.worldObj.isSideSolid(x, y - 1, z, ForgeDirection.UP))
			for (int inv = 0; inv < this.invRails.getSizeInventory(); inv++)
			{
				ItemStack stack = this.invRails.getStackInSlot(inv);
				if (stack != null)
				{
					boolean placed = RailTools.placeRailAt(stack, this.worldObj, x, y, z);
					if (placed)
					{
						this.worldObj.setBlockMetadataWithNotify(x, y, z, meta.ordinal(), 3);
						this.invRails.decrStackSize(inv, 1);
					}
					return placed;
				}
			}
		return false;
	}

	protected boolean checkForLava(int i, int j, int k, EnumTrackMeta dir)
	{
		int xStart = i - 1;
		int zStart = k - 1;
		int xEnd = i + 1;
		int zEnd = k + 1;
		if (dir == EnumTrackMeta.NORTH_SOUTH)
		{
			xStart = i - 2;
			xEnd = i + 2;
		}
		else
		{
			zStart = k - 2;
			zEnd = k + 2;
		}

		for (int jj = j; jj < j + 4; jj++)
			for (int ii = xStart; ii <= xEnd; ii++)
				for (int kk = zStart; kk <= zEnd; kk++)
				{
					Block block = this.worldObj.getBlock(ii, jj, kk);
					if (block == Blocks.lava || block == Blocks.flowing_lava)
						return true;
				}

		return false;
	}

	protected boolean boreLayer(int i, int j, int k, EnumTrackMeta dir)
	{
		boolean clear = true;
		int ii = i;
		int kk = k;
		for (int jj = j; jj < j + 3; jj++)
			clear = clear && this.mineBlock(ii, jj, kk, dir);

		if (dir == EnumTrackMeta.NORTH_SOUTH)
			ii--;
		else
			kk--;
		for (int jj = j; jj < j + 3; jj++)
			clear = clear && this.mineBlock(ii, jj, kk, dir);

		ii = i;
		kk = k;
		if (dir == EnumTrackMeta.NORTH_SOUTH)
			ii++;
		else
			kk++;
		for (int jj = j; jj < j + 3; jj++)
			clear = clear && this.mineBlock(ii, jj, kk, dir);
		return clear;
	}

	protected boolean mineBlock(int x, int y, int z, EnumTrackMeta dir)
	{
		if (this.worldObj.isAirBlock(x, y, z))
			return true;

		Block block = this.worldObj.getBlock(x, y, z);
		if (TrackTools.isRailBlock(block))
		{
			int trackMeta = TrackTools.getTrackMeta(this.worldObj, block, this, x, y, z);
			if (dir.isEqual(trackMeta))
				return true;
		}
		else if (block == Blocks.torch)
			return true;

		ItemStack head = this.getStackInSlot(0);
		if (head == null)
			return false;

		int meta = this.worldObj.getBlockMetadata(x, y, z);

		if (!this.canMineBlock(x, y, z, block, meta))
			return false;

		/* TODO gamerforEA code replace, old code:
		// Start of Event Fire
		BreakEvent breakEvent = new BreakEvent(x, y, z, worldObj, block, meta, PlayerPlugin.getFakePlayer((WorldServer) worldObj, posX, posY, posZ));
		MinecraftForge.EVENT_BUS.post(breakEvent);

		if (breakEvent.isCanceled())
			return false;
		// End of Event Fire */
		if (this.fake.cantBreak(x, y, z))
			return false;
		// TODO gamerforEA code end

		ArrayList<ItemStack> items = block.getDrops(this.worldObj, x, y, z, meta, 0);

		for (ItemStack stack : items)
		{
			if (StackFilter.FUEL.matches(stack))
				stack = InvTools.moveItemStack(stack, this.invFuel);

			if (stack != null && stack.stackSize > 0 && InvTools.isStackEqualToBlock(stack, Blocks.gravel))
				stack = InvTools.moveItemStack(stack, this.invBallast);

			if (stack != null && stack.stackSize > 0)
				stack = CartTools.transferHelper.pushStack(this, stack);

			if (stack != null && stack.stackSize > 0 && !RailcraftConfig.boreDestroysBlocks())
			{
				float f = 0.7F;
				double xr = (this.worldObj.rand.nextFloat() - 0.5D) * f;
				double yr = (this.worldObj.rand.nextFloat() - 0.5D) * f;
				double zr = (this.worldObj.rand.nextFloat() - 0.5D) * f;
				EntityItem entityitem = new EntityItem(this.worldObj, this.getXAhead(this.posX, -3.2) + xr, this.posY + 0.3 + yr, this.getZAhead(this.posZ, -3.2) + zr, stack);
				this.worldObj.spawnEntityInWorld(entityitem);
			}
		}

		this.worldObj.setBlockToAir(x, y, z);

		head.setItemDamage(head.getItemDamage() + 1);
		if (head.getItemDamage() > head.getMaxDamage())
			this.setInventorySlotContents(0, null);
		return true;
	}

	private boolean canMineBlock(int i, int j, int k, Block block, int meta)
	{
		ItemStack head = this.getStackInSlot(0);
		if (block instanceof IMineable)
		{
			if (head == null)
				return false;
			return ((IMineable) block).canMineBlock(this.worldObj, i, j, k, this, head);
		}
		if (block.getBlockHardness(this.worldObj, i, j, k) < 0)
			return false;
		return this.isMinableBlock(block, meta) && canHeadHarvestBlock(head, block, meta);
	}

	protected float getLayerHardness(int i, int j, int k, EnumTrackMeta dir)
	{
		float hardness = 0;
		int ii = i;
		int kk = k;
		for (int jj = j; jj < j + 3; jj++)
			hardness += this.getBlockHardness(ii, jj, kk, dir);

		if (dir == EnumTrackMeta.NORTH_SOUTH)
			ii--;
		else
			kk--;
		for (int jj = j; jj < j + 3; jj++)
			hardness += this.getBlockHardness(ii, jj, kk, dir);

		ii = i;
		kk = k;
		if (dir == EnumTrackMeta.NORTH_SOUTH)
			ii++;
		else
			kk++;
		for (int jj = j; jj < j + 3; jj++)
			hardness += this.getBlockHardness(ii, jj, kk, dir);

		hardness *= HARDNESS_MULTIPLER;

		ItemStack boreSlot = this.getStackInSlot(0);
		if (boreSlot != null && boreSlot.getItem() instanceof IBoreHead)
		{
			IBoreHead head = (IBoreHead) boreSlot.getItem();
			float dig = 2f - head.getDigModifier();
			hardness *= dig;
		}

		hardness /= RailcraftConfig.boreMiningSpeedMultiplier();

		return hardness;
	}

	protected float getBlockHardness(int x, int y, int z, EnumTrackMeta dir)
	{
		if (this.worldObj.isAirBlock(x, y, z))
			return 0;

		Block block = this.worldObj.getBlock(x, y, z);
		if (TrackTools.isRailBlock(block))
		{
			int trackMeta = TrackTools.getTrackMeta(this.worldObj, block, this, x, y, z);
			if (dir.isEqual(trackMeta))
				return 0;
		}

		if (block == Blocks.torch)
			return 0;

		if (block == Blocks.obsidian)
			return 15;

		int meta = this.worldObj.getBlockMetadata(x, y, z);
		if (!this.canMineBlock(x, y, z, block, meta))
			return 0.1f;

		float hardness = block.getBlockHardness(this.worldObj, x, y, z);
		if (hardness <= 0)
			hardness = 0.1f;
		return hardness;
	}

	@Override
	public AxisAlignedBB getCollisionBox(Entity other)
	{
		if (other instanceof EntityLivingBase)
			return other.boundingBox;
		return null;
	}

	@Override
	public AxisAlignedBB getBoundingBox()
	{
		return null;
	}

	@Override
	public String getInventoryName()
	{
		return "Tunnel Bore";
	}

	public float getBoreRotationAngle()
	{
		return (float) Math.toRadians(this.boreRotationAngle);
	}

	@Override
	protected void writeEntityToNBT(NBTTagCompound data)
	{
		//        fuel = getFuel();
		super.writeEntityToNBT(data);
		data.setByte("facing", (byte) this.getFacing().ordinal());
		data.setInteger("delay", this.getDelay());
		data.setBoolean("active", this.isActive());
		data.setInteger("burnTime", this.getBurnTime());
		data.setInteger("fuel", this.fuel);
	}

	@Override
	protected void readEntityFromNBT(NBTTagCompound data)
	{
		super.readEntityFromNBT(data);
		this.setFacing(ForgeDirection.getOrientation(data.getByte("facing")));
		this.setDelay(data.getInteger("delay"));
		this.setActive(data.getBoolean("active"));
		this.setBurnTime(data.getInteger("burnTime"));
		this.setFuel(data.getInteger("fuel"));
	}

	protected int getDelay()
	{
		return this.delay;
		//        return dataWatcher.getWatchableObjectInt(WATCHER_ID_DELAY);
	}

	protected void setDelay(int i)
	{
		this.delay = i;
		//        dataWatcher.updateObject(WATCHER_ID_DELAY, Integer.valueOf(i));
	}

	protected boolean isActive()
	{
		return this.active;
		//        return dataWatcher.getWatchableObjectByte(WATCHER_ID_ACTIVE) != 0;
	}

	protected void setActive(boolean active)
	{
		this.active = active;
		TrainState state = active ? Train.TrainState.STOPPED : Train.TrainState.NORMAL;
		Train.getTrain(this).setTrainState(state);
		//        dataWatcher.updateObject(WATCHER_ID_ACTIVE, Byte.valueOf((byte)(active ? 1 : 0)));
	}

	protected boolean isMoving()
	{
		return this.dataWatcher.getWatchableObjectByte(WATCHER_ID_MOVING) != 0;
	}

	protected void setMoving(boolean move)
	{
		this.dataWatcher.updateObject(WATCHER_ID_MOVING, (byte) (move ? 1 : 0));
	}

	public int getBurnTime()
	{
		return this.burnTime;
		//        return dataWatcher.getWatchableObjectInt(WATCHER_ID_BURN_TIME);
	}

	public void setBurnTime(int burnTime)
	{
		this.burnTime = burnTime;
		//        dataWatcher.updateObject(WATCHER_ID_BURN_TIME, Integer.valueOf(burnTime));
	}

	public int getFuel()
	{
		return this.fuel;
		//        return dataWatcher.getWatchableObjectInt(WATCHER_ID_FUEL);
	}

	public void setFuel(int i)
	{
		this.fuel = i;
		//        dataWatcher.updateObject(WATCHER_ID_FUEL, Integer.valueOf(i));
	}

	public boolean outOfFuel()
	{
		return this.getFuel() <= FUEL_CONSUMPTION;
	}

	public boolean hasFuel()
	{
		return this.getFuel() > 0;
	}

	protected void stockFuel()
	{
		if (InvTools.isEmptySlot(this.invFuel))
		{
			ItemStack stack = CartTools.transferHelper.pullStack(this, StackFilter.FUEL);
			if (stack != null)
				InvTools.moveItemStack(stack, this.invFuel);
		}
	}

	protected void addFuel()
	{
		int burn = 0;
		for (int slot = 0; slot < this.invFuel.getSizeInventory(); slot++)
		{
			ItemStack stack = this.invFuel.getStackInSlot(slot);
			if (stack != null)
			{
				burn = FuelPlugin.getBurnTime(stack);
				if (burn > 0)
				{
					if (stack.getItem().hasContainerItem(stack))
						this.invFuel.setInventorySlotContents(slot, stack.getItem().getContainerItem(stack));
					else
						this.invFuel.decrStackSize(slot, 1);
					break;
				}
			}
		}
		if (burn > 0)
		{
			this.setBurnTime(burn + this.getFuel());
			this.setFuel(this.getFuel() + burn);
		}
	}

	public int getBurnProgressScaled(int i)
	{
		int burn = this.getBurnTime();
		if (burn == 0)
			return 0;

		return this.getFuel() * i / burn;
	}

	protected void spendFuel()
	{
		this.setFuel(this.getFuel() - FUEL_CONSUMPTION);
	}

	protected void forceUpdateBoreHead()
	{
		ItemStack boreStack = this.getStackInSlot(0);
		if (boreStack != null)
			boreStack = boreStack.copy();
		this.dataWatcher.updateObject(WATCHER_ID_BORE_HEAD, boreStack);
	}

	public IBoreHead getBoreHead()
	{
		ItemStack boreStack = this.dataWatcher.getWatchableObjectItemStack(WATCHER_ID_BORE_HEAD);
		if (boreStack != null && boreStack.getItem() instanceof IBoreHead)
			return (IBoreHead) boreStack.getItem();
		return null;
	}

	@Override
	protected void applyDrag()
	{
		this.motionX *= this.getDrag();
		this.motionY *= 0.0D;
		this.motionZ *= this.getDrag();
	}

	@Override
	public List<ItemStack> getItemsDropped()
	{
		List<ItemStack> items = new ArrayList<ItemStack>();
		items.add(this.getCartItem());
		return items;
	}

	@Override
	public boolean isPoweredCart()
	{
		return true;
	}

	@Override
	public boolean doInteract(EntityPlayer player)
	{
		if (Game.isHost(this.worldObj))
			GuiHandler.openGui(EnumGui.CART_BORE, player, this.worldObj, this);
		return true;
	}

	@Override
	public void markDirty()
	{
		if (!this.isActive())
			this.setDelay(STANDARD_DELAY);
	}

	public final ForgeDirection getFacing()
	{
		return ForgeDirection.getOrientation(this.dataWatcher.getWatchableObjectByte(WATCHER_ID_FACING));
	}

	protected final void setFacing(ForgeDirection facing)
	{
		this.dataWatcher.updateObject(WATCHER_ID_FACING, (byte) facing.ordinal());

		this.setYaw();
	}

	@Override
	public boolean isLinkable()
	{
		return true;
	}

	@Override
	public boolean canLinkWithCart(EntityMinecart cart)
	{
		double x = this.getXAhead(this.posX, -LENGTH / 2);
		double z = this.getZAhead(this.posZ, -LENGTH / 2);

		return cart.getDistance(x, this.posY, z) < ILinkageManager.LINKAGE_DISTANCE * 2;
	}

	@Override
	public boolean hasTwoLinks()
	{
		return false;
	}

	@Override
	public float getLinkageDistance(EntityMinecart cart)
	{
		return 4f;
	}

	@Override
	public float getOptimalDistance(EntityMinecart cart)
	{
		return 3.1f;
	}

	@Override
	public void onLinkCreated(EntityMinecart cart)
	{
	}

	@Override
	public void onLinkBroken(EntityMinecart cart)
	{
	}

	@Override
	public boolean canBeAdjusted(EntityMinecart cart)
	{
		return !this.isActive();
	}

	@Override
	public boolean shouldDoRailFunctions()
	{
		return false;
	}

	public IInventory getInventoryFuel()
	{
		return this.invFuel;
	}

	public IInventory getInventoryGravel()
	{
		return this.invBallast;
	}

	public IInventory getInventoryRails()
	{
		return this.invRails;
	}

	@Override
	public Entity[] getParts()
	{
		return this.partArray;
	}

	public boolean attackEntityFromPart(EntityTunnelBorePart part, DamageSource damageSource, float damage)
	{
		return this.attackEntityFrom(damageSource, damage);
	}
}
