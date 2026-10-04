package com.pvpcore.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.pvpcore.Feature;
import com.pvpcore.rules.Rule;
import java.util.List;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.event.Event.Result;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class RulesGameplayTest extends PluginTest {
   @Test
   void consumableCooldown() {
      this.plugin.rules().put(Rule.empty(Material.GOLDEN_APPLE).withCooldown(10));
      PlayerMock player = this.server.addPlayer();
      ItemStack apple = new ItemStack(Material.GOLDEN_APPLE);
      assertFalse(this.call(new PlayerItemConsumeEvent(player, apple, EquipmentSlot.HAND)).isCancelled());
      this.ticks(1);
      assertTrue(player.hasCooldown(Material.GOLDEN_APPLE), "the cooldown shows on the item");
      assertTrue(this.call(new PlayerItemConsumeEvent(player, apple, EquipmentSlot.HAND)).isCancelled(), "second apple blocked");
      PlayerInteractEvent use = this.call(new PlayerInteractEvent(player, Action.RIGHT_CLICK_AIR, apple, null, BlockFace.SELF, EquipmentSlot.HAND));
      assertEquals(Result.DENY, use.useItemInHand(), "can't even start eating");
      this.ticks(10 * 20);
      assertFalse(this.call(new PlayerItemConsumeEvent(player, apple, EquipmentSlot.HAND)).isCancelled(), "ready again");
   }

   @Test
   void cooldownsSurviveRelogging() {
      this.plugin.rules().put(Rule.empty(Material.GOLDEN_APPLE).withCooldown(30));
      PlayerMock player = this.server.addPlayer();
      this.call(new PlayerItemConsumeEvent(player, new ItemStack(Material.GOLDEN_APPLE), EquipmentSlot.HAND));
      this.ticks(1);
      player.disconnect();
      player.setCooldown(Material.GOLDEN_APPLE, 0);
      player.reconnect();
      this.ticks(1);
      assertTrue(this.call(new PlayerItemConsumeEvent(player, new ItemStack(Material.GOLDEN_APPLE), EquipmentSlot.HAND)).isCancelled());
      assertTrue(player.hasCooldown(Material.GOLDEN_APPLE), "shown again after rejoining");
   }

   @Test
   void disabledItemsCantBeUsed() {
      this.plugin.rules().put(Rule.empty(Material.ENDER_PEARL).withDisabled(true));
      PlayerMock player = this.server.addPlayer();
      PlayerInteractEvent use = this.call(new PlayerInteractEvent(player, Action.RIGHT_CLICK_AIR, new ItemStack(Material.ENDER_PEARL), null, BlockFace.SELF, EquipmentSlot.HAND));
      assertEquals(Result.DENY, use.useItemInHand());
   }

   @Test
   void rulesOnlyApplyInTheirWorlds() {
      this.plugin.rules().put(Rule.empty(Material.ENDER_PEARL).withDisabled(true).withWorlds(List.of("arena")));
      PlayerMock player = this.server.addPlayer();
      PlayerInteractEvent use = this.call(new PlayerInteractEvent(player, Action.RIGHT_CLICK_AIR, new ItemStack(Material.ENDER_PEARL), null, BlockFace.SELF, EquipmentSlot.HAND));
      assertEquals(Result.DEFAULT, use.useItemInHand());
   }

   @Test
   void creativeAndBypassAreExempt() {
      this.plugin.rules().put(Rule.empty(Material.ENDER_PEARL).withDisabled(true));
      PlayerMock creative = this.server.addPlayer();
      creative.setGameMode(GameMode.CREATIVE);
      PlayerInteractEvent use = this.call(new PlayerInteractEvent(creative, Action.RIGHT_CLICK_AIR, new ItemStack(Material.ENDER_PEARL), null, BlockFace.SELF, EquipmentSlot.HAND));
      assertEquals(Result.DEFAULT, use.useItemInHand());
      PlayerMock bypass = this.server.addPlayer();
      bypass.addAttachment(this.plugin, "pvpcore.bypass.rules", true);
      use = this.call(new PlayerInteractEvent(bypass, Action.RIGHT_CLICK_AIR, new ItemStack(Material.ENDER_PEARL), null, BlockFace.SELF, EquipmentSlot.HAND));
      assertEquals(Result.DEFAULT, use.useItemInHand());
   }

   @Test
   void theMasterSwitchTurnsEveryRuleOff() {
      this.plugin.rules().put(Rule.empty(Material.ENDER_PEARL).withDisabled(true));
      this.set(Feature.ITEM_RULES, false);
      PlayerMock player = this.server.addPlayer();
      PlayerInteractEvent use = this.call(new PlayerInteractEvent(player, Action.RIGHT_CLICK_AIR, new ItemStack(Material.ENDER_PEARL), null, BlockFace.SELF, EquipmentSlot.HAND));
      assertEquals(Result.DEFAULT, use.useItemInHand());
   }

   @Test
   void meleeNerfAndCap() {
      this.plugin.rules().put(Rule.empty(Material.NETHERITE_SWORD).withDamage(0.5));
      this.plugin.rules().put(Rule.empty(Material.MACE).withMaxDamage(5));
      PlayerMock attacker = this.server.addPlayer();
      PlayerMock victim = this.server.addPlayer();
      attacker.getInventory().setItemInMainHand(new ItemStack(Material.NETHERITE_SWORD));
      assertEquals(4.0, this.call(hit(attacker, victim, DamageCause.ENTITY_ATTACK, 8.0)).getDamage(), 1.0E-9);
      attacker.getInventory().setItemInMainHand(new ItemStack(Material.MACE));
      assertEquals(10.0, this.call(hit(attacker, victim, DamageCause.ENTITY_ATTACK, 40.0)).getDamage(), 1.0E-9);
   }

   @Test
   void weaponCooldownBlocksTheNextHitButNotTheSweepOfTheSameSwing() {
      this.plugin.rules().put(Rule.empty(Material.DIAMOND_SWORD).withCooldown(2));
      PlayerMock attacker = this.server.addPlayer();
      PlayerMock victim = this.server.addPlayer();
      PlayerMock bystander = this.server.addPlayer();
      attacker.getInventory().setItemInMainHand(new ItemStack(Material.DIAMOND_SWORD));
      assertFalse(this.call(hit(attacker, victim, DamageCause.ENTITY_ATTACK, 7)).isCancelled());
      assertFalse(this.call(hit(attacker, bystander, DamageCause.ENTITY_SWEEP_ATTACK, 1)).isCancelled(), "the same swing's sweep still lands");
      assertTrue(this.call(hit(attacker, victim, DamageCause.ENTITY_ATTACK, 7)).isCancelled(), "next hit waits for the cooldown");
      this.ticks(41);
      assertFalse(this.call(hit(attacker, victim, DamageCause.ENTITY_ATTACK, 7)).isCancelled());
   }

   @Test
   void hittingMobsDoesNotStartAPvPCooldown() {
      this.plugin.rules().put(Rule.empty(Material.DIAMOND_SWORD).withCooldown(2));
      PlayerMock attacker = this.server.addPlayer();
      PlayerMock victim = this.server.addPlayer();
      attacker.getInventory().setItemInMainHand(new ItemStack(Material.DIAMOND_SWORD));
      org.bukkit.entity.Zombie zombie = this.world.spawn(new Location(this.world, 0, 64, 0), org.bukkit.entity.Zombie.class);
      this.call(hit(attacker, zombie, DamageCause.ENTITY_ATTACK, 7));
      assertFalse(this.call(hit(attacker, victim, DamageCause.ENTITY_ATTACK, 7)).isCancelled());
   }

   @Test
   void playerBlockLimit() {
      this.plugin.rules().put(Rule.empty(Material.COBWEB).withPlayerLimit(2));
      PlayerMock player = this.server.addPlayer();
      player.getInventory().setItemInMainHand(new ItemStack(Material.COBWEB, 64));
      assertFalse(this.place(player, 0).isCancelled());
      assertFalse(this.place(player, 1).isCancelled());
      assertTrue(this.place(player, 2).isCancelled(), "third web refused");
      this.world.getBlockAt(0, 70, 0).setType(Material.AIR);
      assertFalse(this.place(player, 3).isCancelled(), "breaking one frees a slot");
   }

   BlockPlaceEvent place(PlayerMock player, int x) {
      Location at = new Location(this.world, x, 70, 0);
      BlockPlaceEvent event = player.simulateBlockPlace(Material.COBWEB, at);
      if (event.isCancelled()) {
         at.getBlock().setType(Material.AIR);
      }

      return event;
   }
}
