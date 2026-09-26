package com.pvpcore.it;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.minecart.ExplosiveMinecart;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.inventory.EquipmentSlot;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.ArrowMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class CartHitRegTest extends PluginTest {
   /** A burning arrow flying along x at cart height, one tick per step. */
   /** MockBukkit's arrow can't tell whether it is stuck in a block; these fly. */
   static final class FlyingArrow extends ArrowMock {
      FlyingArrow(ServerMock server) {
         super(server, UUID.randomUUID());
      }

      @Override
      public boolean isInBlock() {
         return false;
      }
   }

   Arrow flyThrough(PlayerMock shooter, double z, double... xs) {
      FlyingArrow arrow = new FlyingArrow(this.server);
      arrow.setLocation(new Location(this.world, xs[0], 64.3, z));
      this.server.registerEntity(arrow);
      arrow.setShooter(shooter);
      arrow.setFireTicks(200);
      this.call(new ProjectileLaunchEvent(arrow));
      for (double x : xs) {
         arrow.teleport(new Location(this.world, x, 64.3, z));
         this.ticks(1);
      }

      return arrow;
   }

   ExplosiveMinecart placeCart(PlayerMock placer) {
      ExplosiveMinecart cart = this.world.spawn(new Location(this.world, 0.5, 64.0, 0.5), ExplosiveMinecart.class);
      this.call(new EntityPlaceEvent(cart, placer, this.world.getBlockAt(0, 64, 0), BlockFace.UP, EquipmentSlot.HAND));
      return cart;
   }

   @Test
   void aCartPlacedWhereABurningArrowJustFlewGoesOff() {
      PlayerMock player = this.server.addPlayer();
      Arrow arrow = this.flyThrough(player, 0.5, -3, 3);
      ExplosiveMinecart cart = this.placeCart(player);
      this.ticks(1);
      assertFalse(arrow.isValid(), "the arrow was used up by the hit");
      assertFalse(cart.isValid() && !cart.isDead(), "the cart went off");
   }

   @Test
   void anArrowThatMissedDoesNothing() {
      PlayerMock player = this.server.addPlayer();
      Arrow arrow = this.flyThrough(player, 4.0, -3, 3);
      ExplosiveMinecart cart = this.placeCart(player);
      this.ticks(1);
      assertTrue(arrow.isValid());
      assertTrue(cart.isValid());
   }

   @Test
   void anArrowFromTooLongAgoDoesNothing() {
      PlayerMock player = this.server.addPlayer();
      Arrow arrow = this.flyThrough(player, 0.5, -3, 3, 9, 15, 21, 27, 33, 39);
      ExplosiveMinecart cart = this.placeCart(player);
      this.ticks(1);
      assertTrue(cart.isValid(), "the path through the cart's spot is older than the ping window");
      assertTrue(arrow.isValid());
   }
}
