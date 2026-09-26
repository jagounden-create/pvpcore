package com.pvpcore.it;

import com.pvpcore.Feature;
import com.pvpcore.PvPCore;
import com.pvpcore.menu.Menu;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.Entity;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.InventoryView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

/** A simulated Paper server with the plugin enabled. */
abstract class PluginTest {
   ServerMock server;
   PvPCore plugin;
   WorldMock world;

   @BeforeEach
   void startServer() {
      this.server = MockBukkit.mock();
      this.world = this.server.addSimpleWorld("world");
      this.plugin = MockBukkit.load(PvPCore.class);
      // MockBukkit has no scoreboard number formats (Paper 1.20.3+ API), which the below-name health uses.
      this.set(Feature.HEALTH_BELOW_NAME, false);
   }

   @AfterEach
   void stopServer() {
      MockBukkit.unmock();
   }

   PlayerMock admin() {
      PlayerMock player = this.server.addPlayer();
      player.setOp(true);
      return player;
   }

   void ticks(int ticks) {
      this.server.getScheduler().performTicks(ticks);
   }

   <T extends Event> T call(T event) {
      this.server.getPluginManager().callEvent(event);
      return event;
   }

   void set(Feature feature, boolean on) {
      this.plugin.settings().set(feature, on);
      this.plugin.applyAll();
   }

   void set(Feature feature, double value) {
      this.plugin.settings().set(feature, true);
      this.plugin.settings().setValue(feature, value);
      this.plugin.applyAll();
   }

   Menu menu(PlayerMock player) {
      return this.plugin.menus().find(player.getOpenInventory().getTopInventory());
   }

   /** Clicks a slot of the open menu (or, past its size, the player's own inventory) and lets the next tick run. */
   void click(PlayerMock player, int rawSlot, ClickType type) {
      InventoryView view = player.getOpenInventory();
      player.simulateInventoryClick(view, type, rawSlot);
      this.ticks(1);
   }

   static EntityDamageByEntityEvent hit(Entity attacker, Entity victim, DamageCause cause, double damage) {
      DamageSource source = DamageSource.builder(DamageType.PLAYER_ATTACK).withCausingEntity(attacker).withDirectEntity(attacker).build();
      return new EntityDamageByEntityEvent(attacker, victim, cause, source, damage);
   }
}
