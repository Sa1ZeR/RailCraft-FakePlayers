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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.gamerforea.railcraft.ExplosionByPlayer;
import com.mojang.authlib.GameProfile;

import mods.railcraft.api.carts.CartTools;
import mods.railcraft.api.carts.IMinecart;
import mods.railcraft.common.blocks.tracks.TrackTools;
import mods.railcraft.common.util.inventory.InvTools;
import mods.railcraft.common.util.misc.Game;
import mods.railcraft.common.util.misc.MiscTools;
import net.minecraft.block.Block;
import net.minecraft.entity.item.EntityMinecart;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

/**
 *
 * @author CovertJaguar <http://www.railcraft.info>
 */
public class CartUtils
{
	public static Map<Item, EnumCart> vanillaCartItemMap = new HashMap<Item, EnumCart>();
	public static Map<Class<? extends EntityMinecart>, EnumCart> classReplacements = new HashMap<Class<? extends EntityMinecart>, EnumCart>();

	/**
	 * Spawns a new cart entity using the provided item.
	 *
	 * The backing item must implement <code>IMinecartItem</code> and/or extend
	 * <code>ItemMinecart</code>.
	 *
	 * Generally Forge requires all cart items to extend ItemMinecart.
	 *
	 * @param owner The player name that should used as the owner
	 * @param cart  An ItemStack containing a cart item, will not be changed by
	 *              the function
	 * @param world The World object
	 * @param x     x-Coord
	 * @param y     y-Coord
	 * @param z     z-Coord
	 * @return the cart placed or null if failed
	 * @see IMinecartItem, ItemMinecart
	 */
	public static EntityMinecart placeCart(GameProfile owner, ItemStack cart, WorldServer world, int x, int y, int z)
	{
		if (cart == null)
			return null;
		cart = cart.copy();

		EnumCart vanillaType = vanillaCartItemMap.get(cart.getItem());
		if (vanillaType != null)
			return placeCart(vanillaType, owner, cart, world, x, y, z);

		return CartTools.placeCart(owner, cart, world, x, y, z);
	}

	public static EntityMinecart placeCart(EnumCart cartType, GameProfile owner, ItemStack cartStack, World world, int i, int j, int k)
	{
		Block block = world.getBlock(i, j, k);
		if (TrackTools.isRailBlock(block))
			if (!CartTools.isMinecartAt(world, i, j, k, 0))
			{
				EntityMinecart cart = cartType.makeCart(cartStack, world, i + 0.5F, j + 0.5F, k + 0.5F);
				if (cartStack.hasDisplayName())
					cart.setMinecartName(cartStack.getDisplayName());
				CartTools.setCartOwner(cart, owner);
				if (world.spawnEntityInWorld(cart))
					return cart;
			}
		return null;
	}

	/**
	 * Will return true if the cart matches the provided filter item.
	 *
	 * @param stack the Filter
	 * @param cart  the Cart
	 * @return true if the item matches the cart
	 * @see IMinecart
	 */
	public static boolean doesCartMatchFilter(ItemStack stack, EntityMinecart cart)
	{
		if (stack == null)
			return false;
		if (cart instanceof IMinecart)
			return ((IMinecart) cart).doesCartMatchFilter(stack, cart);
		ItemStack cartItem = cart.getCartItem();
		return cartItem != null && InvTools.isItemEqual(stack, cartItem, true, false);
	}

	public static void explodeCart(EntityMinecart cart)
	{
		if (cart.isDead)
			return;
		cart.getEntityData().setBoolean("HighSpeed", false);
		cart.motionX = 0;
		cart.motionZ = 0;
		if (Game.isNotHost(cart.worldObj))
			return;
		if (cart.riddenByEntity != null)
			cart.riddenByEntity.mountEntity(cart);

		// TODO gamerforEA code replace, old code: cart.worldObj.newExplosion(cart, cart.posX, cart.posY, cart.posZ, 3F, true, true);
		if (cart instanceof CartBase)
			ExplosionByPlayer.newExplosion(((CartBase) cart).fake.getPlayer(), cart.worldObj, cart, cart.posX, cart.posY, cart.posZ, 3F, true, true);
		else
			ExplosionByPlayer.newExplosion(null, cart.worldObj, cart, cart.posX, cart.posY, cart.posZ, 3F, true, true);
		// TODO gamerforEA code end

		if (MiscTools.getRand().nextInt(2) == 0)
			cart.setDead();
	}

	public static boolean cartVelocityIsLessThan(EntityMinecart cart, float vel)
	{
		return Math.abs(cart.motionX) < vel && Math.abs(cart.motionZ) < vel;
	}

	@SuppressWarnings("rawtypes")
	public static List<EntityMinecart> getMinecartsIn(World world, AxisAlignedBB searchBox)
	{
		List entities = world.getEntitiesWithinAABB(EntityMinecart.class, searchBox);
		List<EntityMinecart> carts = new ArrayList<EntityMinecart>();
		for (Object o : entities)
		{
			EntityMinecart cart = (EntityMinecart) o;
			if (!cart.isDead)
				carts.add((EntityMinecart) o);
		}
		return carts;
	}

	@SuppressWarnings("rawtypes")
	public static List<UUID> getMinecartUUIDsAt(World world, int i, int j, int k, float sensitivity)
	{
		sensitivity = Math.min(sensitivity, 0.49f);
		List entities = world.getEntitiesWithinAABB(EntityMinecart.class, AxisAlignedBB.getBoundingBox(i + sensitivity, j + sensitivity, k + sensitivity, i + 1 - sensitivity, j + 1 - sensitivity, k + 1 - sensitivity));
		List<UUID> carts = new ArrayList<UUID>();
		for (Object o : entities)
		{
			EntityMinecart cart = (EntityMinecart) o;
			if (!cart.isDead)
				carts.add(((EntityMinecart) o).getPersistentID());
		}
		return carts;
	}

}
