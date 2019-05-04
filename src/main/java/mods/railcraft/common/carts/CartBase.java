/*
 * Copyright (c) CovertJaguar, 2014 http://railcraft.info
 *
 * This code is the property of CovertJaguar
 * and may only be used with explicit written
 * permission unless otherwise specified on the
 * license page at http://railcraft.info/wiki/info:license.
 */
package mods.railcraft.common.carts;

import com.gamerforea.eventhelper.fake.FakePlayerContainer;
import com.gamerforea.railcraft.ModUtils;
import mods.railcraft.api.carts.IItemCart;
import net.minecraft.entity.item.EntityMinecart;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.minecart.MinecartInteractEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * It also contains some generic code that most carts will find useful.
 *
 * @author CovertJaguar <http://www.railcraft.info>
 */
public abstract class CartBase extends EntityMinecart implements IRailcraftCart, IItemCart
{
	// TODO gamerforEA code start
	public final FakePlayerContainer fake = ModUtils.NEXUS_FACTORY.wrapFake(this);

	@Override
	protected void writeEntityToNBT(NBTTagCompound nbt)
	{
		super.writeEntityToNBT(nbt);
		this.fake.writeToNBT(nbt);
	}

	@Override
	protected void readEntityFromNBT(NBTTagCompound nbt)
	{
		super.readEntityFromNBT(nbt);
		this.fake.readFromNBT(nbt);
	}
	// TODO gamerforEA code end

	public CartBase(World world)
	{
		super(world);
		this.renderDistanceWeight = CartConstants.RENDER_DIST_MULTIPLIER;
	}

	public CartBase(World world, double x, double y, double z)
	{
		super(world, x, y, z);
		this.renderDistanceWeight = CartConstants.RENDER_DIST_MULTIPLIER;
	}

	@Override
	public void initEntityFromItem(ItemStack stack)
	{
	}

	@Override
	public final boolean interactFirst(EntityPlayer player)
	{
		if (MinecraftForge.EVENT_BUS.post(new MinecartInteractEvent(this, player)))
			return true;
		return this.doInteract(player);
	}

	public boolean doInteract(EntityPlayer player)
	{
		return true;
	}

	public abstract double getDrag();

	@Override
	public ItemStack getCartItem()
	{
		ItemStack stack = EnumCart.fromCart(this).getCartItem();
		if (this.hasCustomInventoryName())
			stack.setStackDisplayName(this.getCommandSenderName());
		return stack;
	}

	public List<ItemStack> getItemsDropped()
	{
		List<ItemStack> items = new ArrayList<ItemStack>();
		items.add(this.getCartItem());
		return items;
	}

	@Override
	public void killMinecart(DamageSource par1DamageSource)
	{
		this.setDead();
		List<ItemStack> drops = this.getItemsDropped();
		if (this.func_95999_t() != null)
			drops.get(0).setStackDisplayName(this.func_95999_t());
		for (ItemStack item : drops)
		{
			this.entityDropItem(item, 0.0F);
		}
	}

	@Override
	public int getMinecartType()
	{
		return -1;
	}

	@Override
	public boolean canPassItemRequests()
	{
		return false;
	}

	@Override
	public boolean canAcceptPushedItem(EntityMinecart requester, ItemStack stack)
	{
		return false;
	}

	@Override
	public boolean canProvidePulledItem(EntityMinecart requester, ItemStack stack)
	{
		return false;
	}
}
