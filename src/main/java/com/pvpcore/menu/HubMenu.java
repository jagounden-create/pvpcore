package com.pvpcore.menu;

import com.pvpcore.Compat;
import com.pvpcore.Feature;
import com.pvpcore.Integrations;
import com.pvpcore.PvPCore;
import com.pvpcore.ServerTweaks;
import com.pvpcore.Settings;
import com.pvpcore.util.Bedrock;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

/**
 * The first page: the five game-mode pages in one row, and Item Rules, Overview and Presets below.
 */
final class HubMenu extends Menu {
   static final int RULES = 20;
   static final int OVERVIEW = 22;
   static final int PRESETS = 24;

   HubMenu(PvPCore plugin, Player viewer) {
      super(plugin, viewer);
   }

   @Override
   protected int rows() {
      return 3;
   }

   @Override
   protected Component title() {
      return new Style(this.plugin.settings()).title(this.plugin.settings().menuTitle());
   }

   @Override
   protected void draw() {
      Feature.Category[] categories = Feature.Category.values();
      int[] slots = Layout.centered(9, categories.length);
      for (int i = 0; i < categories.length; i++) {
         Feature.Category category = categories[i];
         this.button(slots[i], this.categoryIcon(category), (player, click) -> {
            sound(player, 1.0F);
            this.open(new CategoryMenu(this.plugin, this.viewer, category));
         });
      }

      this.button(RULES, this.rulesIcon(), (player, click) -> {
         sound(player, 1.0F);
         this.open(new RulesMenu(this.plugin, this.viewer, 0));
      });
      this.button(OVERVIEW, this.overviewIcon(), this::overviewClick);
      this.button(PRESETS, Items.icon(Material.BOOK, this.style.name("Presets", true), List.of(
         this.style.line("One-click setups for Diamond SMP,"),
         this.style.line("Sword, Mace, Cart or pure vanilla."),
         Component.empty(),
         this.style.hint("Click", "to open")
      ), false), (player, click) -> {
         sound(player, 1.0F);
         this.open(new PresetMenu(this.plugin, this.viewer));
      });
   }

   private org.bukkit.inventory.ItemStack categoryIcon(Feature.Category category) {
      Settings settings = this.plugin.settings();
      int on = settings.enabledCount(category);
      int total = category.features().size();
      List<Component> lore = new ArrayList<>();
      lore.add(this.style.line(category.blurb()));
      lore.add(Component.empty());
      lore.add(this.style.label("On", on + " / " + total));
      lore.add(Component.empty());
      lore.add(this.style.hint("Click", "to open"));
      return Items.icon(category.icon(), this.style.name(category.label(), true), lore, false);
   }

   private org.bukkit.inventory.ItemStack rulesIcon() {
      boolean on = this.plugin.settings().on(Feature.ITEM_RULES);
      List<Component> lore = new ArrayList<>();
      lore.add(this.style.line("Nerf, cooldown, disable or limit"));
      lore.add(this.style.line("any item or block."));
      lore.add(Component.empty());
      lore.add(this.style.label("Rules", Integer.toString(this.plugin.rules().all().size())));
      if (!on) {
         lore.add(this.style.warn("Item Rules are switched off."));
      }

      if (!this.plugin.rules().writable()) {
         lore.add(this.style.warn("rules.yml has an error - not saving."));
      }

      lore.add(Component.empty());
      lore.add(this.style.hint("Click", "to open"));
      return Items.icon(Material.SMITHING_TABLE, this.style.name("Item Rules", on), lore, false);
   }

   private org.bukkit.inventory.ItemStack overviewIcon() {
      Settings settings = this.plugin.settings();
      ServerTweaks tweaks = this.plugin.tweaks();
      List<Component> lore = new ArrayList<>();
      lore.add(this.style.label("Server", Bukkit.getName() + " " + Compat.serverVersion()));
      lore.add(this.style.label("Version", this.plugin.version()));
      lore.add(this.style.label("Switches on", settings.enabledCount() + " / " + Feature.values().length));
      lore.add(Component.empty());
      lore.add(this.style.label("Shields", this.plugin.modernShields() ? "data-driven" : "compatibility mode"));
      lore.add(this.style.label("Stun follow-up", !tweaks.shieldStunAvailable() ? "held open by plugin" : tweaks.shieldStunActive() ? "Paper switch on" : "off"));
      lore.add(this.style.label("Attribute swap", !tweaks.attributeSwappingAvailable() ? "unavailable" : tweaks.attributeSwappingActive() ? "on" : "off"));
      lore.add(this.style.label("Instant KB", !tweaks.instantKnockbackAvailable() ? "needs Leaf" : tweaks.instantKnockbackActive() ? "on" : "off"));
      lore.add(this.style.label("Particles", this.plugin.particlesAvailable() ? "filter ready" : "unavailable"));
      lore.add(this.style.label("Bedrock", Bedrock.source()));
      if (Integrations.knockbackSync()) {
         lore.add(this.style.label("KnockbackSync", "installed"));
      }

      if (!settings.writable()) {
         lore.add(Component.empty());
         lore.add(this.style.warn("config.yml has an error:"));
         lore.add(this.style.warn(shorten(settings.loadError())));
      }

      lore.add(Component.empty());
      lore.add(this.style.hint("Shift-click", "to reload the config"));
      return Items.icon(Material.NETHER_STAR, this.style.name("Overview", true), lore, false);
   }

   private void overviewClick(Player player, ClickType click) {
      if (!click.isShiftClick()) {
         deny(player);
         return;
      }

      boolean clean = this.plugin.reload();
      this.plugin.getLogger().info(player.getName() + " reloaded the config from the menu.");
      player.sendActionBar(this.style.name(clean ? "Reloaded" : "Reloaded - check the console for errors", clean));
      sound(player, clean ? 1.2F : 0.7F);
   }

   static String shorten(String text) {
      if (text == null) {
         return "";
      }

      return text.length() <= 40 ? text : text.substring(0, 39) + "...";
   }
}
