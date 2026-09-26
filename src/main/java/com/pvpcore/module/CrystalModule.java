package com.pvpcore.module;

import com.pvpcore.Feature;
import com.pvpcore.Module;
import com.pvpcore.PvPCore;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Player;
import org.bukkit.event.Event.Result;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityDamageByBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

public final class CrystalModule extends Module {
   public static final String BYPASS = "pvpcore.bypass.crystals";
   private static final long NOTICE_GAP_MS = 1000L;
   private final Map<UUID, Long> lastNotice = new ConcurrentHashMap<>();

   public CrystalModule(PvPCore plugin) {
      super(plugin);
   }

   private boolean banned(Feature feature, World world) {
      return world != null && this.on(feature) && this.settings().crystalWorld(world);
   }

   @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
   public void onUseCrystal(PlayerInteractEvent event) {
      ItemStack item = event.getItem();
      if (event.getAction() == Action.RIGHT_CLICK_BLOCK && item != null && item.getType() == Material.END_CRYSTAL) {
         Player player = event.getPlayer();
         if (this.banned(Feature.BAN_CRYSTALS, player.getWorld()) && !player.hasPermission(BYPASS)) {
            event.setUseItemInHand(Result.DENY);
            this.notice(player, "crystal-disabled");
         }
      }
   }

   @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
   public void onPlaceCrystal(EntityPlaceEvent event) {
      if (event.getEntity() instanceof EnderCrystal) {
         Player player = event.getPlayer();
         if (this.banned(Feature.BAN_CRYSTALS, event.getEntity().getWorld()) && (player == null || !player.hasPermission(BYPASS))) {
            event.setCancelled(true);
         }
      }
   }

   @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
   public void onDamage(EntityDamageEvent event) {
      if (event.getEntity() instanceof EnderCrystal crystal) {
         if (this.banned(Feature.BAN_CRYSTALS, crystal.getWorld())) {
            event.setCancelled(true);
         }
      } else if (event instanceof EntityDamageByEntityEvent byEntity
         && byEntity.getDamager() instanceof EnderCrystal crystal
         && this.banned(Feature.BAN_CRYSTALS, crystal.getWorld())) {
         event.setCancelled(true);
      } else if (event instanceof EntityDamageByBlockEvent byBlock
         && isAnchor(byBlock.getDamagerBlockState())
         && this.banned(Feature.BAN_ANCHORS, event.getEntity().getWorld())) {
         event.setCancelled(true);
      }
   }

   @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
   public void onPrime(ExplosionPrimeEvent event) {
      if (event.getEntity() instanceof EnderCrystal crystal && this.banned(Feature.BAN_CRYSTALS, crystal.getWorld())) {
         event.setCancelled(true);
      }
   }

   @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
   public void onCrystalExplode(EntityExplodeEvent event) {
      if (event.getEntity() instanceof EnderCrystal crystal && this.banned(Feature.BAN_CRYSTALS, crystal.getWorld())) {
         event.setCancelled(true);
      }
   }

   @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
   public void onUseAnchor(PlayerInteractEvent event) {
      Block block = event.getClickedBlock();
      if (event.getAction() == Action.RIGHT_CLICK_BLOCK && block != null && block.getType() == Material.RESPAWN_ANCHOR) {
         World world = block.getWorld();
         // Anchors only explode where they can't set a spawn point, so the Nether keeps working normally.
         if (!world.isRespawnAnchorWorks() && this.banned(Feature.BAN_ANCHORS, world) && !event.getPlayer().hasPermission(BYPASS)) {
            event.setCancelled(true);
            this.notice(event.getPlayer(), "anchor-disabled");
         }
      }
   }

   @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
   public void onAnchorExplode(BlockExplodeEvent event) {
      if (isAnchor(event.getExplodedBlockState()) && this.banned(Feature.BAN_ANCHORS, event.getBlock().getWorld())) {
         event.setCancelled(true);
      }
   }

   private static boolean isAnchor(BlockState state) {
      return state != null && state.getType() == Material.RESPAWN_ANCHOR;
   }

   private void notice(Player player, String message) {
      long now = System.currentTimeMillis();
      Long last = this.lastNotice.get(player.getUniqueId());
      if (last == null || now - last >= NOTICE_GAP_MS) {
         this.lastNotice.put(player.getUniqueId(), now);
         this.plugin.actionBar(player, message);
      }
   }

   @EventHandler
   public void onQuit(PlayerQuitEvent event) {
      this.lastNotice.remove(event.getPlayer().getUniqueId());
   }
}
