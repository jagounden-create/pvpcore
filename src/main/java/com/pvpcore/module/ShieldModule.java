package com.pvpcore.module;

import com.pvpcore.Feature;
import com.pvpcore.Module;
import com.pvpcore.PvPCore;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.BlocksAttacks;
import io.papermc.paper.datacomponent.item.Weapon;
import io.papermc.paper.datacomponent.item.BlocksAttacks.Builder;
import io.papermc.paper.datacomponent.item.blocksattacks.DamageReduction;
import io.papermc.paper.event.player.PlayerShieldDisableEvent;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Event.Result;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

public final class ShieldModule extends Module {
   static final double VANILLA_STUN_SECONDS = 5.0;
   static final float DEFAULT_BLOCK_ANGLE = 90.0F;
   private BukkitTask sweep;
   private final Map<UUID, Integer> heldImmunity = new ConcurrentHashMap<>();

   public ShieldModule(PvPCore plugin) {
      super(plugin);
   }

   @Override
   public void start() {
      this.sweep = Bukkit.getScheduler().runTaskTimer(this.plugin, this::sweepHands, 20L, 10L);
   }

   @Override
   public void apply() {
      this.plugin.tweaks().shieldStun(this.on(Feature.SHIELD_STUN));
      if (this.on(Feature.SHIELD_DELAY)) {
         this.sweepHands();
      } else {
         this.revertEveryone();
      }
   }

   @Override
   public void stop() {
      if (this.sweep != null) {
         this.sweep.cancel();
      }

      this.revertEveryone();

      for (Entry<UUID, Integer> held : this.heldImmunity.entrySet()) {
         Player player = Bukkit.getPlayer(held.getKey());
         if (player != null && player.getMaximumNoDamageTicks() == 0) {
            player.setMaximumNoDamageTicks(held.getValue());
         }
      }

      this.heldImmunity.clear();
   }

   @EventHandler(priority = EventPriority.LOWEST)
   public void onRaise(PlayerInteractEvent event) {
      if (event.getAction().isRightClick() && event.getHand() != null && blocks(event.getItem())) {
         if (!this.on(Feature.SHIELD_USAGE)) {
            event.setUseItemInHand(Result.DENY);
         } else {
            if (this.on(Feature.SHIELD_DELAY) && !event.getPlayer().isHandRaised()) {
               this.patchSlot(event.getPlayer(), event.getHand());
            }
         }
      }
   }

   @EventHandler(priority = EventPriority.MONITOR)
   public void onJoin(PlayerJoinEvent event) {
      if (this.on(Feature.SHIELD_DELAY)) {
         this.patchSlot(event.getPlayer(), EquipmentSlot.HAND);
         this.patchSlot(event.getPlayer(), EquipmentSlot.OFF_HAND);
      }
   }

   @EventHandler
   public void onQuit(PlayerQuitEvent event) {
      Integer held = this.heldImmunity.remove(event.getPlayer().getUniqueId());
      if (held != null && event.getPlayer().getMaximumNoDamageTicks() == 0) {
         event.getPlayer().setMaximumNoDamageTicks(held);
      }
   }

   private void sweepHands() {
      if (this.on(Feature.SHIELD_DELAY)) {
         for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.isHandRaised()) {
               this.patchSlot(player, EquipmentSlot.HAND);
               this.patchSlot(player, EquipmentSlot.OFF_HAND);
            }
         }
      }
   }

   private float wantedDelaySeconds() {
      return (float)(this.settings().value(Feature.SHIELD_DELAY) / 1000.0);
   }

   private void patchSlot(Player player, EquipmentSlot slot) {
      PlayerInventory inventory = player.getInventory();
      ItemStack item = inventory.getItem(slot);
      float wanted = this.wantedDelaySeconds();
      if (needsPatch(item, wanted)) {
         patch(item, wanted);
         if (needsPatch(inventory.getItem(slot), wanted)) {
            inventory.setItem(slot, item);
         }
      }
   }

   private void revertEveryone() {
      for (Player player : Bukkit.getOnlinePlayers()) {
         PlayerInventory inventory = player.getInventory();

         for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack item = inventory.getItem(slot);
            if (patched(item)) {
               revert(item);
               if (patched(inventory.getItem(slot))) {
                  inventory.setItem(slot, item);
               }
            }
         }
      }
   }

   static boolean blocks(ItemStack item) {
      return item != null && !item.getType().isAir() && item.hasData(DataComponentTypes.BLOCKS_ATTACKS);
   }

   static boolean needsPatch(ItemStack item, float wantedSeconds) {
      if (!blocks(item)) {
         return false;
      }

      BlocksAttacks blocking = (BlocksAttacks)item.getData(DataComponentTypes.BLOCKS_ATTACKS);
      return blocking != null && Math.abs(blocking.blockDelaySeconds() - wantedSeconds) > 5.0E-4F;
   }

   static void patch(ItemStack item, float delaySeconds) {
      BlocksAttacks blocking = (BlocksAttacks)item.getData(DataComponentTypes.BLOCKS_ATTACKS);
      if (blocking != null) {
         item.setData(DataComponentTypes.BLOCKS_ATTACKS, copy(blocking, delaySeconds));
      }
   }

   static boolean patched(ItemStack item) {
      if (blocks(item) && item.isDataOverridden(DataComponentTypes.BLOCKS_ATTACKS)) {
         BlocksAttacks now = (BlocksAttacks)item.getData(DataComponentTypes.BLOCKS_ATTACKS);
         BlocksAttacks vanilla = defaultBlocking(item.getType());
         return now != null && vanilla != null && Math.abs(now.blockDelaySeconds() - vanilla.blockDelaySeconds()) > 5.0E-4F;
      } else {
         return false;
      }
   }

   static void revert(ItemStack item) {
      BlocksAttacks now = (BlocksAttacks)item.getData(DataComponentTypes.BLOCKS_ATTACKS);
      BlocksAttacks vanilla = defaultBlocking(item.getType());
      if (item.getType() != Material.SHIELD && now != null && vanilla != null) {
         item.setData(DataComponentTypes.BLOCKS_ATTACKS, copy(now, vanilla.blockDelaySeconds()));
      } else {
         item.resetData(DataComponentTypes.BLOCKS_ATTACKS);
      }
   }

   private static BlocksAttacks defaultBlocking(Material material) {
      return (BlocksAttacks)material.getDefaultData(DataComponentTypes.BLOCKS_ATTACKS);
   }

   private static Builder copy(BlocksAttacks blocking, float delaySeconds) {
      Builder builder = BlocksAttacks.blocksAttacks()
         .blockDelaySeconds(delaySeconds)
         .disableCooldownScale(blocking.disableCooldownScale())
         .damageReductions(blocking.damageReductions())
         .itemDamage(blocking.itemDamage());
      copyBypassedBy(blocking, builder);

      if (blocking.blockSound() != null) {
         builder.blockSound(blocking.blockSound());
      }

      if (blocking.disableSound() != null) {
         builder.disableSound(blocking.disableSound());
      }

      return builder;
   }

   private static volatile Method bypassGetter;
   private static volatile Method bypassSetter;

   /**
    * bypassedBy is a TagKey up to 1.21.11 and a RegistryKeySet from 26.1 on, so it is copied by reflection; calling
    * either signature directly would fail on the other versions.
    */
   private static void copyBypassedBy(BlocksAttacks from, Builder to) {
      try {
         if (bypassGetter == null) {
            Method getter = BlocksAttacks.class.getMethod("bypassedBy");
            bypassSetter = Builder.class.getMethod("bypassedBy", getter.getReturnType());
            bypassGetter = getter;
         }

         Object value = bypassGetter.invoke(from);
         if (value != null) {
            bypassSetter.invoke(to, value);
         }
      } catch (ReflectiveOperationException | RuntimeException e) {
         // leave the item's default bypass
      }
   }

   @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
   public void onAxeHit(EntityDamageByEntityEvent event) {
      if (this.on(Feature.SHIELD_STUN)
         && this.on(Feature.SHIELD_USAGE)
         && event.getCause() == DamageCause.ENTITY_ATTACK
         && event.getEntity() instanceof Player victim
         && event.getDamager() instanceof Player attacker
         && !attacker.equals(victim)) {
         if (victim.isHandRaised()) {
            boolean wasBlocking = victim.isBlocking();
            ItemStack shield = victim.getActiveItem();
            if (blocks(shield) && !victim.hasCooldown(shield)) {
               float stunSeconds = disableSeconds(attacker);
               if (!(stunSeconds <= 0.0F)) {
                  BlocksAttacks blocking = (BlocksAttacks)shield.getData(DataComponentTypes.BLOCKS_ATTACKS);
                  if (blocking != null && facing(victim, attacker.getLocation(), blockAngle(blocking))) {
                     int ticks = Math.max(1, Math.round(stunSeconds * 20.0F * blocking.disableCooldownScale()));
                     PlayerShieldDisableEvent stun = new PlayerShieldDisableEvent(victim, attacker, ticks);
                     if (stun.callEvent()) {
                        if (!wasBlocking) {
                           event.setCancelled(true);
                        }

                        victim.setCooldown(shield, stun.getCooldown());
                        victim.clearActiveItem();
                        victim.getWorld()
                           .playSound(victim, Sound.ITEM_SHIELD_BREAK, SoundCategory.PLAYERS, 0.8F, 0.8F + ThreadLocalRandom.current().nextFloat() * 0.4F);
                     }
                  }
               }
            }
         }
      }
   }

   static float disableSeconds(Player attacker) {
      ItemStack weapon = attacker.getInventory().getItemInMainHand();
      if (weapon.getType().isAir()) {
         return 0.0F;
      }

      Weapon data = (Weapon)weapon.getData(DataComponentTypes.WEAPON);
      return data == null ? 0.0F : data.disableBlockingForSeconds();
   }

   private static float blockAngle(BlocksAttacks blocking) {
      float angle = 0.0F;

      for (DamageReduction reduction : blocking.damageReductions()) {
         angle = Math.max(angle, reduction.horizontalBlockingAngle());
      }

      return angle > 0.0F ? angle : 90.0F;
   }

   static boolean facing(Player victim, Location source, float angleDegrees) {
      Vector look = victim.getLocation().getDirection().setY(0);
      Vector toSource = source.toVector().subtract(victim.getLocation().toVector()).setY(0);
      if (!(look.lengthSquared() < 1.0E-8) && !(toSource.lengthSquared() < 1.0E-8)) {
         double angle = Math.toDegrees(look.normalize().angle(toSource.normalize()));
         return angle <= angleDegrees;
      } else {
         return true;
      }
   }

   @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
   public void onStun(PlayerShieldDisableEvent event) {
      if (!this.on(Feature.SHIELD_STUN)) {
         event.setCancelled(true);
      } else {
         double seconds = this.settings().value(Feature.SHIELD_STUN);
         if (Math.abs(seconds - 5.0) > 1.0E-6) {
            int ticks = (int)Math.round(event.getCooldown() * seconds / 5.0);
            event.setCooldown(Math.max(1, ticks));
         }
      }
   }

   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onStunned(PlayerShieldDisableEvent event) {
      Player victim = event.getPlayer();
      if (this.plugin.tweaks().shieldStunAvailable() && this.plugin.tweaks().shieldStunActive()) {
         this.playBreakSound(event);
      } else {
         victim.setNoDamageTicks(0);
         int max = victim.getMaximumNoDamageTicks();
         if (max != 0) {
            this.heldImmunity.putIfAbsent(victim.getUniqueId(), max);
            victim.setMaximumNoDamageTicks(0);
            Bukkit.getScheduler().runTask(this.plugin, () -> {
               Integer held = this.heldImmunity.remove(victim.getUniqueId());
               if (held != null && victim.getMaximumNoDamageTicks() == 0) {
                  victim.setMaximumNoDamageTicks(held);
               }
            });
         }

         this.playBreakSound(event);
      }
   }

   private void playBreakSound(PlayerShieldDisableEvent event) {
      if (this.on(Feature.BREAK_SOUND)) {
         Player victim = event.getPlayer();
         float pitch = 0.8F + ThreadLocalRandom.current().nextFloat() * 0.4F;
         victim.playSound(victim, Sound.ITEM_SHIELD_BREAK, SoundCategory.PLAYERS, 1.0F, pitch);
         if (event.getDamager() instanceof Player attacker && !attacker.equals(victim)) {
            attacker.playSound(attacker, Sound.ITEM_SHIELD_BREAK, SoundCategory.PLAYERS, 1.0F, pitch);
         }
      }
   }
}
