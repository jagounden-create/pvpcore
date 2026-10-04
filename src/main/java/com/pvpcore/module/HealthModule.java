package com.pvpcore.module;

import com.pvpcore.Feature;
import com.pvpcore.Module;
import com.pvpcore.PvPCore;
import com.pvpcore.util.Text;
import io.papermc.paper.scoreboard.numbers.NumberFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;

/**
 * Health under names and the target health action bar. The below-name objective is kept on the main scoreboard and
 * on every scoreboard a player is looking at, so it still shows when a sidebar plugin gives players their own board.
 */
public final class HealthModule extends Module {
   static final String OBJECTIVE = "pvpcore_health";
   private static final TextColor HEART = TextColor.fromHexString("#FF5C5C");
   private static final TextColor ABSORPTION = TextColor.fromHexString("#FFD34E");
   private static final TextColor WHITE = TextColor.color(0xFFFFFF);
   /** What each board last showed for each player ({@link #key}), so unchanged scores are not re-sent. */
   private final Map<Scoreboard, Map<UUID, Long>> shown = new ConcurrentHashMap<>();
   private final Set<UUID> dirty = ConcurrentHashMap.newKeySet();
   /** Target health bars to send at the end of this tick: one per attacker, for the last thing they hit. */
   private final Map<Player, LivingEntity> bars = new LinkedHashMap<>();
   private boolean barsQueued;
   private BukkitTask ticker;
   private int ticks;

   public HealthModule(PvPCore plugin) {
      super(plugin);
   }

   @Override
   public void start() {
      this.ticker = Bukkit.getScheduler().runTaskTimer(this.plugin, this::tick, 1L, 2L);
   }

   @Override
   public void apply() {
      if (this.on(Feature.HEALTH_BELOW_NAME)) {
         this.shown.clear();
         this.refreshEveryone();
      } else {
         this.removeObjectives();
      }
   }

   @Override
   public void stop() {
      if (this.ticker != null) {
         this.ticker.cancel();
      }

      this.bars.clear();
      this.removeObjectives();
   }

   private void tick() {
      this.ticks++;
      if (!this.on(Feature.HEALTH_BELOW_NAME)) {
         this.dirty.clear();
      } else if (this.ticks % 10 == 0) {
         // Once a second: catch anything no event reported, and boards that were swapped since.
         this.dirty.clear();
         this.refreshEveryone();
      } else if (!this.dirty.isEmpty()) {
         List<Player> players = new ArrayList<>(this.dirty.size());
         for (UUID id : this.dirty) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
               players.add(player);
            }
         }

         this.dirty.clear();
         this.push(this.boards(), players);
      }
   }

   private Set<Scoreboard> boards() {
      Set<Scoreboard> boards = new HashSet<>();
      boards.add(Bukkit.getScoreboardManager().getMainScoreboard());
      for (Player player : Bukkit.getOnlinePlayers()) {
         boards.add(player.getScoreboard());
      }

      this.shown.keySet().retainAll(boards);
      return boards;
   }

   private void refreshEveryone() {
      this.push(this.boards(), new ArrayList<>(Bukkit.getOnlinePlayers()));
   }

   /**
    * Brings {@code players}' scores up to date on every board. Each player's health is read and formatted once, not
    * once per board: with a sidebar plugin every player has their own board, so this runs players x players times.
    */
   private void push(Set<Scoreboard> boards, List<Player> players) {
      if (players.isEmpty()) {
         return;
      }

      Shown[] now = new Shown[players.size()];
      for (int i = 0; i < now.length; i++) {
         Player player = players.get(i);
         double health = player.isDead() ? 0.0 : player.getHealth();
         now[i] = new Shown(player, health, player.getAbsorptionAmount());
      }

      for (Scoreboard board : boards) {
         Objective objective = this.objective(board);
         if (objective == null) {
            continue;
         }

         Map<UUID, Long> seen = this.shown.computeIfAbsent(board, ignored -> new ConcurrentHashMap<>());
         for (Shown shown : now) {
            Long before = seen.get(shown.id);
            if (before == null || before != shown.key) {
               Score score = objective.getScore(shown.player);
               score.setScore((int)Math.ceil(shown.health));
               score.numberFormat(NumberFormat.fixed(shown.line()));
               seen.put(shown.id, shown.key);
            }
         }
      }
   }

   /** This board's health objective, made if missing; null when another plugin owns the below-name slot. */
   private Objective objective(Scoreboard board) {
      Objective objective = board.getObjective(OBJECTIVE);
      if (objective == null) {
         objective = board.registerNewObjective(OBJECTIVE, Criteria.DUMMY, Component.empty());
         objective.numberFormat(NumberFormat.blank());
         this.shown.remove(board);
      }

      if (objective.getDisplaySlot() != DisplaySlot.BELOW_NAME) {
         if (board.getObjective(DisplaySlot.BELOW_NAME) != null) {
            return null;
         }

         objective.setDisplaySlot(DisplaySlot.BELOW_NAME);
      }

      return objective;
   }

   /** What the line under a name shows, as one number: whole health points and whole golden-heart points. */
   static long key(double health, double absorption) {
      return (long)Math.ceil(Math.max(0.0, health)) << 32 | (long)Math.ceil(Math.max(0.0, absorption)) & 0xFFFFFFFFL;
   }

   /** One player's line, formatted only if some board needs it. */
   private static final class Shown {
      final Player player;
      final UUID id;
      final double health;
      final double absorption;
      final long key;
      private Component line;

      Shown(Player player, double health, double absorption) {
         this.player = player;
         this.id = player.getUniqueId();
         this.health = health;
         this.absorption = absorption;
         this.key = key(health, absorption);
      }

      Component line() {
         if (this.line == null) {
            this.line = health(this.health, this.absorption);
         }

         return this.line;
      }
   }

   static Component health(double health, double absorption) {
      TextComponent.Builder line = Component.text().append(Component.text(Text.points(health) + " ", WHITE)).append(Component.text(Text.HEART, HEART));
      if (absorption > 0.0) {
         line.append(Component.text(" +" + Text.points(absorption), ABSORPTION));
      }

      return line.build();
   }

   private void removeObjectives() {
      Set<Scoreboard> boards = new HashSet<>(this.shown.keySet());
      boards.add(Bukkit.getScoreboardManager().getMainScoreboard());
      for (Player player : Bukkit.getOnlinePlayers()) {
         boards.add(player.getScoreboard());
      }

      for (Scoreboard board : boards) {
         Objective objective = board.getObjective(OBJECTIVE);
         if (objective != null) {
            try {
               objective.unregister();
            } catch (IllegalStateException ignored) {
               // already gone
            }
         }
      }

      this.shown.clear();
      this.dirty.clear();
   }

   private void markDirty(Entity entity) {
      if (entity instanceof Player player) {
         this.dirty.add(player.getUniqueId());
      }
   }

   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onDamage(EntityDamageEvent event) {
      this.markDirty(event.getEntity());
   }

   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onRegain(EntityRegainHealthEvent event) {
      this.markDirty(event.getEntity());
   }

   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onEffect(EntityPotionEffectEvent event) {
      this.markDirty(event.getEntity());
   }

   @EventHandler(priority = EventPriority.MONITOR)
   public void onJoin(PlayerJoinEvent event) {
      this.markDirty(event.getPlayer());
   }

   @EventHandler(priority = EventPriority.MONITOR)
   public void onRespawn(PlayerRespawnEvent event) {
      this.markDirty(event.getPlayer());
   }

   @EventHandler
   public void onQuit(PlayerQuitEvent event) {
      Player player = event.getPlayer();
      this.dirty.remove(player.getUniqueId());
      for (Map.Entry<Scoreboard, Map<UUID, Long>> entry : this.shown.entrySet()) {
         if (entry.getValue().remove(player.getUniqueId()) != null) {
            Objective objective = entry.getKey().getObjective(OBJECTIVE);
            if (objective != null) {
               Score score = objective.getScore(player);
               if (score.isScoreSet()) {
                  score.resetScore();
               }
            }
         }
      }
   }

   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onHit(EntityDamageByEntityEvent event) {
      if (this.on(Feature.HEALTH_ACTION_BAR)
         && event.getEntity() instanceof LivingEntity victim
         && !(victim instanceof ArmorStand)
         && !victim.hasMetadata("NPC")) {
         Player attacker = this.plugin.attacker(event);
         if (attacker != null && !attacker.equals(victim)) {
            // Sent once the damage is applied. A blast that hits ten players sends one bar, not ten.
            this.bars.put(attacker, victim);
            if (!this.barsQueued) {
               this.barsQueued = true;
               Bukkit.getScheduler().runTask(this.plugin, this::sendBars);
            }
         }
      }
   }

   private void sendBars() {
      this.barsQueued = false;
      for (Map.Entry<Player, LivingEntity> bar : this.bars.entrySet()) {
         if (bar.getKey().isOnline()) {
            bar.getKey().sendActionBar(healthBar(bar.getValue()));
         }
      }

      this.bars.clear();
   }

   static Component healthBar(LivingEntity victim) {
      String name = victim instanceof Player player ? player.getName() : Text.plain(victim.name());
      TextComponent.Builder bar = Component.text().append(Component.text(name + "  ", WHITE));
      return !victim.isDead() && (victim.isValid() || victim instanceof Player)
         ? bar.append(health(victim.getHealth(), victim.getAbsorptionAmount())).build()
         : bar.append(Component.text(Text.SKULL, HEART)).build();
   }
}
