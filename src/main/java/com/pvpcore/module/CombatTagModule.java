package com.pvpcore.module;

import com.pvpcore.Feature;
import com.pvpcore.Module;
import com.pvpcore.PvPCore;
import com.pvpcore.util.Text;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

/**
 * Combat tag: a PvP hit tags both players for a while. Tagged players can't run the blocked commands, and logging out
 * while tagged kills them (their items drop like any death). A thin boss bar shows the time left.
 */
public final class CombatTagModule extends Module {
   public static final String BYPASS = "pvpcore.bypass.combattag";
   private final Map<UUID, Tag> tags = new HashMap<>();
   private BukkitTask ticker;

   public CombatTagModule(PvPCore plugin) {
      super(plugin);
   }

   @Override
   public void start() {
      this.ticker = Bukkit.getScheduler().runTaskTimer(this.plugin, this::tick, 5L, 5L);
   }

   @Override
   public void apply() {
      if (!this.on(Feature.COMBAT_TAG)) {
         this.clearAll();
      }
   }

   @Override
   public void stop() {
      if (this.ticker != null) {
         this.ticker.cancel();
      }

      this.clearAll();
   }

   public boolean tagged(Player player) {
      Tag tag = this.tags.get(player.getUniqueId());
      return tag != null && tag.until > Bukkit.getCurrentTick();
   }

   private void clearAll() {
      for (Map.Entry<UUID, Tag> entry : this.tags.entrySet()) {
         Player player = Bukkit.getPlayer(entry.getKey());
         if (player != null) {
            player.hideBossBar(entry.getValue().bar);
         }
      }

      this.tags.clear();
   }

   private void tick() {
      if (this.tags.isEmpty()) {
         return;
      }

      int now = Bukkit.getCurrentTick();
      int total = Math.max(1, (int)Math.round(this.settings().value(Feature.COMBAT_TAG) * 20.0));
      Iterator<Map.Entry<UUID, Tag>> iterator = this.tags.entrySet().iterator();

      while (iterator.hasNext()) {
         Map.Entry<UUID, Tag> entry = iterator.next();
         Tag tag = entry.getValue();
         Player player = Bukkit.getPlayer(entry.getKey());
         if (player == null) {
            iterator.remove();
            continue;
         }

         int left = tag.until - now;
         if (left <= 0) {
            player.hideBossBar(tag.bar);
            iterator.remove();
            this.plugin.send(player, "combat-end", Map.of());
            continue;
         }

         this.render(tag, left, total);
      }
   }

   private void render(Tag tag, int ticksLeft, int total) {
      int seconds = (int)Math.ceil(ticksLeft / 20.0);
      tag.bar.progress(Math.max(0.0F, Math.min(1.0F, ticksLeft / (float)total)));
      if (seconds != tag.shownSeconds) {
         tag.shownSeconds = seconds;
         tag.bar.name(this.barName(seconds));
      }
   }

   private Component barName(int seconds) {
      String text = this.settings().message("combat-bar");
      return Text.mm(text.isEmpty() ? "<white>In combat <gray>" + Text.DOT + " <white><seconds>s" : text, Map.of("seconds", Integer.toString(seconds)));
   }

   private void tag(Player player) {
      if (player.hasPermission(BYPASS) || player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
         return;
      }

      int now = Bukkit.getCurrentTick();
      int total = Math.max(1, (int)Math.round(this.settings().value(Feature.COMBAT_TAG) * 20.0));
      Tag tag = this.tags.get(player.getUniqueId());
      if (tag == null) {
         tag = new Tag(BossBar.bossBar(this.barName((int)Math.ceil(total / 20.0)), 1.0F, BossBar.Color.WHITE, BossBar.Overlay.PROGRESS));
         this.tags.put(player.getUniqueId(), tag);
         player.showBossBar(tag.bar);
         this.plugin.send(player, "combat-start", Map.of());
      }

      tag.until = now + total;
      this.render(tag, total, total);
   }

   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onHit(EntityDamageByEntityEvent event) {
      if (this.on(Feature.COMBAT_TAG) && event.getEntity() instanceof Player victim) {
         Player attacker = this.plugin.attacker(event);
         if (attacker != null && !attacker.equals(victim)) {
            this.tag(victim);
            this.tag(attacker);
         }
      }
   }

   @EventHandler(priority = EventPriority.MONITOR)
   public void onDeath(PlayerDeathEvent event) {
      Tag tag = this.tags.remove(event.getEntity().getUniqueId());
      if (tag != null) {
         event.getEntity().hideBossBar(tag.bar);
      }
   }

   @EventHandler(priority = EventPriority.LOWEST)
   public void onQuit(PlayerQuitEvent event) {
      Player player = event.getPlayer();
      Tag tag = this.tags.remove(player.getUniqueId());
      if (tag == null) {
         return;
      }

      player.hideBossBar(tag.bar);
      if (tag.until > Bukkit.getCurrentTick() && this.on(Feature.COMBAT_TAG) && this.settings().killOnLogout() && !player.isDead()) {
         String name = player.getName();
         player.setHealth(0.0);
         String broadcast = this.settings().message("combat-logout");
         if (!broadcast.isEmpty()) {
            Bukkit.broadcast(Text.mm(broadcast, Map.of("player", name)));
         }
      }
   }

   @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
   public void onCommand(PlayerCommandPreprocessEvent event) {
      Player player = event.getPlayer();
      if (!this.on(Feature.COMBAT_TAG) || !this.tagged(player)) {
         return;
      }

      if (blocked(event.getMessage(), this.settings().blockedCommands())) {
         event.setCancelled(true);
         this.plugin.send(player, "combat-command", Map.of());
      }
   }

   /** Whether "/label args" names a blocked command, with or without a "plugin:" prefix. */
   static boolean blocked(String message, List<String> blockedCommands) {
      String trimmed = message.trim();
      while (trimmed.startsWith("/")) {
         trimmed = trimmed.substring(1);
      }

      int space = trimmed.indexOf(' ');
      String label = (space < 0 ? trimmed : trimmed.substring(0, space)).toLowerCase(Locale.ROOT);
      int colon = label.indexOf(':');
      if (colon >= 0) {
         label = label.substring(colon + 1);
      }

      return !label.isEmpty() && blockedCommands.contains(label);
   }

   private static final class Tag {
      final BossBar bar;
      int until;
      int shownSeconds = -1;

      Tag(BossBar bar) {
         this.bar = bar;
      }
   }
}
