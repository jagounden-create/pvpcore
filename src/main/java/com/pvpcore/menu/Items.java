package com.pvpcore.menu;

import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/** Menu items: a name, a short lore, optional glint, and every vanilla tooltip line hidden. */
final class Items {
   private static final NamespacedKey MODIFIER = new NamespacedKey("pvpcore", "menu");
   private static volatile Attribute placeholder;
   private static volatile boolean placeholderResolved;

   private Items() {
   }

   static ItemStack icon(Material material, Component name, List<Component> lore, boolean glow) {
      ItemStack item = new ItemStack(material.isItem() && !material.isAir() ? material : Material.PAPER);
      ItemMeta meta = item.getItemMeta();
      if (meta == null) {
         return item;
      }

      meta.displayName(name);
      meta.lore(lore);
      hideTooltipExtras(meta);
      try {
         meta.setEnchantmentGlintOverride(glow);
      } catch (LinkageError ignored) {
         // no glint override on this version
      }

      item.setItemMeta(meta);
      return item;
   }

   static ItemStack filler(Material material) {
      if (material == null || material.isAir()) {
         return null;
      }

      ItemStack item = new ItemStack(material);
      ItemMeta meta = item.getItemMeta();
      if (meta == null) {
         return item;
      }

      meta.displayName(Component.space());
      hideTooltipExtras(meta);
      try {
         meta.setHideTooltip(true);
      } catch (LinkageError ignored) {
         // the blank name is enough on versions without it
      }

      item.setItemMeta(meta);
      return item;
   }

   /**
    * Hides attributes, enchantments, potion effects and the rest. Default attributes (a sword's damage) only hide
    * once the item has modifiers of its own, so a harmless zero luck modifier is added.
    */
   private static void hideTooltipExtras(ItemMeta meta) {
      Attribute attribute = placeholder();
      if (attribute != null) {
         try {
            meta.addAttributeModifier(attribute, new AttributeModifier(MODIFIER, 0.0, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.ANY));
         } catch (LinkageError | RuntimeException ignored) {
            // cosmetic only
         }
      }

      meta.addItemFlags(ItemFlag.values());
   }

   private static Attribute placeholder() {
      if (!placeholderResolved) {
         Attribute found = null;
         for (String key : new String[]{"luck", "generic.luck"}) {
            try {
               found = Registry.ATTRIBUTE.get(NamespacedKey.minecraft(key));
            } catch (LinkageError | RuntimeException ignored) {
               found = null;
            }

            if (found != null) {
               break;
            }
         }

         placeholder = found;
         placeholderResolved = true;
      }

      return placeholder;
   }
}
