package com.pvpcore.rules;

import com.destroystokyo.paper.event.player.PlayerElytraBoostEvent;
import com.destroystokyo.paper.event.player.PlayerLaunchProjectileEvent;
import com.pvpcore.Feature;
import com.pvpcore.Module;
import com.pvpcore.PvPCore;
import com.pvpcore.util.Text;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Firework;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.ThrowableProjectile;
import org.bukkit.entity.Trident;
import org.bukkit.entity.minecart.ExplosiveMinecart;
import org.bukkit.event.Event.Result;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/**
 * Enforces rules.yml: damage nerfs and caps, cooldowns, disabled items and block limits.
 * Players with {@link #BYPASS}, and players in creative or spectator, are never affected.
 */
public final class RulesModule extends Module {
   public static final String BYPASS = "pvpcore.bypass.rules";
   static final int CHUNK_CACHE_TICKS = 100;
   static final int MAX_CHUNK_CACHE = 4096;
   private static final long NOTICE_GAP_MS = 750L;
   private final NamespacedKey sourceKey;
   /** Cooldown end tick per player and item. Survives relogging, unlike vanilla item cooldowns. */
   private final Map<UUID, Map<Material, Integer>> cooldowns = new HashMap<>();
   private final Map<UUID, Map<Material, ArrayDeque<BlockKey>>> placed = new HashMap<>();
   private final Map<ChunkKey, CachedCount> chunkCounts = new HashMap<>();
   private final Map<UUID, Long> lastNotice = new HashMap<>();

   public RulesModule(PvPCore plugin) {
      super(plugin);
      this.sourceKey = new NamespacedKey(plugin, "source");
   }

   private Rules rules() {
      return this.plugin.rules();
   }

   @Override
   public void apply() {
      this.chunkCounts.clear();
   }

   @Override
   public void stop() {
      this.chunkCounts.clear();
   }

   Rule rule(Material material, World world) {
      if (material == null || material.isAir() || !this.on(Feature.ITEM_RULES)) {
         return null;
      }

      Rule rule = this.rules().get(material);
      return rule != null && rule.appliesIn(world) ? rule : null;
   }

   static boolean exempt(Player player) {
      GameMode mode = player.getGameMode();
      return mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR || player.hasPermission(BYPASS);
   }

   // ------------------------------------------------------------------ cooldowns

   public int remaining(Player player, Material material) {
      Map<Material, Integer> own = this.cooldowns.get(player.getUniqueId());
      if (own == null) {
         return 0;
      }

      Integer end = own.get(material);
      if (end == null) {
         return 0;
      }

      int left = end - Bukkit.getCurrentTick();
      if (left <= 0) {
         own.remove(material);
         if (own.isEmpty()) {
            this.cooldowns.remove(player.getUniqueId());
         }

         return 0;
      }

      return left;
   }

   private void startCooldown(Player player, Material material, Rule rule) {
      int ticks = rule.cooldownTicks();
      if (ticks <= 0) {
         return;
      }

      this.cooldowns.computeIfAbsent(player.getUniqueId(), ignored -> new EnumMap<>(Material.class)).put(material, Bukkit.getCurrentTick() + ticks);
      // A tick later, so it lands on top of any cooldown vanilla sets after the event (pearls, wind charges, chorus).
      Bukkit.getScheduler().runTask(this.plugin, () -> this.showCooldown(player, material));
   }

   private void showCooldown(Player player, Material material) {
      if (player.isOnline()) {
         int left = this.remaining(player, material);
         if (left > 0) {
            player.setCooldown(material, left);
         }
      }
   }

   /** Whether the player may use this item now; tells them why not. */
   private boolean blocked(Player player, Material material, Rule rule) {
      if (rule.disabled()) {
         this.notice(player, "rule-disabled", Map.of("item", Text.itemName(material)));
         return true;
      }

      if (rule.hasCooldown()) {
         int left = this.remaining(player, material);
         if (left > 0) {
            this.notice(player, "rule-cooldown", Map.of("item", Text.itemName(material), "seconds", Text.trim(Math.ceil(left / 2.0) / 10.0)));
            return true;
         }
      }

      return false;
   }

   private void notice(Player player, String message, Map<String, ?> placeholders) {
      long now = System.currentTimeMillis();
      Long last = this.lastNotice.get(player.getUniqueId());
      if (last == null || now - last >= NOTICE_GAP_MS) {
         this.lastNotice.put(player.getUniqueId(), now);
         this.plugin.actionBar(player, message, placeholders);
      }
   }

   @EventHandler(priority = EventPriority.MONITOR)
   public void onJoin(PlayerJoinEvent event) {
      Player player = event.getPlayer();
      Map<Material, Integer> own = this.cooldowns.get(player.getUniqueId());
      if (own != null) {
         for (Material material : new ArrayList<>(own.keySet())) {
            this.showCooldown(player, material);
         }
      }
   }

   // ------------------------------------------------------------------ using items

   @EventHandler(priority = EventPriority.LOW)
   public void onInteract(PlayerInteractEvent event) {
      ItemStack item = event.getItem();
      if (item == null || !event.getAction().isRightClick() || event.useItemInHand() == Result.DENY) {
         return;
      }

      Player player = event.getPlayer();
      Rule rule = this.rule(item.getType(), player.getWorld());
      if (rule != null && !exempt(player) && this.blocked(player, item.getType(), rule)) {
         event.setUseItemInHand(Result.DENY);
      }
   }

   @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
   public void onConsume(PlayerItemConsumeEvent event) {
      Player player = event.getPlayer();
      Material material = event.getItem().getType();
      Rule rule = this.rule(material, player.getWorld());
      if (rule != null && !exempt(player) && this.blocked(player, material, rule)) {
         event.setCancelled(true);
      }
   }

   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onConsumed(PlayerItemConsumeEvent event) {
      this.used(event.getPlayer(), event.getItem().getType());
   }

   @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
   public void onThrow(PlayerLaunchProjectileEvent event) {
      Player player = event.getPlayer();
      Material material = event.getItemStack().getType();
      Rule rule = this.rule(material, player.getWorld());
      if (rule != null && !exempt(player) && this.blocked(player, material, rule)) {
         event.setCancelled(true);
      }
   }

   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onThrown(PlayerLaunchProjectileEvent event) {
      Material material = event.getItemStack().getType();
      this.tagSource(event.getProjectile(), material.name());
      this.used(event.getPlayer(), material);
   }

   /** Tridents are thrown without Paper's launch event on some versions. */
   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onTrident(ProjectileLaunchEvent event) {
      if (event.getEntity() instanceof Trident trident && trident.getShooter() instanceof Player player) {
         this.used(player, Material.TRIDENT);
      }
   }

   @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
   public void onShoot(EntityShootBowEvent event) {
      if (!(event.getEntity() instanceof Player player) || exempt(player) || event.getBow() == null) {
         return;
      }

      Material bow = event.getBow().getType();
      Rule bowRule = this.rule(bow, player.getWorld());
      if (bowRule != null && this.blocked(player, bow, bowRule)) {
         event.setCancelled(true);
         return;
      }

      ItemStack ammo = event.getConsumable();
      Rule ammoRule = ammo == null ? null : this.rule(ammo.getType(), player.getWorld());
      if (ammoRule != null && ammoRule.disabled()) {
         this.blocked(player, ammo.getType(), ammoRule);
         event.setCancelled(true);
      }
   }

   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onShot(EntityShootBowEvent event) {
      if (event.getBow() == null) {
         return;
      }

      ItemStack ammo = event.getConsumable();
      String source = event.getBow().getType().name() + (ammo == null || ammo.getType().isAir() ? "" : "," + ammo.getType().name());
      this.tagSource(event.getProjectile(), source);
      if (event.getEntity() instanceof Player player) {
         this.used(player, event.getBow().getType());
      }
   }

   @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
   public void onBucket(PlayerBucketEmptyEvent event) {
      Player player = event.getPlayer();
      Rule rule = this.rule(event.getBucket(), player.getWorld());
      if (rule != null && !exempt(player) && this.blocked(player, event.getBucket(), rule)) {
         event.setCancelled(true);
      }
   }

   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onBucketUsed(PlayerBucketEmptyEvent event) {
      this.used(event.getPlayer(), event.getBucket());
   }

   @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
   public void onBoost(PlayerElytraBoostEvent event) {
      Player player = event.getPlayer();
      Material material = event.getItemStack().getType();
      Rule rule = this.rule(material, player.getWorld());
      if (rule != null && !exempt(player) && this.blocked(player, material, rule)) {
         event.setCancelled(true);
      }
   }

   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onBoosted(PlayerElytraBoostEvent event) {
      this.used(event.getPlayer(), event.getItemStack().getType());
   }

   @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
   public void onIgnite(BlockIgniteEvent event) {
      Material material = igniter(event);
      if (material != null && event.getPlayer() != null) {
         Player player = event.getPlayer();
         Rule rule = this.rule(material, player.getWorld());
         if (rule != null && !exempt(player) && this.blocked(player, material, rule)) {
            event.setCancelled(true);
         }
      }
   }

   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onIgnited(BlockIgniteEvent event) {
      Material material = igniter(event);
      if (material != null && event.getPlayer() != null) {
         this.used(event.getPlayer(), material);
      }
   }

   private static Material igniter(BlockIgniteEvent event) {
      return switch (event.getCause()) {
         case FLINT_AND_STEEL -> Material.FLINT_AND_STEEL;
         case FIREBALL -> Material.FIRE_CHARGE;
         default -> null;
      };
   }

   @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
   public void onTotem(EntityResurrectEvent event) {
      if (event.getEntity() instanceof Player player && !exempt(player)) {
         Rule rule = this.rule(Material.TOTEM_OF_UNDYING, player.getWorld());
         if (rule != null && this.blocked(player, Material.TOTEM_OF_UNDYING, rule)) {
            event.setCancelled(true);
         }
      }
   }

   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onTotemUsed(EntityResurrectEvent event) {
      if (event.getEntity() instanceof Player player) {
         this.used(player, Material.TOTEM_OF_UNDYING);
      }
   }

   private void used(Player player, Material material) {
      Rule rule = this.rule(material, player.getWorld());
      if (rule != null && rule.hasCooldown() && !exempt(player)) {
         this.startCooldown(player, material, rule);
      }
   }

   private void tagSource(Entity projectile, String source) {
      try {
         projectile.getPersistentDataContainer().set(this.sourceKey, PersistentDataType.STRING, source);
      } catch (RuntimeException ignored) {
         // not every projectile keeps data on every version; damage just won't be matched to its item
      }
   }

   // ------------------------------------------------------------------ placing

   @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
   public void onPlaceEntity(EntityPlaceEvent event) {
      Player player = event.getPlayer();
      if (player == null || exempt(player)) {
         return;
      }

      Material material = handItem(player, event);
      Rule rule = this.rule(material, player.getWorld());
      if (rule != null && this.blocked(player, material, rule)) {
         event.setCancelled(true);
      }
   }

   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onPlacedEntity(EntityPlaceEvent event) {
      if (event.getPlayer() != null) {
         this.used(event.getPlayer(), handItem(event.getPlayer(), event));
      }
   }

   private static Material handItem(Player player, EntityPlaceEvent event) {
      EquipmentSlot hand;
      try {
         hand = event.getHand();
      } catch (LinkageError | RuntimeException e) {
         hand = EquipmentSlot.HAND;
      }

      return player.getInventory().getItem(hand == null ? EquipmentSlot.HAND : hand).getType();
   }

   @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
   public void onPlace(BlockPlaceEvent event) {
      Player player = event.getPlayer();
      if (exempt(player)) {
         return;
      }

      Material item = event.getItemInHand().getType();
      Rule rule = this.rule(item, player.getWorld());
      if (rule != null && this.blocked(player, item, rule)) {
         event.setCancelled(true);
      }
   }

   @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
   public void onPlaceLimit(BlockPlaceEvent event) {
      Player player = event.getPlayer();
      if (exempt(player)) {
         return;
      }

      Block block = event.getBlockPlaced();
      Rule rule = this.limitRule(event.getItemInHand().getType(), block.getType(), block.getWorld());
      if (rule == null) {
         return;
      }

      Material type = block.getType();
      if (rule.playerLimit() > 0 && this.alive(player, type) >= rule.playerLimit()) {
         event.setCancelled(true);
         this.notice(player, "rule-player-limit", Map.of("item", Text.itemName(rule.material()), "limit", Integer.toString(rule.playerLimit())));
         return;
      }

      if (rule.chunkLimit() > 0 && this.existingInChunk(block.getChunk(), type, true) >= rule.chunkLimit()) {
         event.setCancelled(true);
         this.notice(player, "rule-chunk-limit", Map.of("item", Text.itemName(rule.material()), "limit", Integer.toString(rule.chunkLimit())));
      }
   }

   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onPlaced(BlockPlaceEvent event) {
      Player player = event.getPlayer();
      Block block = event.getBlockPlaced();
      this.used(player, event.getItemInHand().getType());
      Rule rule = this.limitRule(event.getItemInHand().getType(), block.getType(), block.getWorld());
      if (rule == null) {
         return;
      }

      Material type = block.getType();
      ChunkKey key = ChunkKey.of(block.getChunk(), type);
      CachedCount cached = this.chunkCounts.get(key);
      if (cached != null) {
         cached.count++;
      }

      if (rule.playerLimit() > 0 && !exempt(player)) {
         ArrayDeque<BlockKey> own = this.placed.computeIfAbsent(player.getUniqueId(), ignored -> new HashMap<>())
            .computeIfAbsent(type, ignored -> new ArrayDeque<>());
         own.addLast(BlockKey.of(block));
         while (own.size() > Math.max(64, rule.playerLimit() * 4)) {
            own.pollFirst();
         }
      }
   }

   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onBreak(BlockBreakEvent event) {
      if (this.chunkCounts.isEmpty()) {
         return;
      }

      Block block = event.getBlock();
      CachedCount cached = this.chunkCounts.get(ChunkKey.of(block.getChunk(), block.getType()));
      if (cached != null && cached.count > 0) {
         cached.count--;
      }
   }

   private Rule limitRule(Material item, Material block, World world) {
      Rule rule = this.rule(item, world);
      if (rule == null || !rule.limitsBlocks()) {
         rule = item == block ? null : this.rule(block, world);
      }

      return rule != null && rule.limitsBlocks() ? rule : null;
   }

   /** Blocks of this type the player placed that are still standing. Unloaded ones still count. */
   int alive(Player player, Material type) {
      Map<Material, ArrayDeque<BlockKey>> own = this.placed.get(player.getUniqueId());
      ArrayDeque<BlockKey> blocks = own == null ? null : own.get(type);
      if (blocks == null) {
         return 0;
      }

      Iterator<BlockKey> iterator = blocks.iterator();
      while (iterator.hasNext()) {
         BlockKey key = iterator.next();
         World world = Bukkit.getWorld(key.world());
         if (world == null) {
            iterator.remove();
         } else if (world.isChunkLoaded(key.x() >> 4, key.z() >> 4) && world.getBlockAt(key.x(), key.y(), key.z()).getType() != type) {
            iterator.remove();
         }
      }

      return blocks.size();
   }

   /**
    * Blocks of this type already in the chunk. With {@code includesNew}, the block being placed is in the world
    * already (it is during BlockPlaceEvent) and is not counted.
    */
   int existingInChunk(Chunk chunk, Material type, boolean includesNew) {
      int now = Bukkit.getCurrentTick();
      ChunkKey key = ChunkKey.of(chunk, type);
      CachedCount cached = this.chunkCounts.get(key);
      if (cached != null && now - cached.tick <= CHUNK_CACHE_TICKS) {
         return cached.count;
      }

      int count = count(chunk, type);
      if (includesNew) {
         count = Math.max(0, count - 1);
      }

      if (this.chunkCounts.size() >= MAX_CHUNK_CACHE) {
         this.chunkCounts.clear();
      }

      this.chunkCounts.put(key, new CachedCount(count, now));
      return count;
   }

   static int count(Chunk chunk, Material type) {
      World world = chunk.getWorld();
      ChunkSnapshot snapshot = chunk.getChunkSnapshot(false, false, false);
      int minY = world.getMinHeight();
      int maxY = world.getMaxHeight();
      int sections = (maxY - minY + 15) >> 4;
      int count = 0;

      for (int section = 0; section < sections; section++) {
         boolean empty;
         try {
            empty = snapshot.isSectionEmpty(section);
         } catch (RuntimeException e) {
            empty = false;
         }

         if (empty) {
            continue;
         }

         int bottom = minY + (section << 4);
         int top = Math.min(maxY, bottom + 16);
         for (int y = bottom; y < top; y++) {
            for (int x = 0; x < 16; x++) {
               for (int z = 0; z < 16; z++) {
                  if (snapshot.getBlockType(x, y, z) == type) {
                     count++;
                  }
               }
            }
         }
      }

      return count;
   }

   // ------------------------------------------------------------------ damage

   @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
   public void onDamage(EntityDamageByEntityEvent event) {
      if (!(event.getEntity() instanceof LivingEntity victim) || !this.on(Feature.ITEM_RULES)) {
         return;
      }

      if (!(victim instanceof Player) && !this.rules().affectMobs()) {
         return;
      }

      Source source = this.source(event);
      if (source.materials().isEmpty() || source.player() != null && exempt(source.player())) {
         return;
      }

      World world = victim.getWorld();
      double multiplier = 1.0;
      double cap = Double.POSITIVE_INFINITY;
      for (Material material : source.materials()) {
         Rule rule = this.rule(material, world);
         if (rule == null) {
            continue;
         }

         if (source.melee() && source.player() != null) {
            // A sweep is part of the same swing as the main hit, which may have started the cooldown this tick.
            boolean primary = event.getCause() == DamageCause.ENTITY_ATTACK;
            if (primary ? this.blocked(source.player(), material, rule) : rule.disabled()) {
               event.setCancelled(true);
               return;
            }
         }

         multiplier *= rule.damage();
         if (rule.maxDamage() > 0.0) {
            cap = Math.min(cap, rule.maxDamage() * 2.0);
         }
      }

      this.scale(event, multiplier, cap);
   }

   /** A weapon's cooldown starts with its main hit on a player (or any mob, with affect-mobs). */
   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onDamaged(EntityDamageByEntityEvent event) {
      if (!(event.getEntity() instanceof LivingEntity victim) || !(victim instanceof Player) && !this.rules().affectMobs()) {
         return;
      }

      if (event.getDamager() instanceof Player attacker && event.getCause() == DamageCause.ENTITY_ATTACK) {
         Material weapon = attacker.getInventory().getItemInMainHand().getType();
         if (Rule.weapon(weapon)) {
            this.used(attacker, weapon);
         }
      }
   }

   @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
   public void onBlockDamage(EntityDamageByBlockEvent event) {
      if (!(event.getEntity() instanceof LivingEntity victim) || !this.on(Feature.ITEM_RULES)) {
         return;
      }

      if (!(victim instanceof Player) && !this.rules().affectMobs()) {
         return;
      }

      BlockState state;
      try {
         state = event.getDamagerBlockState();
      } catch (LinkageError | RuntimeException e) {
         state = null;
      }

      Material material = state == null ? null : state.getType();
      Rule rule = material == null ? null : this.rule(material, victim.getWorld());
      if (rule != null) {
         this.scale(event, rule.damage(), rule.maxDamage() > 0.0 ? rule.maxDamage() * 2.0 : Double.POSITIVE_INFINITY);
      }
   }

   private void scale(org.bukkit.event.entity.EntityDamageEvent event, double multiplier, double cap) {
      if (Math.abs(multiplier - 1.0) < 1.0E-9 && cap == Double.POSITIVE_INFINITY) {
         return;
      }

      double damage = Math.min(event.getDamage() * multiplier, cap);
      event.setDamage(Math.max(0.0, damage));
   }

   static boolean melee(DamageCause cause) {
      return cause == DamageCause.ENTITY_ATTACK || cause == DamageCause.ENTITY_SWEEP_ATTACK;
   }

   /** Which items a hit came from, and the player behind it. */
   Source source(EntityDamageByEntityEvent event) {
      Entity damager = event.getDamager();
      if (damager instanceof Player player) {
         if (!melee(event.getCause())) {
            return new Source(player, List.of(), false);
         }

         Material weapon = player.getInventory().getItemInMainHand().getType();
         return new Source(player, weapon.isAir() ? List.of() : List.of(weapon), true);
      }

      if (damager instanceof Projectile projectile) {
         Player shooter = projectile.getShooter() instanceof Player player ? player : null;
         if (projectile instanceof Trident) {
            return new Source(shooter, List.of(Material.TRIDENT), false);
         }

         List<Material> tagged = this.taggedSource(projectile);
         if (!tagged.isEmpty()) {
            return new Source(shooter, tagged, false);
         }

         Material material = projectileItem(projectile);
         return new Source(shooter, material == null ? List.of() : List.of(material), false);
      }

      Player owner = this.plugin.attacker(event);
      if (damager instanceof ExplosiveMinecart) {
         return new Source(owner, List.of(Material.TNT_MINECART), false);
      }

      if (damager instanceof EnderCrystal) {
         return new Source(owner, List.of(Material.END_CRYSTAL), false);
      }

      if (damager instanceof TNTPrimed) {
         return new Source(owner, List.of(Material.TNT), false);
      }

      return new Source(owner, List.of(), false);
   }

   private List<Material> taggedSource(Entity projectile) {
      String tag;
      try {
         tag = projectile.getPersistentDataContainer().get(this.sourceKey, PersistentDataType.STRING);
      } catch (RuntimeException e) {
         tag = null;
      }

      if (tag == null || tag.isEmpty()) {
         return List.of();
      }

      List<Material> materials = new ArrayList<>(2);
      for (String name : tag.split(",")) {
         Material material = Material.getMaterial(name);
         if (material != null && !materials.contains(material)) {
            materials.add(material);
         }
      }

      return materials;
   }

   private static Material projectileItem(Projectile projectile) {
      try {
         if (projectile instanceof Firework) {
            return Material.FIREWORK_ROCKET;
         }

         if (projectile instanceof AbstractArrow arrow) {
            return arrow.getItemStack().getType();
         }

         if (projectile instanceof ThrowableProjectile throwable) {
            return throwable.getItem().getType();
         }
      } catch (LinkageError | RuntimeException ignored) {
         // fall through
      }

      return null;
   }

   record Source(Player player, List<Material> materials, boolean melee) {
   }

   record BlockKey(UUID world, int x, int y, int z) {
      static BlockKey of(Block block) {
         return new BlockKey(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
      }
   }

   record ChunkKey(UUID world, int x, int z, Material type) {
      static ChunkKey of(Chunk chunk, Material type) {
         return new ChunkKey(chunk.getWorld().getUID(), chunk.getX(), chunk.getZ(), type);
      }
   }

   static final class CachedCount {
      int count;
      final int tick;

      CachedCount(int count, int tick) {
         this.count = count;
         this.tick = tick;
      }
   }
}
