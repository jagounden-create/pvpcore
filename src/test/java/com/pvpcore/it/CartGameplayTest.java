package com.pvpcore.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.pvpcore.Feature;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.ExplosionResult;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.minecart.ExplosiveMinecart;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class CartGameplayTest extends PluginTest {
   ExplosiveMinecart cart(double x) {
      return this.world.spawn(new Location(this.world, x, 64, 0), ExplosiveMinecart.class);
   }

   Arrow burningArrow(PlayerMock shooter, boolean crossbow) {
      Arrow arrow = this.world.spawn(new Location(this.world, 0, 65, 0), Arrow.class);
      arrow.setShooter(shooter);
      arrow.setFireTicks(200);
      arrow.setShotFromCrossbow(crossbow);
      return arrow;
   }

   EntityDamageByEntityEvent blast(ExplosiveMinecart cart, PlayerMock victim, double damage) {
      DamageSource source = DamageSource.builder(DamageType.EXPLOSION).withDirectEntity(cart).build();
      return this.call(new EntityDamageByEntityEvent(cart, victim, DamageCause.ENTITY_EXPLOSION, source, damage));
   }

   @Test
   void crossbowCartsAreNerfedMoreThanBowCarts() {
      PlayerMock shooter = this.server.addPlayer();
      PlayerMock victim = this.server.addPlayer();

      ExplosiveMinecart bowCart = this.cart(0);
      this.call(new ProjectileHitEvent(this.burningArrow(shooter, false), bowCart));
      assertEquals(16.0, this.blast(bowCart, victim, 20.0).getDamage(), 1.0E-9, "bow cart x0.8");

      ExplosiveMinecart crossbowCart = this.cart(10);
      this.call(new ProjectileHitEvent(this.burningArrow(shooter, true), crossbowCart));
      assertEquals(12.0, this.blast(crossbowCart, victim, 20.0).getDamage(), 1.0E-9, "crossbow cart x0.6");

      ExplosiveMinecart railCart = this.cart(20);
      assertEquals(20.0, this.blast(railCart, victim, 20.0).getDamage(), 1.0E-9, "carts lit another way are untouched");
   }

   @Test
   void aChainReactionKeepsTheWeaponThatStartedIt() {
      PlayerMock shooter = this.server.addPlayer();
      PlayerMock victim = this.server.addPlayer();
      ExplosiveMinecart first = this.cart(0);
      ExplosiveMinecart second = this.cart(4);
      this.call(new ProjectileHitEvent(this.burningArrow(shooter, true), first));
      this.call(new ExplosionPrimeEvent(first, 6.0F, false));
      this.call(new ExplosionPrimeEvent(second, 6.0F, false));
      assertEquals(12.0, this.blast(second, victim, 20.0).getDamage(), 1.0E-9);
   }

   @Test
   void stackedCartsMergeIntoOneBlast() {
      ExplosiveMinecart first = this.cart(0);
      ExplosiveMinecart stacked = this.cart(0.3);
      ExplosiveMinecart apart = this.cart(5);
      assertFalse(this.call(new ExplosionPrimeEvent(first, 6.0F, false)).isCancelled());
      assertTrue(this.call(new ExplosionPrimeEvent(stacked, 6.0F, false)).isCancelled());
      assertFalse(stacked.isValid(), "the merged cart is gone");
      assertFalse(this.call(new ExplosionPrimeEvent(apart, 6.0F, false)).isCancelled());
      this.ticks(1);
      ExplosiveMinecart nextTick = this.cart(0.3);
      assertFalse(this.call(new ExplosionPrimeEvent(nextTick, 6.0F, false)).isCancelled(), "only blasts in the same tick merge");
   }

   @Test
   void consistentPower() {
      ExplosionPrimeEvent event = this.call(new ExplosionPrimeEvent(this.cart(0), 9.3F, false));
      assertEquals(9.3F, event.getRadius(), "vanilla by default");
      this.set(Feature.CART_POWER, 6.5);
      event = this.call(new ExplosionPrimeEvent(this.cart(20), 9.3F, false));
      assertEquals(6.5F, event.getRadius());
   }

   @Test
   void terrainAndDrops() {
      ExplosiveMinecart cart = this.cart(0);
      List<Block> blocks = new ArrayList<>(List.of(this.world.getBlockAt(1, 63, 0), this.world.getBlockAt(2, 63, 0)));
      EntityExplodeEvent event = this.call(new EntityExplodeEvent(cart, cart.getLocation(), blocks, 1.0F, ExplosionResult.DESTROY));
      assertEquals(2, event.blockList().size());
      assertEquals(0.0F, event.getYield(), "no block drops by default");

      this.set(Feature.CART_TERRAIN, true);
      blocks = new ArrayList<>(List.of(this.world.getBlockAt(1, 63, 0)));
      event = this.call(new EntityExplodeEvent(cart, cart.getLocation(), blocks, 1.0F, ExplosionResult.DESTROY));
      assertTrue(event.blockList().isEmpty(), "terrain protected");
   }

   @Test
   void chunkLimit() {
      this.set(Feature.CART_LIMIT, 2);
      PlayerMock player = this.server.addPlayer();
      this.cart(1);
      this.cart(2);
      ExplosiveMinecart third = this.world.spawn(new Location(this.world, 3, 64, 0), ExplosiveMinecart.class);
      EntityPlaceEvent place = this.call(new EntityPlaceEvent(third, player, this.world.getBlockAt(3, 64, 0), BlockFace.UP, EquipmentSlot.HAND));
      assertTrue(place.isCancelled());
   }

   @Test
   void selfDamage() {
      this.set(Feature.CART_SELF_DAMAGE, 50);
      PlayerMock owner = this.server.addPlayer();
      PlayerMock other = this.server.addPlayer();
      ExplosiveMinecart cart = this.cart(0);
      this.call(new EntityPlaceEvent(cart, owner, this.world.getBlockAt(0, 64, 0), BlockFace.UP, EquipmentSlot.HAND));
      assertEquals(10.0, this.blast(cart, owner, 20.0).getDamage(), 1.0E-9);
      assertEquals(20.0, this.blast(cart, other, 20.0).getDamage(), 1.0E-9);
   }
}
