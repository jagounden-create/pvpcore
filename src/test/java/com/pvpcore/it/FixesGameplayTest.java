package com.pvpcore.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.pvpcore.Feature;
import com.pvpcore.module.GhostModule;
import com.pvpcore.util.Attributes;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.inventory.EquipmentSlot;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

/** Ghost fixes, pearl anti-glitch and the built-in attribute swapping, on a simulated server. */
class FixesGameplayTest extends PluginTest {
   PlayerTeleportEvent pearlInto(PlayerMock player, WorldMock other) {
      return this.call(new PlayerTeleportEvent(player, new Location(this.world, 0, 64, 0), new Location(other, 0, 64, 0), TeleportCause.ENDER_PEARL));
   }

   @Test
   void aPearlIntoAnotherWorldIsStoppedAndGivenBack() {
      WorldMock nether = this.server.addSimpleWorld("world_nether");
      PlayerMock player = this.server.addPlayer();
      player.setGameMode(GameMode.SURVIVAL);
      player.getInventory().clear();
      assertTrue(this.pearlInto(player, nether).isCancelled());
      assertTrue(player.getInventory().contains(Material.ENDER_PEARL, 1), "pearl refunded");

      player.getInventory().clear();
      player.setGameMode(GameMode.CREATIVE);
      assertTrue(this.pearlInto(player, nether).isCancelled());
      assertFalse(player.getInventory().contains(Material.ENDER_PEARL), "nothing to refund in creative");
   }

   @Test
   void antiGlitchCanBeTurnedOff() {
      WorldMock nether = this.server.addSimpleWorld("world_nether");
      PlayerMock player = this.server.addPlayer();
      this.set(Feature.PEARL_ANTI_GLITCH, false);
      assertFalse(this.pearlInto(player, nether).isCancelled());
   }

   @Test
   void aTotemPopReShowsThePlayerToEveryoneNearby() {
      PlayerMock popped = this.server.addPlayer();
      PlayerMock watcher = this.server.addPlayer();
      popped.teleport(new Location(this.world, 0, 64, 0));
      watcher.teleport(new Location(this.world, 5, 64, 0));
      this.ticks(10); // joins settle
      GhostModule ghosts = this.plugin.ghosts();
      long before = ghosts.count(GhostModule.Reason.TOTEM);
      this.call(new EntityResurrectEvent(popped, EquipmentSlot.OFF_HAND));
      this.ticks(2);
      assertEquals(before + 1, ghosts.count(GhostModule.Reason.TOTEM));
      assertTrue(watcher.canSee(popped), "still visible afterwards");
   }

   @Test
   void hiddenPlayersAreNeverReShown() {
      PlayerMock vanished = this.server.addPlayer();
      PlayerMock watcher = this.server.addPlayer();
      watcher.hidePlayer(this.plugin, vanished);
      this.ticks(10);
      this.plugin.ghosts().fix(vanished, GhostModule.Reason.COMMAND);
      assertFalse(watcher.canSee(vanished), "a vanish stays a vanish");
   }

   @Test
   void fixCommand() {
      PlayerMock admin = this.admin();
      PlayerMock other = this.server.addPlayer();
      admin.teleport(new Location(this.world, 0, 64, 0));
      other.teleport(new Location(this.world, 3, 64, 0));
      this.ticks(10);
      admin.performCommand("pvpcore fix " + other.getName());
      assertTrue(this.plugin.ghosts().count(GhostModule.Reason.COMMAND) >= 1);
      assertTrue(admin.canSee(other));
   }

   @Test
   void swapHitsKeepTheDamageOfTheItemSwappedFrom() {
      Attribute damage = Attributes.attackDamage();
      PlayerMock player = this.server.addPlayer();
      PlayerMock target = this.server.addPlayer();
      if (damage != null && player.getAttribute(damage) == null) {
         try {
            player.registerAttribute(damage); // players have it on a real server
         } catch (RuntimeException ignored) {
            // the assumption below skips the test
         }
      }

      AttributeInstance instance = damage == null ? null : player.getAttribute(damage);
      org.junit.jupiter.api.Assumptions.assumeTrue(instance != null, "MockBukkit has no attack damage attribute here");
      assertTrue(this.plugin.combat().emulatingSwaps(), "no Paper switch in the test server, so PvPCore does it");

      instance.setBaseValue(10.0); // holding an axe
      this.call(new PlayerItemHeldEvent(player, 0, 1));
      instance.setBaseValue(7.0); // now the sword
      this.call(new PrePlayerAttackEntityEvent(player, target, true));
      assertEquals(10.0, instance.getValue(), 1.0E-9, "the hit in the swap tick uses the axe");
      this.ticks(1);
      assertEquals(7.0, instance.getValue(), 1.0E-9, "and only that hit");

      // a hit a tick after the swap is a normal sword hit
      this.call(new PlayerItemHeldEvent(player, 1, 0));
      this.ticks(1);
      this.call(new PrePlayerAttackEntityEvent(player, target, true));
      assertEquals(7.0, instance.getValue(), 1.0E-9);
   }
}
