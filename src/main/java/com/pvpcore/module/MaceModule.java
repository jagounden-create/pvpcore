package com.pvpcore.module;

import com.pvpcore.Compat;
import com.pvpcore.Feature;
import com.pvpcore.Module;
import com.pvpcore.PvPCore;
import com.pvpcore.Settings;
import com.pvpcore.util.Hits;
import com.pvpcore.util.Text;
import com.destroystokyo.paper.event.player.PlayerLaunchProjectileEvent;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.WindCharge;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/**
 * Mace balance and fairness, and wind charge tech. Damage changes only apply to hits on players, so mob farms and
 * bosses stay vanilla.
 * <p>
 * Wind charge stop: using a wind charge while falling and looking down stops the fall in mid-air instead of throwing
 * it. Instant wind jump: a wind charge thrown down at the ground just below bursts there at once, so the jump doesn't
 * wait for the charge to fly down and back over the network - it feels the same at any ping.
 */
public final class MaceModule extends Module {
   /** Vanilla's smash threshold: a mace hit smashes after falling more than this. */
   static final double SMASH_MIN_FALL = 1.5;
   /** How long after a smash attempt its fall is still covered. */
   static final int ATTEMPT_WINDOW_TICKS = 40;
   /** Falling this far after the attempt would not have hurt anyway (vanilla's safe fall distance, plus slack). */
   static final double SAFE_EXTRA_FALL = 3.5;
   private final Map<UUID, Attempt> attempts = new ConcurrentHashMap<>();
   private final Map<UUID, Integer> smashReady = new ConcurrentHashMap<>();
   private final Set<UUID> glided = ConcurrentHashMap.newKeySet();
   private final Map<UUID, Integer> windStopReady = new ConcurrentHashMap<>();
   /**
    * Each player's latest movement, {dx, dy, dz, tick}, from their own movement packets. The server's velocity for a
    * player is not how they are really moving (their game moves them), so wind charge stops go by this instead.
    */
   private final Map<UUID, double[]> moves = new HashMap<>();
   /** A movement older than this many ticks says nothing about how the player is moving now. */
   static final int MOVE_FRESH_TICKS = 3;
   private final Enchantment density = density();

   public MaceModule(PvPCore plugin) {
      super(plugin);
   }

   /** Registered only when Paper's pre-attack event exists; it also sees hits that hit immunity swallows. */
   public Listener preAttackListener() {
      return new PreAttack();
   }

   @Override
   public void apply() {
      if (!this.on(Feature.SMASH_PROTECTION)) {
         this.attempts.clear();
      }

      if (!this.on(Feature.SMASH_COOLDOWN)) {
         this.smashReady.clear();
      }

      if (!this.on(Feature.NO_ELYTRA_SMASH)) {
         this.glided.clear();
      }

      if (!this.on(Feature.WIND_STOP)) {
         this.moves.clear();
      }
   }

   private static Enchantment density() {
      try {
         return Enchantment.DENSITY;
      } catch (LinkageError | RuntimeException e) {
         return null;
      }
   }

   static boolean isMace(ItemStack item) {
      return Compat.MACE && item != null && item.getType() == Material.MACE;
   }

   static boolean canSmash(Player player) {
      return player.getFallDistance() > SMASH_MIN_FALL && !player.isGliding();
   }

   /** The bonus vanilla adds to a smash: 4 per block for the first 3, 2 per block for the next 5, then 1, plus Density. */
   static double smashBonus(double fall, int densityLevel) {
      if (fall <= SMASH_MIN_FALL) {
         return 0.0;
      }

      double bonus;
      if (fall <= 3.0) {
         bonus = 4.0 * fall;
      } else if (fall <= 8.0) {
         bonus = 12.0 + 2.0 * (fall - 3.0);
      } else {
         bonus = 22.0 + fall - 8.0;
      }

      return bonus + 0.5 * Math.max(0, densityLevel) * fall;
   }

   /**
    * Whether a fall that ends now is the same fall a smash attempt was made in, and short enough to forgive: the fall
    * distance kept growing from the attempt, and grew by as much as the player actually dropped since.
    */
   static boolean covers(double fallAtAttempt, double yAtAttempt, double fallNow, double yNow, int ticksSince) {
      if (ticksSince < 0 || ticksSince > ATTEMPT_WINDOW_TICKS) {
         return false;
      }

      double extra = fallNow - fallAtAttempt;
      double dropped = yAtAttempt - yNow;
      return extra >= -0.01 && extra <= SAFE_EXTRA_FALL && Math.abs(extra - dropped) <= 1.0;
   }

   private int densityLevel(ItemStack weapon) {
      return this.density == null ? 0 : weapon.getEnchantmentLevel(this.density);
   }

   void attempt(Player attacker) {
      if (this.on(Feature.SMASH_PROTECTION) && isMace(attacker.getInventory().getItemInMainHand()) && canSmash(attacker)) {
         this.attempts.put(attacker.getUniqueId(), new Attempt(Bukkit.getCurrentTick(), attacker.getFallDistance(), attacker.getLocation().getY()));
      }
   }

   @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
   public void onMaceHit(EntityDamageByEntityEvent event) {
      // Smashes have their own damage type; servers don't all report it as a plain attack.
      if (!(event.getDamager() instanceof Player attacker) || !Hits.primary(event)) {
         return;
      }

      ItemStack weapon = attacker.getInventory().getItemInMainHand();
      if (!isMace(weapon)) {
         return;
      }

      if (!Compat.PRE_ATTACK_EVENT) {
         this.attempt(attacker);
      }

      if (!(event.getEntity() instanceof Player)) {
         return;
      }

      double damage = event.getDamage();
      boolean changed = false;
      if (canSmash(attacker)) {
         double bonus = Math.min(damage, smashBonus(attacker.getFallDistance(), this.densityLevel(weapon)));
         if (bonus > 0.0 && this.on(Feature.NO_ELYTRA_SMASH) && this.glided.contains(attacker.getUniqueId())) {
            damage -= bonus;
            bonus = 0.0;
            changed = true;
         }

         if (bonus > 0.0 && this.on(Feature.SMASH_COOLDOWN)) {
            int now = Bukkit.getCurrentTick();
            Integer ready = this.smashReady.get(attacker.getUniqueId());
            if (ready != null && now < ready) {
               damage -= bonus;
               bonus = 0.0;
               changed = true;
               this.plugin.actionBar(attacker, "smash-cooldown", Map.of("seconds", Text.trim(Math.ceil((ready - now) / 2.0) / 10.0)));
            } else {
               // Told by message, not an item cooldown: an item cooldown could stop the mace hitting at all.
               this.smashReady.put(attacker.getUniqueId(), now + (int)Math.round(this.settings().value(Feature.SMASH_COOLDOWN) * 20.0));
            }
         }

         if (bonus > 0.0 && this.on(Feature.SMASH_DAMAGE)) {
            damage += bonus * (this.settings().value(Feature.SMASH_DAMAGE) - 1.0);
            changed = true;
         }
      }

      if (this.on(Feature.SMASH_CAP)) {
         double cap = this.settings().value(Feature.SMASH_CAP) * 2.0;
         if (damage > cap) {
            damage = cap;
            changed = true;
         }
      }

      if (changed) {
         event.setDamage(Math.max(0.0, damage));
      }
   }

   @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
   public void onFall(EntityDamageEvent event) {
      if (event.getCause() == DamageCause.FALL && event.getEntity() instanceof Player player && this.on(Feature.SMASH_PROTECTION)) {
         Attempt attempt = this.attempts.remove(player.getUniqueId());
         if (attempt != null && covers(attempt.fall(), attempt.y(), player.getFallDistance(), player.getLocation().getY(), Bukkit.getCurrentTick() - attempt.tick())) {
            event.setCancelled(true);
         }
      }
   }

   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onWindCharge(ProjectileLaunchEvent event) {
      if (Compat.WIND_CHARGE && this.on(Feature.WIND_CHARGE_COOLDOWN) && event.getEntity() instanceof WindCharge && event.getEntity().getShooter() instanceof Player player) {
         int ticks = this.settings().ticks(Feature.WIND_CHARGE_COOLDOWN);
         // Vanilla sets its own 10 tick cooldown right after this event.
         Bukkit.getScheduler().runTask(this.plugin, () -> {
            if (player.isOnline()) {
               player.setCooldown(Material.WIND_CHARGE, Math.max(0, ticks - 1));
            }
         });
      }
   }

   // ------------------------------------------------------------------ wind charge tech

   @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
   public void onWindTech(PlayerLaunchProjectileEvent event) {
      if (!Compat.WIND_CHARGE || !(event.getProjectile() instanceof WindCharge) || !this.on(Feature.WIND_STOP) && !this.on(Feature.WIND_JUMP)) {
         return;
      }

      Player player = event.getPlayer();
      if (player.isGliding() || player.isFlying() || player.isInsideVehicle() || player.isRiptiding()) {
         return;
      }

      Settings.Tuning tuning = this.settings().tuning();
      float pitch = player.getLocation().getPitch();
      int now = Bukkit.getCurrentTick();
      Vector velocity = this.movement(player, now);
      if (this.on(Feature.WIND_STOP) && canStop(velocity.getY(), pitch, ((LivingEntity)player).isOnGround(), tuning)) {
         Integer ready = this.windStopReady.get(player.getUniqueId());
         if (ready == null || now >= ready) {
            event.setCancelled(true);
            this.useWindCharge(player, tuning.windStopConsume());
            player.setVelocity(stopVelocity(velocity, this.settings().value(Feature.WIND_STOP), tuning.windStopHorizontal()));
            player.setFallDistance(0.0F);
            // The player keeps falling until the new speed reaches them, one ping later; that bit isn't a fall either.
            Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
               if (player.isOnline() && !((LivingEntity)player).isOnGround()) {
                  player.setFallDistance(0.0F);
               }
            }, pingTicks(player));
            this.windStopReady.put(player.getUniqueId(), now + tuning.windStopCooldown());
            return;
         }
      }

      if (this.on(Feature.WIND_JUMP) && pitch >= tuning.windJumpMinPitch()) {
         Location ground = groundBelow(player, tuning.windJumpMaxHeight());
         if (ground != null) {
            event.setCancelled(true);
            this.useWindCharge(player, true);
            // Where a thrown charge would have burst: a quarter block off the ground, like vanilla.
            WindCharge charge = player.getWorld().spawn(ground.add(0.0, 0.25, 0.0), WindCharge.class, spawned -> spawned.setShooter(player));
            charge.explode();
         }
      }
   }

   /** How the player is moving, per tick, by their latest movement packets; still when there is no recent one. */
   private Vector movement(Player player, int now) {
      double[] move = this.moves.get(player.getUniqueId());
      return move == null || now - (int)move[3] > MOVE_FRESH_TICKS ? new Vector() : new Vector(move[0], move[1], move[2]);
   }

   /** Falling fast enough, looking far enough down, and not standing on anything. */
   static boolean canStop(double velocityY, float pitch, boolean onGround, Settings.Tuning tuning) {
      return !onGround && velocityY <= -tuning.windStopMinFall() && pitch >= tuning.windStopMinPitch();
   }

   /** What a stop leaves: the chosen upward speed, and a share of the sideways speed. */
   static Vector stopVelocity(Vector velocity, double lift, double keepHorizontal) {
      return new Vector(velocity.getX() * keepHorizontal, lift, velocity.getZ() * keepHorizontal);
   }

   /** The point on the ground straight below the player's feet, if it is within {@code maxHeight} blocks. */
   private static Location groundBelow(Player player, double maxHeight) {
      Location feet = player.getLocation();
      World world = feet.getWorld();
      if (world == null) {
         return null;
      }

      if (((LivingEntity)player).isOnGround()) {
         return feet;
      }

      RayTraceResult hit = world.rayTraceBlocks(feet, new Vector(0, -1, 0), maxHeight, FluidCollisionMode.NEVER, true);
      return hit == null ? null : hit.getHitPosition().toLocation(world);
   }

   /** About one round trip to the player, in ticks: 1 to 10. */
   static long pingTicks(Player player) {
      int ping;
      try {
         ping = player.getPing();
      } catch (RuntimeException | LinkageError e) {
         ping = 100;
      }

      return Math.max(1L, Math.min(10L, ping / 50L + 1L));
   }

   /** What vanilla does when a wind charge is thrown: one used up (outside creative) and the item cooldown. */
   private void useWindCharge(Player player, boolean consume) {
      if (consume && player.getGameMode() != GameMode.CREATIVE) {
         PlayerInventory inventory = player.getInventory();
         ItemStack main = inventory.getItemInMainHand();
         boolean inMain = main.getType() == Material.WIND_CHARGE;
         ItemStack held = inMain ? main : inventory.getItemInOffHand();
         if (held.getType() == Material.WIND_CHARGE) {
            held.setAmount(held.getAmount() - 1);
            if (inMain) {
               inventory.setItemInMainHand(held.getAmount() > 0 ? held : null);
            } else {
               inventory.setItemInOffHand(held.getAmount() > 0 ? held : null);
            }
         }
      }

      int cooldown = this.on(Feature.WIND_CHARGE_COOLDOWN) ? this.settings().ticks(Feature.WIND_CHARGE_COOLDOWN) : VANILLA_WIND_COOLDOWN;
      player.setCooldown(Material.WIND_CHARGE, cooldown);
      player.updateInventory();
      this.plugin.itemUsed(player, Material.WIND_CHARGE);
   }

   static final int VANILLA_WIND_COOLDOWN = 10;

   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onGlide(EntityToggleGlideEvent event) {
      if (event.isGliding() && event.getEntity() instanceof Player player && this.on(Feature.NO_ELYTRA_SMASH)) {
         this.glided.add(player.getUniqueId());
      }
   }

   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onMove(PlayerMoveEvent event) {
      if (this.on(Feature.WIND_STOP)) {
         Location from = event.getFrom();
         Location to = event.getTo();
         double dx = to.getX() - from.getX();
         double dy = to.getY() - from.getY();
         double dz = to.getZ() - from.getZ();
         // Turning the head only says nothing about movement; keep the last real one.
         if (dx != 0.0 || dy != 0.0 || dz != 0.0) {
            double[] move = this.moves.computeIfAbsent(event.getPlayer().getUniqueId(), ignored -> new double[4]);
            move[0] = dx;
            move[1] = dy;
            move[2] = dz;
            move[3] = Bukkit.getCurrentTick();
         }
      }

      if (!this.glided.isEmpty()) {
         Player player = event.getPlayer();
         // Landed: the next fall is a clean one.
         if (!player.isGliding() && ((LivingEntity)player).isOnGround()) {
            this.glided.remove(player.getUniqueId());
         }
      }
   }

   @EventHandler
   public void onDeath(PlayerDeathEvent event) {
      this.forget(event.getEntity().getUniqueId());
   }

   @EventHandler
   public void onQuit(PlayerQuitEvent event) {
      this.forget(event.getPlayer().getUniqueId());
      this.smashReady.remove(event.getPlayer().getUniqueId());
      this.windStopReady.remove(event.getPlayer().getUniqueId());
      this.moves.remove(event.getPlayer().getUniqueId());
   }

   private void forget(UUID id) {
      this.attempts.remove(id);
      this.glided.remove(id);
   }

   record Attempt(int tick, double fall, double y) {
   }

   private final class PreAttack implements Listener {
      @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
      public void onAttack(PrePlayerAttackEntityEvent event) {
         if (event.willAttack() && event.getAttacked() instanceof LivingEntity) {
            MaceModule.this.attempt(event.getPlayer());
         }
      }
   }
}
