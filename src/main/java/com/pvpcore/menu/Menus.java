package com.pvpcore.menu;

import com.pvpcore.PvPCore;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/** Opens menu pages and redraws every open one after a change, so two admins never see stale settings. */
public final class Menus {
   public static final String PERMISSION = "pvpcore.admin";
   private final PvPCore plugin;
   private final Set<Menu> open = ConcurrentHashMap.newKeySet();

   public Menus(PvPCore plugin) {
      this.plugin = plugin;
   }

   /** Opens on the next tick: opening an inventory from inside a click is unreliable. */
   public void open(Player player, Menu menu) {
      Bukkit.getScheduler().runTask(this.plugin, () -> {
         if (player.isOnline() && player.hasPermission(PERMISSION)) {
            menu.render();
            this.open.add(menu);
            player.openInventory(menu.getInventory());
         }
      });
   }

   public void openHub(Player player) {
      this.open(player, new HubMenu(this.plugin, player));
   }

   public void openRules(Player player) {
      this.open(player, new RulesMenu(this.plugin, player, 0));
   }

   public void openPresets(Player player) {
      this.open(player, new PresetMenu(this.plugin, player));
   }

   public void openCategory(Player player, com.pvpcore.Feature.Category category) {
      this.open(player, new CategoryMenu(this.plugin, player, category));
   }

   void closed(Menu menu) {
      this.open.remove(menu);
   }

   /** The menu page showing in this inventory, if it is one of ours. Never touches the inventory's holder. */
   public Menu find(org.bukkit.inventory.Inventory inventory) {
      if (inventory == null || this.open.isEmpty()) {
         return null;
      }

      for (Menu menu : this.open) {
         if (menu.getInventory().equals(inventory)) {
            return menu;
         }
      }

      return null;
   }

   public void refreshAll() {
      for (Menu menu : new ArrayList<>(this.open)) {
         if (menu.viewer.isOnline() && menu.viewer.getOpenInventory().getTopInventory().equals(menu.getInventory())) {
            menu.render();
         } else {
            this.open.remove(menu);
         }
      }
   }

   public void closeAll() {
      for (Menu menu : new ArrayList<>(this.open)) {
         if (menu.viewer.isOnline() && menu.viewer.getOpenInventory().getTopInventory().equals(menu.getInventory())) {
            menu.viewer.closeInventory();
         }
      }

      this.open.clear();
   }
}
