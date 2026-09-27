package com.pvpcore.menu;

import com.pvpcore.PvPCore;
import com.pvpcore.rules.Rule;
import com.pvpcore.util.Text;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

/**
 * One item's rule. Left-click raises a number, right-click lowers it; hold shift for bigger steps.
 */
final class RuleEditorMenu extends Menu {
   static final int ITEM = 13;
   static final int BACK = 40;
   static final int REMOVE = 44;
   private final Material material;
   private final int returnPage;

   RuleEditorMenu(PvPCore plugin, Player viewer, Material material, int returnPage) {
      super(plugin, viewer);
      this.material = material;
      this.returnPage = returnPage;
   }

   @Override
   protected int rows() {
      return 5;
   }

   @Override
   protected Component title() {
      return new Style(this.plugin.settings()).title("Rule").append(Component.text(" · ")).append(Text.itemName(this.material));
   }

   @Override
   protected void draw() {
      Rule rule = this.plugin.rules().get(this.material);
      if (rule == null) {
         rule = Rule.empty(this.material);
      }

      List<Component> summary = summary(this.style, rule);
      Component name = Text.itemName(this.material).color(TextColor.fromHexString(this.plugin.settings().accent())).decoration(TextDecoration.ITALIC, false);
      this.button(ITEM, Items.icon(this.material, name, summary, rule.active()), null);

      List<Control> controls = new ArrayList<>();
      controls.add(new Control(Material.IRON_SWORD, "Damage", format(rule.damage(), "x"), rule.damage() != 1.0,
         List.of("Damage dealt with it: melee, what", "it shoots, or its blast. 1 = normal."), "0.05", "0.25",
         (r, step) -> r.withDamage(r.damage() + step * 0.05)));
      controls.add(new Control(Material.ANVIL, "Max Damage", rule.maxDamage() > 0 ? format(rule.maxDamage(), " " + Text.HEART) : "off", rule.maxDamage() > 0,
         List.of("Most one hit with it can deal,", "before armor. 0 = no cap."), "0.5 " + Text.HEART, "5 " + Text.HEART,
         (r, step) -> r.withMaxDamage(r.maxDamage() + step * 0.5)));
      controls.add(new Control(Material.CLOCK, "Cooldown", rule.cooldown() > 0 ? format(rule.cooldown(), "s") : "off", rule.cooldown() > 0,
         List.of("Seconds before it can be used", "again. 0 = none."), "0.5s", "5s",
         (r, step) -> r.withCooldown(r.cooldown() + step * 0.5)));
      if (this.material.isBlock()) {
         controls.add(new Control(Material.MAP, "Chunk Limit", rule.chunkLimit() > 0 ? Integer.toString(rule.chunkLimit()) : "off", rule.chunkLimit() > 0,
            List.of("Most of this block one chunk", "can hold. 0 = no limit."), "1", "10",
            (r, step) -> r.withChunkLimit(r.chunkLimit() + step)));
         controls.add(new Control(Material.PLAYER_HEAD, "Player Limit", rule.playerLimit() > 0 ? Integer.toString(rule.playerLimit()) : "off", rule.playerLimit() > 0,
            List.of("Most of this block one player", "can have placed. 0 = no limit."), "1", "10",
            (r, step) -> r.withPlayerLimit(r.playerLimit() + step)));
      }

      int[] slots = Layout.centered(27, controls.size() + 1);
      for (int i = 0; i < controls.size(); i++) {
         Control control = controls.get(i);
         this.button(slots[i], this.controlIcon(control), (player, click) -> this.change(player, click, control));
      }

      boolean disabled = rule.disabled();
      this.button(slots[controls.size()], Items.icon(disabled ? Material.RED_STAINED_GLASS_PANE : Material.LIME_STAINED_GLASS_PANE,
         Text.mm((disabled ? Style.BAD : Style.GOOD) + Text.DOT + " " + this.style.caps(disabled ? "Disabled" : "Allowed")), List.of(
            this.style.line(disabled ? "Nobody can use this item." : "The item can be used."),
            Component.empty(),
            this.style.hint("Click", disabled ? "allow it" : "disable it")
         ), false), (player, click) -> this.save(player, this.current().withDisabled(!this.current().disabled()), disabled ? "allowed" : "disabled"));

      this.button(BACK, Items.icon(Material.ARROW, this.style.name("Back", true), List.of(), false), (player, click) -> {
         sound(player, 0.9F);
         this.open(new RulesMenu(this.plugin, this.viewer, this.returnPage));
      });
      this.button(REMOVE, Items.icon(Material.LAVA_BUCKET, this.style.name("Remove Rule", false), List.of(
         this.style.line("The item goes back to normal."),
         Component.empty(),
         this.style.hint("Shift-click", "to remove")
      ), false), (player, click) -> {
         if (!click.isShiftClick()) {
            deny(player);
            return;
         }

         this.plugin.rules().remove(this.material);
         this.plugin.getLogger().info(player.getName() + " removed the item rule for " + Text.pretty(this.material) + ".");
         sound(player, 0.8F);
         this.plugin.menus().refreshAll();
         this.open(new RulesMenu(this.plugin, this.viewer, this.returnPage));
      });
   }

   private Rule current() {
      Rule rule = this.plugin.rules().get(this.material);
      return rule == null ? Rule.empty(this.material) : rule;
   }

   private ItemStack controlIcon(Control control) {
      List<Component> lore = new ArrayList<>();
      for (String line : control.description()) {
         lore.add(this.style.line(line));
      }

      lore.add(Component.empty());
      lore.add(this.style.label("Now", control.value()));
      lore.add(Component.empty());
      lore.add(this.style.hint("Left", "+" + control.small(), "Right", Text.MINUS + control.small()));
      lore.add(this.style.hint("Shift", "steps of " + control.big()));
      return Items.icon(control.icon(), this.style.name(control.title(), control.active()), lore, control.active());
   }

   private void change(Player player, ClickType click, Control control) {
      int step = switch (click) {
         case LEFT -> 1;
         case RIGHT -> -1;
         case SHIFT_LEFT -> big(control);
         case SHIFT_RIGHT -> -big(control);
         default -> 0;
      };
      if (step == 0) {
         return;
      }

      Rule before = this.current();
      Rule after = control.apply().apply(before, step);
      if (after.equals(before)) {
         deny(player);
         return;
      }

      this.save(player, after, "changed " + control.title().toLowerCase(java.util.Locale.ROOT));
      sound(player, step > 0 ? 1.2F : 1.0F);
   }

   /** Shift steps: 5 small steps for damage (0.25) and 10 for the rest (5 hearts, 5s, 10 blocks). */
   private static int big(Control control) {
      return control.title().equals("Damage") ? 5 : 10;
   }

   private void save(Player player, Rule rule, String what) {
      if (!this.plugin.rules().writable()) {
         player.sendActionBar(this.style.warn("rules.yml has an error - this change won't be saved."));
      }

      this.plugin.rules().put(rule);
      this.plugin.getLogger().info(player.getName() + " " + what + " for " + Text.pretty(this.material) + ".");
      this.plugin.menus().refreshAll();
   }

   static String format(double value, String unit) {
      return Text.trim(value) + unit;
   }

   /** The lines that describe a rule, used on its icon everywhere. */
   static List<Component> summary(Style style, Rule rule) {
      List<Component> lore = new ArrayList<>();
      if (rule.disabled()) {
         lore.add(Text.mm(Style.BAD + style.caps("Disabled")));
      }

      if (Math.abs(rule.damage() - 1.0) > 1.0E-9) {
         lore.add(style.label("Damage", format(rule.damage(), "x")));
      }

      if (rule.maxDamage() > 0.0) {
         lore.add(style.label("Max damage", format(rule.maxDamage(), " " + Text.HEART)));
      }

      if (rule.cooldown() > 0.0) {
         lore.add(style.label("Cooldown", format(rule.cooldown(), "s")));
      }

      if (rule.chunkLimit() > 0) {
         lore.add(style.label("Chunk limit", Integer.toString(rule.chunkLimit())));
      }

      if (rule.playerLimit() > 0) {
         lore.add(style.label("Player limit", Integer.toString(rule.playerLimit())));
      }

      if (!rule.worlds().isEmpty()) {
         lore.add(style.label("Worlds", String.join(", ", rule.worlds())));
      }

      if (lore.isEmpty()) {
         lore.add(style.line("No changes yet."));
      }

      return lore;
   }

   @FunctionalInterface
   interface Step {
      Rule apply(Rule rule, int steps);
   }

   record Control(Material icon, String title, String value, boolean active, List<String> description, String small, String big, Step apply) {
   }
}
