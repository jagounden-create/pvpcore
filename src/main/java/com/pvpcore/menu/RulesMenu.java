package com.pvpcore.menu;

import com.pvpcore.Feature;
import com.pvpcore.PvPCore;
import com.pvpcore.rules.Rule;
import com.pvpcore.util.Text;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

/**
 * Every item rule, 28 to a page. Click any item in your own inventory to add a rule for it.
 */
final class RulesMenu extends Menu {
   static final int PER_PAGE = 28;
   static final int PREVIOUS = 45;
   static final int BACK = 48;
   static final int ADD = 49;
   static final int SWITCH = 50;
   static final int NEXT = 53;
   private int page;

   RulesMenu(PvPCore plugin, Player viewer, int page) {
      super(plugin, viewer);
      this.page = Math.max(0, page);
   }

   @Override
   protected int rows() {
      return 6;
   }

   @Override
   protected Component title() {
      return new Style(this.plugin.settings()).title("Item Rules");
   }

   /** The 28 slots inside the border: rows 1 to 4, columns 1 to 7. */
   static int slot(int index) {
      return (1 + index / 7) * 9 + 1 + index % 7;
   }

   @Override
   protected void draw() {
      List<Rule> rules = this.plugin.rules().sorted();
      int pages = Math.max(1, (rules.size() + PER_PAGE - 1) / PER_PAGE);
      this.page = Math.min(this.page, pages - 1);
      int start = this.page * PER_PAGE;
      for (int i = 0; i < PER_PAGE && start + i < rules.size(); i++) {
         Rule rule = rules.get(start + i);
         this.button(slot(i), this.ruleIcon(rule), (player, click) -> {
            sound(player, 1.0F);
            this.open(new RuleEditorMenu(this.plugin, this.viewer, rule.material(), this.page));
         });
      }

      if (rules.isEmpty()) {
         this.button(22, Items.icon(Material.PAPER, this.style.name("No rules yet", false), List.of(
            this.style.line("Click any item in your inventory"),
            this.style.line("below to nerf it, give it a"),
            this.style.line("cooldown or limit it.")
         ), false), null);
      }

      if (this.page > 0) {
         this.button(PREVIOUS, Items.icon(Material.ARROW, this.style.name("Previous page", true), List.of(), false), (player, click) -> {
            this.page--;
            sound(player, 1.0F);
            this.render();
         });
      }

      if (this.page < pages - 1) {
         this.button(NEXT, Items.icon(Material.ARROW, this.style.name("Next page", true), List.of(), false), (player, click) -> {
            this.page++;
            sound(player, 1.0F);
            this.render();
         });
      }

      this.button(BACK, Items.icon(Material.ARROW, this.style.name("Back", true), List.of(), false), (player, click) -> {
         sound(player, 0.9F);
         this.open(new HubMenu(this.plugin, this.viewer));
      });
      this.button(ADD, Items.icon(Material.ANVIL, this.style.name("Add a rule", true), List.of(
         this.style.line("Click any item in your own"),
         this.style.line("inventory, or click here to add"),
         this.style.line("the item in your hand."),
         Component.empty(),
         this.style.hint("Click", "add the held item")
      ), false), (player, click) -> this.add(player, player.getInventory().getItemInMainHand()));

      Status.State state = Status.of(this.plugin, Feature.ITEM_RULES);
      Material pane = state.on() ? Material.LIME_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE;
      List<Component> lore = new ArrayList<>();
      if (!this.plugin.rules().writable()) {
         lore.add(this.style.warn("rules.yml has an error, so changes"));
         lore.add(this.style.warn("here are not saved. Fix it, then"));
         lore.add(this.style.warn("/pvpcore reload."));
      }

      lore.add(this.style.hint("Click", state.on() ? "switch all rules off" : "switch rules on"));
      this.button(SWITCH, Items.icon(pane, this.style.status(state), lore, false), (player, click) -> {
         boolean on = !this.plugin.settings().on(Feature.ITEM_RULES);
         this.plugin.settings().set(Feature.ITEM_RULES, on);
         this.plugin.applyAll();
         this.plugin.getLogger().info(player.getName() + " turned Item Rules " + (on ? "on." : "off."));
         sound(player, on ? 1.4F : 0.8F);
      });
   }

   @Override
   void clickOwn(Player player, ItemStack item, ClickType click) {
      this.add(player, item);
   }

   private void add(Player player, ItemStack item) {
      if (item == null || item.getType().isAir()) {
         deny(player);
         return;
      }

      Material material = item.getType();
      if (this.plugin.rules().get(material) == null) {
         this.plugin.rules().put(Rule.empty(material));
         this.plugin.getLogger().info(player.getName() + " added an item rule for " + Text.pretty(material) + ".");
      }

      sound(player, 1.2F);
      this.open(new RuleEditorMenu(this.plugin, this.viewer, material, this.page));
   }

   private ItemStack ruleIcon(Rule rule) {
      List<Component> lore = RuleEditorMenu.summary(this.style, rule);
      lore.add(Component.empty());
      lore.add(this.style.hint("Click", "to edit"));
      Component name = Text.itemName(rule.material()).color(net.kyori.adventure.text.format.TextColor.fromHexString(this.plugin.settings().accent()))
         .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false);
      return Items.icon(rule.material(), name, lore, rule.active());
   }
}
