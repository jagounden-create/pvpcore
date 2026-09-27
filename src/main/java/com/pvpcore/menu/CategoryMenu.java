package com.pvpcore.menu;

import com.pvpcore.Feature;
import com.pvpcore.PvPCore;
import com.pvpcore.Settings;
import com.pvpcore.util.Text;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

/**
 * One game mode's switches. Each section is a row of icons with a status pane under each one:
 * green on, red off, orange when this server can't run it. Click either to toggle.
 */
final class CategoryMenu extends Menu {
   private final Feature.Category category;

   CategoryMenu(PvPCore plugin, Player viewer, Feature.Category category) {
      super(plugin, viewer);
      this.category = category;
   }

   @Override
   protected int rows() {
      return 2 + 2 * this.category.sections().size();
   }

   @Override
   protected Component title() {
      return new Style(this.plugin.settings()).title(this.category.label());
   }

   @Override
   protected void draw() {
      List<Feature.Section> sections = this.category.sections();
      for (int i = 0; i < sections.size(); i++) {
         List<Feature> features = sections.get(i).features();
         int[] slots = Layout.centered((1 + 2 * i) * 9, features.size());
         for (int j = 0; j < features.size(); j++) {
            Feature feature = features.get(j);
            Status.State state = Status.of(this.plugin, feature);
            Button toggle = (player, click) -> this.clickFeature(player, feature, click);
            this.button(slots[j], this.icon(feature, state), toggle);
            this.button(slots[j] + 9, this.indicator(feature, state), toggle);
         }
      }

      int back = (this.rows() - 1) * 9 + 4;
      this.button(back, Items.icon(Material.ARROW, this.style.name("Back", true), List.of(), false), (player, click) -> {
         sound(player, 0.9F);
         this.open(new HubMenu(this.plugin, this.viewer));
      });
   }

   private ItemStack icon(Feature feature, Status.State state) {
      Settings settings = this.plugin.settings();
      boolean active = state.on() && state.available();
      List<Component> lore = new ArrayList<>();
      lore.add(Text.mm(Style.MUTED + this.style.caps(feature.section().label())));
      for (String line : feature.description()) {
         lore.add(this.style.line(line));
      }

      Feature.Value value = feature.value();
      if (value != null) {
         lore.add(Component.empty());
         String now = value.unit().format(settings.value(feature));
         if (value.vanilla() != null) {
            lore.add(this.style.label("Value", now, "Vanilla", value.vanilla()));
         } else {
            lore.add(this.style.label("Value", now));
         }
      }

      lore.add(Component.empty());
      lore.add(this.style.status(state));
      if (!state.available()) {
         lore.add(this.style.line(state.reason()));
      }

      for (String note : state.notes()) {
         lore.add(this.style.warn(note));
      }

      lore.add(Component.empty());
      lore.add(this.style.hint("Click", state.on() ? "turn off" : "turn on"));
      if (value != null) {
         String step = value.unit().step(value.step());
         lore.add(this.style.hint("Right", "+" + step, "Shift-right", Text.MINUS + step));
      }

      return Items.icon(feature.icon(), this.style.name(feature.title(), active), lore, active);
   }

   private ItemStack indicator(Feature feature, Status.State state) {
      Material pane = !state.available() ? Material.ORANGE_STAINED_GLASS_PANE
         : state.on() ? Material.LIME_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE;
      List<Component> lore = new ArrayList<>();
      if (feature.value() != null) {
         lore.add(this.style.label("Value", feature.value().unit().format(this.plugin.settings().value(feature))));
      }

      lore.add(this.style.hint("Click", state.on() ? "turn off" : "turn on"));
      return Items.icon(pane, this.style.status(state), lore, false);
   }

   private void clickFeature(Player player, Feature feature, ClickType click) {
      Settings settings = this.plugin.settings();
      String change;
      float pitch;
      if (click == ClickType.LEFT || click == ClickType.SHIFT_LEFT) {
         boolean on = !settings.on(feature);
         settings.set(feature, on);
         change = "turned " + feature.title() + (on ? " on" : " off");
         pitch = on ? 1.4F : 0.8F;
      } else if ((click == ClickType.RIGHT || click == ClickType.SHIFT_RIGHT) && feature.value() != null) {
         Feature.Value value = feature.value();
         double before = settings.value(feature);
         double after = settings.setValue(feature, before + (click == ClickType.SHIFT_RIGHT ? -value.step() : value.step()));
         if (Math.abs(after - before) < 1.0E-9) {
            deny(player);
            return;
         }

         change = "set " + feature.title() + " to " + value.unit().format(after);
         pitch = after > before ? 1.2F : 1.0F;
      } else {
         return;
      }

      this.plugin.applyAll();
      this.plugin.getLogger().info(player.getName() + " " + change + ".");
      sound(player, pitch);
   }
}
