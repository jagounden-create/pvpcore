package com.pvpcore.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.pvpcore.Feature;
import com.pvpcore.rules.Rule;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class CommandTest extends PluginTest {
   @Test
   void toggleAndSet() {
      PlayerMock admin = this.admin();
      admin.performCommand("pvpcore toggle shield-stun off");
      assertFalse(this.plugin.settings().on(Feature.SHIELD_STUN));
      admin.performCommand("pvpcore toggle shield-stun");
      assertTrue(this.plugin.settings().on(Feature.SHIELD_STUN));
      admin.performCommand("pvpcore set shield-delay 5000");
      assertEquals(250.0, this.plugin.settings().value(Feature.SHIELD_DELAY), "clamped to the allowed range");
      admin.performCommand("pvpcore set shield-delay nope");
      assertEquals(250.0, this.plugin.settings().value(Feature.SHIELD_DELAY));
      admin.performCommand("pvpcore set shield-delay NaN");
      assertEquals(250.0, this.plugin.settings().value(Feature.SHIELD_DELAY));
   }

   @Test
   void presets() {
      PlayerMock admin = this.admin();
      admin.performCommand("pvpcore preset vanilla");
      assertEquals(0, this.plugin.settings().enabledCount());
      admin.performCommand("pvpcore preset sword");
      assertTrue(this.plugin.settings().on(Feature.NO_SWEEP));
      assertFalse(this.plugin.settings().on(Feature.KNOCKBACK_DISTANCE));
      assertEquals(0.4, this.plugin.settings().value(Feature.VERTICAL_KNOCKBACK), 1.0E-9);
      admin.performCommand("pvpcore preset cart");
      assertTrue(this.plugin.settings().on(Feature.CART_TERRAIN));
      assertFalse(this.plugin.settings().on(Feature.NO_SWEEP), "presets start from the defaults");
   }

   @Test
   void rules() {
      PlayerMock admin = this.admin();
      admin.performCommand("pvpcore rule ender_pearl cooldown 15");
      admin.performCommand("pvpcore rule minecraft:netherite_sword damage 0.9");
      admin.performCommand("pvpcore rule cobweb player-limit 8");
      admin.performCommand("pvpcore rule end_crystal disabled true");
      admin.performCommand("pvpcore rule end_crystal worlds arena, ffa");
      assertEquals(15.0, this.plugin.rules().get(Material.ENDER_PEARL).cooldown());
      assertEquals(0.9, this.plugin.rules().get(Material.NETHERITE_SWORD).damage());
      assertEquals(8, this.plugin.rules().get(Material.COBWEB).playerLimit());
      Rule crystal = this.plugin.rules().get(Material.END_CRYSTAL);
      assertTrue(crystal.disabled());
      assertEquals(List.of("arena", "ffa"), crystal.worlds());
      admin.performCommand("pvpcore rule ender_pearl cooldown -3");
      assertEquals(15.0, this.plugin.rules().get(Material.ENDER_PEARL).cooldown(), "negative numbers refused");
      admin.performCommand("pvpcore rule not_an_item cooldown 3");
      admin.performCommand("pvpcore rule ender_pearl remove");
      assertNull(this.plugin.rules().get(Material.ENDER_PEARL));
   }

   @Test
   void tabCompletion() {
      PlayerMock admin = this.admin();
      Command command = this.plugin.getCommand("pvpcore");
      assertNotNull(command);
      List<String> first = command.tabComplete(admin, "pvpcore", new String[]{"t"});
      assertEquals(List.of("toggle"), first);
      assertTrue(command.tabComplete(admin, "pvpcore", new String[]{"toggle", "cart-"}).contains("cart-hit-reg"));
      assertTrue(command.tabComplete(admin, "pvpcore", new String[]{"set", "sh"}).contains("shield-delay"));
      assertFalse(command.tabComplete(admin, "pvpcore", new String[]{"set", "sh"}).contains("shield-usage"), "set lists numbers only");
      assertTrue(command.tabComplete(admin, "pvpcore", new String[]{"rule", "ender"}).contains("ender_pearl"));
      assertTrue(command.tabComplete(admin, "pvpcore", new String[]{"rule", "ender_pearl", "c"}).contains("cooldown"));
      PlayerMock player = this.server.addPlayer();
      assertTrue(command.tabComplete(player, "pvpcore", new String[]{""}).isEmpty(), "nothing for non-admins");
   }

   @Test
   void statusAndReloadWorkFromConsole() {
      assertTrue(this.server.dispatchCommand(this.server.getConsoleSender(), "pvpcore status"));
      assertTrue(this.server.dispatchCommand(this.server.getConsoleSender(), "pvpcore reload"));
      assertTrue(this.server.dispatchCommand(this.server.getConsoleSender(), "pvpcore rules"));
      assertTrue(this.server.dispatchCommand(this.server.getConsoleSender(), "pvpcore"));
   }
}
