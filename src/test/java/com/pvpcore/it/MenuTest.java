package com.pvpcore.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.pvpcore.Feature;
import com.pvpcore.menu.Menu;
import com.pvpcore.rules.Rule;
import java.nio.file.Files;
import java.io.File;
import java.io.IOException;
import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class MenuTest extends PluginTest {
   PlayerMock openHub() {
      PlayerMock admin = this.admin();
      admin.performCommand("pvpcore");
      this.ticks(1);
      assertNotNull(this.menu(admin), "hub did not open");
      return admin;
   }

   @Test
   void hubShowsEveryCategoryAndTheTools() {
      PlayerMock admin = this.openHub();
      Inventory hub = admin.getOpenInventory().getTopInventory();
      assertEquals(27, hub.getSize());
      int[] categorySlots = {11, 12, 13, 14, 15};
      Feature.Category[] categories = Feature.Category.values();
      for (int i = 0; i < categories.length; i++) {
         assertEquals(categories[i].icon(), hub.getItem(categorySlots[i]).getType(), categories[i].label());
      }

      assertEquals(Material.SMITHING_TABLE, hub.getItem(20).getType());
      assertEquals(Material.NETHER_STAR, hub.getItem(22).getType());
      assertEquals(Material.BOOK, hub.getItem(24).getType());
      // everything else is filler
      assertEquals(Material.BLACK_STAINED_GLASS_PANE, hub.getItem(0).getType());
   }

   @Test
   void everyCategoryPageRendersEverySwitchWithAStatusPane() {
      for (Feature.Category category : Feature.Category.values()) {
         PlayerMock admin = this.openHub();
         this.click(admin, 11 + category.ordinal(), ClickType.LEFT);
         Inventory page = admin.getOpenInventory().getTopInventory();
         assertEquals((2 + 2 * category.sections().size()) * 9, page.getSize(), category.label());
         int icons = 0;
         int panes = 0;
         for (ItemStack item : page.getContents()) {
            if (item == null) {
               continue;
            }

            for (Feature feature : category.features()) {
               if (item.getType() == feature.icon()) {
                  icons++;
               }
            }

            if (item.getType() == Material.LIME_STAINED_GLASS_PANE || item.getType() == Material.RED_STAINED_GLASS_PANE
               || item.getType() == Material.ORANGE_STAINED_GLASS_PANE) {
               panes++;
            }
         }

         assertEquals(category.features().size(), icons, category.label() + " icons");
         assertEquals(category.features().size(), panes, category.label() + " status panes");
      }
   }

   @Test
   void clickingTogglesAndRightClickingStepsTheValue() throws IOException {
      PlayerMock admin = this.openHub();
      this.click(admin, 11, ClickType.LEFT); // Sword
      Inventory page = admin.getOpenInventory().getTopInventory();
      int slot = slotOf(page, Feature.KNOCKBACK_DISTANCE.icon());
      assertTrue(this.plugin.settings().on(Feature.KNOCKBACK_DISTANCE));

      this.click(admin, slot, ClickType.LEFT);
      assertFalse(this.plugin.settings().on(Feature.KNOCKBACK_DISTANCE));
      assertEquals(Material.RED_STAINED_GLASS_PANE, page.getItem(slot + 9).getType(), "status pane follows");

      this.click(admin, slot + 9, ClickType.LEFT); // the pane toggles too
      assertTrue(this.plugin.settings().on(Feature.KNOCKBACK_DISTANCE));

      this.click(admin, slot, ClickType.RIGHT);
      assertEquals(0.95, this.plugin.settings().value(Feature.KNOCKBACK_DISTANCE), 1.0E-9);
      this.click(admin, slot, ClickType.SHIFT_RIGHT);
      this.click(admin, slot, ClickType.SHIFT_RIGHT);
      assertEquals(0.85, this.plugin.settings().value(Feature.KNOCKBACK_DISTANCE), 1.0E-9);

      String saved = Files.readString(new File(this.plugin.getDataFolder(), "config.yml").toPath());
      assertTrue(saved.contains("factor: 0.85"), "the change was saved");
   }

   @Test
   void backReturnsToTheHub() {
      PlayerMock admin = this.openHub();
      this.click(admin, 12, ClickType.LEFT); // Mace: 1 section -> 4 rows, back at 31
      assertEquals(36, admin.getOpenInventory().getTopInventory().getSize());
      this.click(admin, 31, ClickType.LEFT);
      assertEquals(27, admin.getOpenInventory().getTopInventory().getSize());
   }

   @Test
   void menuItemsNeverMove() {
      PlayerMock admin = this.openHub();
      ItemStack before = admin.getOpenInventory().getTopInventory().getItem(11).clone();
      admin.simulateInventoryClick(admin.getOpenInventory(), ClickType.SHIFT_LEFT, 11);
      assertEquals(before, admin.getOpenInventory().getTopInventory().getItem(11));
      assertTrue(admin.getInventory().isEmpty(), "nothing was taken into the player's inventory");
   }

   @Test
   void presetsNeedAShiftClick() {
      PlayerMock admin = this.openHub();
      this.click(admin, 24, ClickType.LEFT);
      Inventory presets = admin.getOpenInventory().getTopInventory();
      int vanilla = slotOf(presets, Material.GRASS_BLOCK);
      this.click(admin, vanilla, ClickType.LEFT);
      assertTrue(this.plugin.settings().on(Feature.SHIELD_STUN), "a plain click does nothing");
      this.click(admin, vanilla, ClickType.SHIFT_LEFT);
      assertEquals(0, this.plugin.settings().enabledCount());
   }

   @Test
   void addingEditingAndRemovingARuleFromTheMenu() throws IOException {
      PlayerMock admin = this.openHub();
      this.click(admin, 20, ClickType.LEFT);
      admin.getInventory().setItem(0, new ItemStack(Material.ENDER_PEARL, 16));
      int topSize = admin.getOpenInventory().getTopInventory().getSize();
      // raw slot of hotbar slot 0 in a 54-slot view: 54 + 27
      this.click(admin, topSize + 27, ClickType.LEFT);
      assertNotNull(this.plugin.rules().get(Material.ENDER_PEARL), "rule created from own inventory click");
      Menu editor = this.menu(admin);
      assertNotNull(editor);
      Inventory page = admin.getOpenInventory().getTopInventory();
      assertEquals(45, page.getSize());

      int cooldown = slotOf(page, Material.CLOCK);
      this.click(admin, cooldown, ClickType.SHIFT_LEFT); // +5s
      this.click(admin, cooldown, ClickType.LEFT);       // +0.5s
      assertEquals(5.5, this.plugin.rules().get(Material.ENDER_PEARL).cooldown(), 1.0E-9);
      this.click(admin, cooldown, ClickType.RIGHT);
      assertEquals(5.0, this.plugin.rules().get(Material.ENDER_PEARL).cooldown(), 1.0E-9);
      this.ticks(1);
      String saved = Files.readString(new File(this.plugin.getDataFolder(), "rules.yml").toPath());
      assertTrue(saved.contains("ENDER_PEARL") && saved.contains("cooldown: 5.0"), saved);

      // ender pearls are not blocks: no block limit controls
      assertEquals(-1, slotOf(page, Material.MAP));

      this.click(admin, 44, ClickType.LEFT);
      assertNotNull(this.plugin.rules().get(Material.ENDER_PEARL), "a plain click does not remove");
      this.click(admin, 44, ClickType.SHIFT_LEFT);
      assertNull(this.plugin.rules().get(Material.ENDER_PEARL));
      assertEquals(54, admin.getOpenInventory().getTopInventory().getSize(), "back on the rules list");
   }

   @Test
   void blocksGetLimitControls() {
      this.plugin.rules().put(Rule.empty(Material.COBWEB));
      PlayerMock admin = this.openHub();
      this.click(admin, 20, ClickType.LEFT);
      this.click(admin, 10, ClickType.LEFT); // first rule
      Inventory page = admin.getOpenInventory().getTopInventory();
      int chunk = slotOf(page, Material.MAP);
      assertTrue(chunk >= 0);
      this.click(admin, chunk, ClickType.SHIFT_LEFT);
      assertEquals(10, this.plugin.rules().get(Material.COBWEB).chunkLimit());
      int disable = slotOf(page, Material.LIME_STAINED_GLASS_PANE);
      this.click(admin, disable, ClickType.LEFT);
      assertTrue(this.plugin.rules().get(Material.COBWEB).disabled());
   }

   @Test
   void playersWithoutPermissionCannotOpenIt() {
      PlayerMock player = this.server.addPlayer();
      player.performCommand("pvpcore");
      this.ticks(1);
      assertNull(this.menu(player));
   }

   static int slotOf(Inventory inventory, Material material) {
      for (int slot = 0; slot < inventory.getSize(); slot++) {
         ItemStack item = inventory.getItem(slot);
         if (item != null && item.getType() == material) {
            return slot;
         }
      }

      return -1;
   }
}
