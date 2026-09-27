package com.pvpcore.menu;

import com.pvpcore.PvPCore;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

public final class MenuListener implements Listener {
   private final PvPCore plugin;

   public MenuListener(PvPCore plugin) {
      this.plugin = plugin;
   }

   @EventHandler(priority = EventPriority.HIGH)
   public void onClick(InventoryClickEvent event) {
      Inventory top = event.getView().getTopInventory();
      Menu menu = this.plugin.menus().find(top);
      if (menu == null) {
         return;
      }

      // Nothing moves in or out of a menu, whatever the click.
      event.setCancelled(true);
      if (!(event.getWhoClicked() instanceof Player player)) {
         return;
      }

      if (!player.hasPermission(Menus.PERMISSION)) {
         Bukkit.getScheduler().runTask(this.plugin, () -> player.closeInventory());
         return;
      }

      Inventory clicked = event.getClickedInventory();
      if (clicked == null) {
         return;
      }

      if (clicked.equals(top)) {
         menu.click(player, event.getSlot(), event.getClick());
      } else {
         // Read the slot itself: the clicked inventory and index can't disagree with each other.
         menu.clickOwn(player, clicked.getItem(event.getSlot()), event.getClick());
      }
   }

   @EventHandler(priority = EventPriority.HIGH)
   public void onDrag(InventoryDragEvent event) {
      if (this.plugin.menus().find(event.getView().getTopInventory()) != null) {
         event.setCancelled(true);
      }
   }

   @EventHandler
   public void onClose(InventoryCloseEvent event) {
      Menu menu = this.plugin.menus().find(event.getInventory());
      if (menu != null) {
         this.plugin.menus().closed(menu);
      }
   }
}
