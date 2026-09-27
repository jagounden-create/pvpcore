package com.pvpcore.menu;

import com.pvpcore.PvPCore;
import java.util.HashMap;
import java.util.Map;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/** One open menu page for one player. Pages redraw in place, so they never flicker or reset the cursor. */
public abstract class Menu implements InventoryHolder {
   protected final PvPCore plugin;
   protected final Player viewer;
   private final Map<Integer, Button> buttons = new HashMap<>();
   private Inventory inventory;
   protected Style style;

   protected Menu(PvPCore plugin, Player viewer) {
      this.plugin = plugin;
      this.viewer = viewer;
   }

   protected abstract int rows();

   protected abstract Component title();

   /** Places this page's items with {@link #button}. The filler is already down. */
   protected abstract void draw();

   @Override
   public @NotNull Inventory getInventory() {
      if (this.inventory == null) {
         this.style = new Style(this.plugin.settings());
         this.inventory = Bukkit.createInventory(this, this.rows() * 9, this.title());
      }

      return this.inventory;
   }

   final void render() {
      Inventory inventory = this.getInventory();
      this.style = new Style(this.plugin.settings());
      this.buttons.clear();
      ItemStack filler = Items.filler(this.plugin.settings().filler());
      ItemStack[] contents = new ItemStack[inventory.getSize()];
      for (int slot = 0; slot < contents.length; slot++) {
         contents[slot] = filler;
      }

      inventory.setContents(contents);
      this.draw();
   }

   protected final void button(int slot, ItemStack item, Button action) {
      this.inventory.setItem(slot, item);
      if (action != null) {
         this.buttons.put(slot, action);
      } else {
         this.buttons.remove(slot);
      }
   }

   final void click(Player player, int slot, ClickType click) {
      Button button = this.buttons.get(slot);
      if (button != null) {
         button.click(player, click);
      }
   }

   /** A click on an item in the player's own inventory while this page is open. */
   void clickOwn(Player player, ItemStack item, ClickType click) {
   }

   protected final void open(Menu next) {
      this.plugin.menus().open(this.viewer, next);
   }

   protected static void sound(Player player, float pitch) {
      player.playSound(player, Sound.UI_BUTTON_CLICK, SoundCategory.MASTER, 0.5F, pitch);
   }

   protected static void deny(Player player) {
      player.playSound(player, Sound.BLOCK_NOTE_BLOCK_BASS, SoundCategory.MASTER, 0.6F, 0.7F);
   }

   @FunctionalInterface
   public interface Button {
      void click(Player player, ClickType click);
   }
}
