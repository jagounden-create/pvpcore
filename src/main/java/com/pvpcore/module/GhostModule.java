package com.pvpcore.module;

import com.pvpcore.Compat;
import com.pvpcore.Feature;
import com.pvpcore.Module;
import com.pvpcore.PvPCore;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

/**
 * Ghost fixes: when a viewer's game and the server disagree about a player.
 * <ul>
 *    <li><b>Ghost players</b> - invisible or frozen players after a totem pop, respawn, teleport, world change, join,
 *    leaving spectator or getting off a mount. Viewers who should see the player but aren't sent them are re-shown the
 *    player; after a totem pop everyone watching is.</li>
 *    <li><b>Ghost scanner</b> - every few seconds, players close enough to see each other who aren't being sent each
 *    other (twice in a row, so normal tracking lag never counts) are re-shown.</li>
 *    <li><b>Ghost shields</b> - a shield that looks raised when it is down, or down when it is up. Whenever a player's
 *    shield goes up, comes down or moves between hands, viewers are re-sent its state once it settles.</li>
 *    <li><b>Golden apple fix</b> - after eating a golden apple, a totem, or when golden hearts run out, the player's
 *    inventory and hearts are re-sent.</li>
 * </ul>
 * Re-showing a player means hiding and showing them to one viewer in the same tick, which makes that viewer's game
 * rebuild them from scratch. Shields and hearts are re-sent without that when the server allows ({@link Resync}).
 */
public final class GhostModule extends Module {
   /** Why a fix ran, for /pvpcore status. */
   public enum Reason {
      TOTEM, RESPAWN, TELEPORT, WORLD, JOIN, GAMEMODE, DISMOUNT, SCANNER, SHIELD, HEARTS, COMMAND
   }

   /** Teleports shorter than this, in the same world, never lose anyone. */
   static final double TELEPORT_MIN_DISTANCE = 8.0;
   /** A shield's state must hold this long before it is re-sent, so quick taps send once. */
   static final int SHIELD_SETTLE_TICKS = 2;
   /** At most one full re-show per player this often when the light re-send isn't available. */
   static final int FALLBACK_GAP_TICKS = 10;
   private final Map<Reason, Long> counts = new EnumMap<>(Reason.class);
   private final Map<UUID, Integer> shieldStates = new HashMap<>();
   private final Map<UUID, Integer> shieldDue = new HashMap<>();
   /** Players whose shield changed hands (or blocked a hit) since the last re-send: their held items go too. */
   private final Set<UUID> equipDue = new HashSet<>();
   private final Map<UUID, Integer> lastFallback = new HashMap<>();
   private Set<Long> suspects = new HashSet<>();
   private Resync resync;
   private BukkitTask ticker;
   private int nextScan;
   /** Player tracking range per world name, from spigot.yml: a world's own setting, else the default. */
   private final Map<String, Double> trackingRanges = new HashMap<>();
   private boolean trackerBroken;

   public GhostModule(PvPCore plugin) {
      super(plugin);
   }

   @Override
   public void start() {
      this.resync = Resync.create();
      if (this.resync == null) {
         this.plugin.getLogger().info("Ghost fixes: this server's internals are not the expected ones, so golden hearts are fixed by re-showing the player.");
      }
      this.ticker = Bukkit.getScheduler().runTaskTimer(this.plugin, this::tick, 1L, 1L);
   }

   @Override
   public void apply() {
      this.trackingRanges.clear();
      if (!this.on(Feature.GHOST_SHIELDS)) {
         this.shieldStates.clear();
         this.shieldDue.clear();
         this.equipDue.clear();
      }

      if (!this.on(Feature.GHOST_SCANNER)) {
         this.suspects.clear();
      }
   }

   @Override
   public void stop() {
      if (this.ticker != null) {
         this.ticker.cancel();
      }

      this.shieldStates.clear();
      this.shieldDue.clear();
      this.equipDue.clear();
      this.suspects.clear();
   }

   /** Whether shields and hearts are re-sent without re-showing the player. */
   public boolean lightResync() {
      return this.resync != null;
   }

   public boolean scannerAvailable() {
      return Compat.TRACKED_BY && !this.trackerBroken;
   }

   public long count(Reason reason) {
      return this.counts.getOrDefault(reason, 0L);
   }

   private void counted(Reason reason) {
      this.counts.merge(reason, 1L, Long::sum);
   }

   // ------------------------------------------------------------------ ticking

   private void tick() {
      int now = Bukkit.getCurrentTick();
      if (this.on(Feature.GHOST_SHIELDS)) {
         this.watchShields(now);
      }

      if (this.on(Feature.GHOST_SCANNER) && this.scannerAvailable() && now >= this.nextScan) {
         this.nextScan = now + Math.max(20, (int)Math.round(this.settings().value(Feature.GHOST_SCANNER) * 20.0));
         this.scan();
         if (this.trackerBroken) {
            this.plugin.getLogger().info("The ghost scanner is not supported on this server and has switched itself off.");
         }
      }
   }

   // ------------------------------------------------------------------ ghost players

   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onTotem(EntityResurrectEvent event) {
      if (event.getEntity() instanceof Player player) {
         if (this.on(Feature.GHOST_PLAYERS)) {
            this.later(player, Reason.TOTEM, 1);
         }

         if (this.on(Feature.GHOST_HEARTS)) {
            this.heartsLater(player, 1);
         }
      }
   }

   @EventHandler(priority = EventPriority.MONITOR)
   public void onRespawn(PlayerRespawnEvent event) {
      if (this.on(Feature.GHOST_PLAYERS)) {
         this.later(event.getPlayer(), Reason.RESPAWN, 10);
      }
   }

   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onTeleport(PlayerTeleportEvent event) {
      Location from = event.getFrom();
      Location to = event.getTo();
      if (this.on(Feature.GHOST_PLAYERS) && to != null && from.getWorld() == to.getWorld()
         && from.distanceSquared(to) >= TELEPORT_MIN_DISTANCE * TELEPORT_MIN_DISTANCE) {
         this.later(event.getPlayer(), Reason.TELEPORT, 5);
      }
   }

   @EventHandler(priority = EventPriority.MONITOR)
   public void onWorldChange(PlayerChangedWorldEvent event) {
      if (this.on(Feature.GHOST_PLAYERS)) {
         this.later(event.getPlayer(), Reason.WORLD, 10);
      }
   }

   @EventHandler(priority = EventPriority.MONITOR)
   public void onJoin(PlayerJoinEvent event) {
      if (this.on(Feature.GHOST_PLAYERS)) {
         this.later(event.getPlayer(), Reason.JOIN, 20);
      }
   }

   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onGameMode(PlayerGameModeChangeEvent event) {
      if (this.on(Feature.GHOST_PLAYERS) && event.getPlayer().getGameMode() == GameMode.SPECTATOR && event.getNewGameMode() != GameMode.SPECTATOR) {
         this.later(event.getPlayer(), Reason.GAMEMODE, 2);
      }
   }

   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onDismount(EntityDismountEvent event) {
      if (this.on(Feature.GHOST_PLAYERS) && event.getEntity() instanceof Player player) {
         this.later(player, Reason.DISMOUNT, 2);
      }
   }

   @EventHandler
   public void onQuit(PlayerQuitEvent event) {
      UUID id = event.getPlayer().getUniqueId();
      this.shieldStates.remove(id);
      this.shieldDue.remove(id);
      this.equipDue.remove(id);
      this.lastFallback.remove(id);
   }

   private void later(Player player, Reason reason, int delay) {
      Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
         if (player.isOnline()) {
            this.fix(player, reason);
         }
      }, delay);
   }

   /** Re-shows {@code player} to whoever is missing them (everyone, after a totem or by command). */
   public int fix(Player player, Reason reason) {
      if (npc(player)) {
         return 0;
      }

      boolean everyone = reason == Reason.TOTEM || reason == Reason.COMMAND || !this.scannerAvailable();
      Set<Player> tracked = everyone ? Set.of() : this.trackedBy(player);
      int fixed = 0;
      List<Player> nearby = this.nearby(player);
      for (Player viewer : nearby) {
         if ((everyone || !tracked.contains(viewer)) && hasChunk(viewer, player) && this.reshow(player, viewer)) {
            fixed++;
         }
      }

      // Someone who just arrived may be missing the others, too.
      if (reason != Reason.TOTEM && reason != Reason.DISMOUNT) {
         for (Player other : nearby) {
            if ((everyone || !this.trackedBy(other).contains(player)) && hasChunk(player, other) && this.reshow(other, player)) {
               fixed++;
            }
         }
      }

      if (fixed > 0) {
         this.counted(reason);
      }

      return fixed;
   }

   private boolean reshow(Player target, Player viewer) {
      if (viewer.equals(target) || !viewer.isOnline() || !target.isOnline() || npc(viewer) || !viewer.canSee(target)) {
         return false;
      }

      viewer.hidePlayer(this.plugin, target);
      viewer.showPlayer(this.plugin, target);
      return true;
   }

   private Set<Player> trackedBy(Player player) {
      if (!this.scannerAvailable()) {
         return Set.of();
      }

      try {
         return player.getTrackedBy();
      } catch (RuntimeException | LinkageError e) {
         this.trackerBroken = true;
         return Set.of();
      }
   }

   /** Players in range of {@code player} who should be able to see them. */
   private List<Player> nearby(Player player) {
      List<Player> out = new ArrayList<>();
      World world = player.getWorld();
      Location at = player.getLocation();
      for (Player other : world.getPlayers()) {
         if (!other.equals(player) && !npc(other) && this.inRange(other, at)) {
            out.add(other);
         }
      }

      return out;
   }

   private boolean inRange(Player viewer, Location target) {
      double range = expectedRange(this.settings().tuning().ghostRange(), this.trackingRange(viewer.getWorld()), viewDistance(viewer));
      return viewer.getLocation().distanceSquared(target) <= range * range;
   }

   private double trackingRange(World world) {
      return this.trackingRanges.computeIfAbsent(world.getName(), GhostModule::playerTrackingRange);
   }

   /** How far apart two players can be and still certainly be sent to each other. */
   static double expectedRange(double configured, double trackingRange, int viewDistanceChunks) {
      double byView = Math.max(0, viewDistanceChunks - 1) * 16.0;
      return Math.max(0.0, Math.min(configured, Math.min(trackingRange - 4.0, byView)));
   }

   private static int viewDistance(Player viewer) {
      int server;
      try {
         server = viewer.getViewDistance();
      } catch (RuntimeException | LinkageError e) {
         server = Bukkit.getViewDistance();
      }

      int client;
      try {
         client = viewer.getClientViewDistance();
      } catch (RuntimeException | LinkageError e) {
         client = server;
      }

      return client > 0 ? Math.min(server, client) : server;
   }

   /** How far players are sent to each other in this world, by spigot.yml. */
   static double playerTrackingRange(String world) {
      try {
         org.bukkit.configuration.file.YamlConfiguration spigot = Bukkit.spigot().getSpigotConfig();
         double fallback = spigot.getDouble("world-settings.default.entity-tracking-range.players", 48.0);
         return spigot.getDouble("world-settings." + world + ".entity-tracking-range.players", fallback);
      } catch (RuntimeException | LinkageError e) {
         return 48.0;
      }
   }

   /**
    * Whether {@code viewer}'s game has the chunk {@code target} stands in. Until it does, the server holds the target
    * back on purpose, so that is not a ghost. True when the server can't say.
    */
   static boolean hasChunk(Player viewer, Player target) {
      Location at = target.getLocation();
      try {
         return viewer.isChunkSent(chunkKey(at.getBlockX() >> 4, at.getBlockZ() >> 4));
      } catch (RuntimeException | LinkageError e) {
         return true;
      }
   }

   /** Paper's chunk key: x in the low 32 bits, z in the high. */
   static long chunkKey(int chunkX, int chunkZ) {
      return (long)chunkX & 0xFFFFFFFFL | ((long)chunkZ & 0xFFFFFFFFL) << 32;
   }

   static boolean npc(Entity entity) {
      return entity.hasMetadata("NPC");
   }

   // ------------------------------------------------------------------ scanner

   private void scan() {
      Set<Long> found = new HashSet<>();
      double configured = this.settings().tuning().ghostRange();
      for (World world : Bukkit.getWorlds()) {
         List<Player> players = world.getPlayers();
         int count = players.size();
         if (count < 2) {
            continue;
         }

         // Everything per player is read once, not once per pair.
         double[] x = new double[count];
         double[] y = new double[count];
         double[] z = new double[count];
         double[] reach = new double[count];
         boolean[] views = new boolean[count];
         boolean[] seen = new boolean[count];
         for (int i = 0; i < count; i++) {
            Player player = players.get(i);
            Location at = player.getLocation();
            x[i] = at.getX();
            y[i] = at.getY();
            z[i] = at.getZ();
            double range = expectedRange(configured, this.trackingRange(world), viewDistance(player));
            reach[i] = range * range;
            views[i] = !player.isDead() && !npc(player);
            seen[i] = views[i] && player.getGameMode() != GameMode.SPECTATOR;
         }

         for (int t = 0; t < count; t++) {
            if (!seen[t]) {
               continue;
            }

            Player target = players.get(t);
            Set<Player> tracked = null;
            for (int v = 0; v < count; v++) {
               if (v == t || !views[v]) {
                  continue;
               }

               double dx = x[v] - x[t];
               double dy = y[v] - y[t];
               double dz = z[v] - z[t];
               if (dx * dx + dy * dy + dz * dz > reach[v]) {
                  continue;
               }

               Player viewer = players.get(v);
               if (!viewer.canSee(target)) {
                  continue;
               }

               if (tracked == null) {
                  tracked = this.trackedBy(target);
                  if (this.trackerBroken) {
                     return;
                  }
               }

               if (!tracked.contains(viewer) && hasChunk(viewer, target)) {
                  long key = pairKey(target, viewer);
                  if (this.suspects.contains(key)) {
                     if (this.reshow(target, viewer)) {
                        this.counted(Reason.SCANNER);
                     }
                  } else {
                     found.add(key);
                  }
               }
            }
         }
      }

      this.suspects = found;
   }

   static long pairKey(Player target, Player viewer) {
      return (long)target.getEntityId() << 32 | viewer.getEntityId() & 0xFFFFFFFFL;
   }

   // ------------------------------------------------------------------ shields

   /** The shield-in-hand bits of {@link #shieldState}. */
   static final int HANDS = 6;

   /** Bits: 1 shield raised, 2 shield in main hand, 4 shield in off hand. */
   static int shieldState(Player player) {
      PlayerInventory inventory = player.getInventory();
      int state = 0;
      if (blocks(inventory.getItemInMainHand())) {
         state |= 2;
      }

      if (blocks(inventory.getItemInOffHand())) {
         state |= 4;
      }

      if (state != 0 && player.isHandRaised() && blocks(player.getActiveItem())) {
         state |= 1;
      }

      return state;
   }

   static boolean blocks(ItemStack item) {
      if (item == null || item.getType().isAir()) {
         return false;
      }

      return item.getType() == Material.SHIELD || Compat.DATA_DRIVEN_SHIELDS && Modern.blocks(item);
   }

   private void watchShields(int now) {
      for (Player player : Bukkit.getOnlinePlayers()) {
         int state = shieldState(player);
         Integer before = state == 0 ? this.shieldStates.remove(player.getUniqueId()) : this.shieldStates.put(player.getUniqueId(), state);
         int was = before == null ? 0 : before;
         if (was != state) {
            this.shieldDue.put(player.getUniqueId(), now + SHIELD_SETTLE_TICKS);
            if ((was & HANDS) != (state & HANDS)) {
               this.equipDue.add(player.getUniqueId());
            }
         }
      }

      if (this.shieldDue.isEmpty()) {
         return;
      }

      Iterator<Map.Entry<UUID, Integer>> due = this.shieldDue.entrySet().iterator();
      while (due.hasNext()) {
         Map.Entry<UUID, Integer> entry = due.next();
         if (now >= entry.getValue()) {
            due.remove();
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) {
               this.resendShield(player);
            }
         }
      }
   }

   /** Taking a hit while blocking: make sure everyone saw the shield up. */
   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onBlockedHit(EntityDamageByEntityEvent event) {
      if (this.on(Feature.GHOST_SHIELDS) && event.getEntity() instanceof Player victim && victim.isHandRaised() && blocks(victim.getActiveItem())) {
         this.shieldDue.putIfAbsent(victim.getUniqueId(), Bukkit.getCurrentTick() + 1);
         this.equipDue.add(victim.getUniqueId());
      }
   }

   private void resendShield(Player player) {
      boolean equipment = this.equipDue.remove(player.getUniqueId());
      Collection<Player> viewers = this.viewers(player);
      if (viewers.isEmpty()) {
         return;
      }

      if (equipment) {
         Map<EquipmentSlot, ItemStack> hands = Map.of(
            EquipmentSlot.HAND, player.getInventory().getItemInMainHand(),
            EquipmentSlot.OFF_HAND, player.getInventory().getItemInOffHand()
         );
         for (Player viewer : viewers) {
            viewer.sendEquipmentChange(player, hands);
         }
      }

      // No re-show fallback here: shields go up and down too often, and re-showing a fighter mid-fight is a stutter.
      this.light(player, viewers, Resync.Part.BLOCKING);

      this.counted(Reason.SHIELD);
   }

   /** Who is watching {@code player} right now. */
   private Collection<Player> viewers(Player player) {
      if (this.scannerAvailable()) {
         Set<Player> tracked = this.trackedBy(player);
         if (!this.trackerBroken) {
            List<Player> out = new ArrayList<>(tracked.size());
            for (Player viewer : tracked) {
               if (!npc(viewer)) {
                  out.add(viewer);
               }
            }

            return out;
         }
      }

      return this.nearby(player);
   }

   /** The light re-send; false when it isn't available. One failure switches it off for good. */
   private boolean light(Player player, Collection<Player> viewers, Resync.Part part) {
      if (this.resync == null) {
         return false;
      }

      if (this.resync.send(player, viewers, part)) {
         return true;
      }

      this.resync = null;
      this.plugin.getLogger().info("Ghost fixes: the light re-send failed on this server and is off; golden hearts are fixed by re-showing the player.");
      return false;
   }

   private void fallback(Player player, Collection<Player> viewers) {
      int now = Bukkit.getCurrentTick();
      Integer last = this.lastFallback.get(player.getUniqueId());
      if (last != null && now - last < FALLBACK_GAP_TICKS) {
         return;
      }

      this.lastFallback.put(player.getUniqueId(), now);
      for (Player viewer : viewers) {
         this.reshow(player, viewer);
      }
   }

   // ------------------------------------------------------------------ golden hearts

   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onEat(PlayerItemConsumeEvent event) {
      Material food = event.getItem().getType();
      if (this.on(Feature.GHOST_HEARTS) && (food == Material.GOLDEN_APPLE || food == Material.ENCHANTED_GOLDEN_APPLE)) {
         this.heartsLater(event.getPlayer(), 1);
      }
   }

   /** The last golden heart is gone: make sure nobody still sees it. */
   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onHeartsUsed(EntityDamageEvent event) {
      if (this.on(Feature.GHOST_HEARTS) && event.getEntity() instanceof Player player) {
         double absorption = player.getAbsorptionAmount();
         if (absorption > 0.0 && event.getDamage() >= absorption) {
            Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
               if (player.isOnline() && !player.isDead() && player.getAbsorptionAmount() <= 0.0) {
                  this.resendHearts(player);
               }
            }, 1L);
         }
      }
   }

   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onAbsorptionEnds(EntityPotionEffectEvent event) {
      if (this.on(Feature.GHOST_HEARTS) && event.getEntity() instanceof Player player && event.getModifiedType().equals(PotionEffectType.ABSORPTION)
         && event.getNewEffect() == null) {
         this.heartsLater(player, 1);
      }
   }

   private void heartsLater(Player player, int delay) {
      Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
         if (player.isOnline() && !player.isDead()) {
            this.resendHearts(player);
         }
      }, delay);
   }

   private void resendHearts(Player player) {
      player.updateInventory();
      player.sendHealthUpdate();
      Collection<Player> viewers = this.viewers(player);
      this.light(player, List.of(player), Resync.Part.OWN_HEARTS);
      if (!this.light(player, viewers, Resync.Part.HEARTS)) {
         this.fallback(player, viewers);
      }

      this.counted(Reason.HEARTS);
   }

   /** Only touched where shields are data-driven, so older servers never load these classes. */
   private static final class Modern {
      static boolean blocks(ItemStack item) {
         return item.hasData(io.papermc.paper.datacomponent.DataComponentTypes.BLOCKS_ATTACKS);
      }
   }
}
