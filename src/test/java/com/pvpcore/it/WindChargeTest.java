package com.pvpcore.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.destroystokyo.paper.event.player.PlayerLaunchProjectileEvent;
import com.pvpcore.Feature;
import java.util.UUID;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.entity.WindChargeMock;

/** Wind Charge Stop, driven by the player's own movement the way a real server sees it. */
class WindChargeTest extends PluginTest {
   PlayerMock falling(double dx, double dy, float pitch) {
      PlayerMock player = this.server.addPlayer();
      player.setGameMode(GameMode.SURVIVAL);
      player.teleport(new Location(this.world, 0.5, 120.0, 0.5, 0.0F, pitch));
      player.simulatePlayerMove(new Location(this.world, 0.5 + dx, 120.0 + dy, 0.5, 0.0F, pitch));
      player.setFallDistance(12.0F);
      player.getInventory().setItemInMainHand(new ItemStack(Material.WIND_CHARGE, 4));
      return player;
   }

   PlayerLaunchProjectileEvent use(PlayerMock player) {
      return this.call(new PlayerLaunchProjectileEvent(player, new ItemStack(Material.WIND_CHARGE), new WindChargeMock(this.server, UUID.randomUUID())));
   }

   @Test
   void aWindChargeStopsAFallLookingDown() {
      this.set(Feature.WIND_STOP, true);
      PlayerMock player = this.falling(0.4, -0.9, 80.0F);
      assertTrue(this.use(player).isCancelled(), "used for the stop, not thrown");
      assertEquals(3, player.getInventory().getItemInMainHand().getAmount(), "one charge used up");
      assertEquals(0.1, player.getVelocity().getY(), 1.0E-9, "the lift");
      assertEquals(0.2, player.getVelocity().getX(), 1.0E-9, "half the sideways speed kept");
      assertEquals(0.0F, player.getFallDistance(), "the fall is forgiven");
      assertTrue(player.hasCooldown(Material.WIND_CHARGE));

      // Still on cooldown a moment later: a normal throw.
      player.simulatePlayerMove(new Location(this.world, 1.0, 118.0, 0.5, 0.0F, 80.0F));
      assertFalse(this.use(player).isCancelled());
   }

   @Test
   void noStopWithoutAFallOrLookingAhead() {
      this.set(Feature.WIND_STOP, true);
      assertFalse(this.use(this.falling(0.4, 0.0, 80.0F)).isCancelled(), "not falling");
      assertFalse(this.use(this.falling(0.0, -0.9, 10.0F)).isCancelled(), "looking ahead");
   }

   @Test
   void offByDefault() {
      assertFalse(this.use(this.falling(0.0, -0.9, 80.0F)).isCancelled());
   }
}
