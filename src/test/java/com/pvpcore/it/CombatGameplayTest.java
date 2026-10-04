package com.pvpcore.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.pvpcore.Feature;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.event.Event.Result;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class CombatGameplayTest extends PluginTest {
   @Test
   void combatTagBlocksCommandsExpiresAndPunishesLogout() {
      this.set(Feature.COMBAT_TAG, 5);
      PlayerMock a = this.server.addPlayer();
      PlayerMock b = this.server.addPlayer();
      this.call(hit(a, b, DamageCause.ENTITY_ATTACK, 2));
      assertTrue(this.call(new PlayerCommandPreprocessEvent(a, "/spawn")).isCancelled());
      assertTrue(this.call(new PlayerCommandPreprocessEvent(b, "/essentials:home")).isCancelled());
      assertFalse(this.call(new PlayerCommandPreprocessEvent(b, "/msg a hi")).isCancelled());
      this.ticks(5 * 20 + 10);
      assertFalse(this.call(new PlayerCommandPreprocessEvent(a, "/spawn")).isCancelled(), "tag expired");

      this.call(hit(a, b, DamageCause.ENTITY_ATTACK, 2));
      b.disconnect();
      assertEquals(0.0, b.getHealth(), "logging out in combat kills");
   }

   @Test
   void selfHitsAndBypassDoNotTag() {
      this.set(Feature.COMBAT_TAG, 5);
      PlayerMock a = this.server.addPlayer();
      PlayerMock b = this.server.addPlayer();
      b.addAttachment(this.plugin, "pvpcore.bypass.combattag", true);
      this.call(hit(a, a, DamageCause.ENTITY_ATTACK, 2));
      assertFalse(this.call(new PlayerCommandPreprocessEvent(a, "/spawn")).isCancelled());
      this.call(hit(a, b, DamageCause.ENTITY_ATTACK, 2));
      assertFalse(this.call(new PlayerCommandPreprocessEvent(b, "/spawn")).isCancelled());
   }

   @Test
   void crystalBanAndBypass() {
      PlayerMock player = this.server.addPlayer();
      PlayerInteractEvent place = this.call(new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, new ItemStack(Material.END_CRYSTAL),
         this.world.getBlockAt(0, 64, 0), BlockFace.UP, EquipmentSlot.HAND));
      assertEquals(Result.DENY, place.useItemInHand());
      player.addAttachment(this.plugin, "pvpcore.bypass.crystals", true);
      place = this.call(new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, new ItemStack(Material.END_CRYSTAL),
         this.world.getBlockAt(0, 64, 0), BlockFace.UP, EquipmentSlot.HAND));
      assertEquals(Result.DEFAULT, place.useItemInHand());
   }

   @Test
   void maceCapAndSmashScaling() {
      PlayerMock attacker = this.server.addPlayer();
      PlayerMock victim = this.server.addPlayer();
      attacker.getInventory().setItemInMainHand(new ItemStack(Material.MACE));
      attacker.setFallDistance(10.0F); // vanilla bonus 24
      assertEquals(30.0, this.call(hit(attacker, victim, DamageCause.ENTITY_ATTACK, 30.0)).getDamage(), 1.0E-9, "vanilla by default");

      this.set(Feature.SMASH_DAMAGE, 0.5);
      assertEquals(30.0 - 12.0, this.call(hit(attacker, victim, DamageCause.ENTITY_ATTACK, 30.0)).getDamage(), 1.0E-9, "half the smash bonus");

      this.set(Feature.SMASH_CAP, 5);
      assertEquals(10.0, this.call(hit(attacker, victim, DamageCause.ENTITY_ATTACK, 30.0)).getDamage(), 1.0E-9, "capped at 5 hearts");
   }

   @Test
   void smashCooldownRemovesOnlyTheBonus() {
      this.set(Feature.SMASH_COOLDOWN, 2);
      PlayerMock attacker = this.server.addPlayer();
      PlayerMock victim = this.server.addPlayer();
      attacker.getInventory().setItemInMainHand(new ItemStack(Material.MACE));
      attacker.setFallDistance(3.0F); // bonus 12
      assertEquals(18.0, this.call(hit(attacker, victim, DamageCause.ENTITY_ATTACK, 18.0)).getDamage(), 1.0E-9);
      assertEquals(6.0, this.call(hit(attacker, victim, DamageCause.ENTITY_ATTACK, 18.0)).getDamage(), 1.0E-9, "second smash inside the cooldown");
      this.ticks(41);
      assertEquals(18.0, this.call(hit(attacker, victim, DamageCause.ENTITY_ATTACK, 18.0)).getDamage(), 1.0E-9);
   }

   @Test
   void swallowedSmashCostsNoFallDamage() {
      PlayerMock attacker = this.server.addPlayer();
      PlayerMock victim = this.server.addPlayer();
      attacker.getInventory().setItemInMainHand(new ItemStack(Material.MACE));
      attacker.teleport(new Location(this.world, 0, 80, 0));
      attacker.setFallDistance(6.0F);
      this.call(new PrePlayerAttackEntityEvent(attacker, victim, true));
      attacker.teleport(new Location(this.world, 0, 78, 0));
      attacker.setFallDistance(8.0F);
      EntityDamageEvent fall = this.call(new EntityDamageEvent(attacker, DamageCause.FALL, DamageSource.builder(DamageType.FALL).build(), 5.0));
      assertTrue(fall.isCancelled());

      // a plain fall with no smash is untouched
      attacker.setFallDistance(8.0F);
      fall = this.call(new EntityDamageEvent(attacker, DamageCause.FALL, DamageSource.builder(DamageType.FALL).build(), 5.0));
      assertFalse(fall.isCancelled());
   }

   @Test
   void bedrockBuffForSwordsAndAxesOnly() {
      PlayerMock bedrock = new PlayerMock(this.server, "BedrockGuy", new UUID(0L, 42L));
      this.server.addPlayer(bedrock);
      PlayerMock java = this.server.addPlayer();
      PlayerMock victim = this.server.addPlayer();
      bedrock.getInventory().setItemInMainHand(new ItemStack(Material.DIAMOND_SWORD));
      java.getInventory().setItemInMainHand(new ItemStack(Material.DIAMOND_SWORD));
      assertEquals(10.2, this.call(hit(bedrock, victim, DamageCause.ENTITY_ATTACK, 10.0)).getDamage(), 1.0E-9);
      assertEquals(10.0, this.call(hit(java, victim, DamageCause.ENTITY_ATTACK, 10.0)).getDamage(), 1.0E-9);
      bedrock.getInventory().setItemInMainHand(new ItemStack(Material.MACE));
      assertEquals(10.0, this.call(hit(bedrock, victim, DamageCause.ENTITY_ATTACK, 10.0)).getDamage(), 1.0E-9, "mace is not buffed");
   }

   @Test
   void noSweepDamage() {
      PlayerMock a = this.server.addPlayer();
      PlayerMock b = this.server.addPlayer();
      assertFalse(this.call(hit(a, b, DamageCause.ENTITY_SWEEP_ATTACK, 1)).isCancelled());
      this.set(Feature.NO_SWEEP, true);
      assertTrue(this.call(hit(a, b, DamageCause.ENTITY_SWEEP_ATTACK, 1)).isCancelled());
      assertFalse(this.call(hit(a, b, DamageCause.ENTITY_ATTACK, 1)).isCancelled());
   }

   @Test
   void noPearlDamageOnlyForThePearlLanding() {
      this.set(Feature.NO_PEARL_DAMAGE, true);
      this.set(Feature.PEARL_ANTI_GLITCH, false); // MockBukkit can't check block shapes
      PlayerMock player = this.server.addPlayer();
      Location from = new Location(this.world, 0, 64, 0);
      Location to = new Location(this.world, 5, 64, 0);
      this.call(new PlayerTeleportEvent(player, from, to, PlayerTeleportEvent.TeleportCause.ENDER_PEARL));
      assertTrue(this.call(new EntityDamageEvent(player, DamageCause.FALL, DamageSource.builder(DamageType.FALL).build(), 5.0)).isCancelled());
      this.ticks(5);
      assertFalse(this.call(new EntityDamageEvent(player, DamageCause.FALL, DamageSource.builder(DamageType.FALL).build(), 5.0)).isCancelled());
   }

   @Test
   void hitDelayIsAppliedAndRestored() {
      PlayerMock player = this.server.addPlayer();
      this.set(Feature.HIT_DELAY, 10);
      assertEquals(10, player.getMaximumNoDamageTicks());
      this.set(Feature.HIT_DELAY, false);
      assertEquals(20, player.getMaximumNoDamageTicks());
   }
}
