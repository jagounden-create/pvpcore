package com.pvpcore.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;

public final class Text {
   public static final String GOOD = "#4ADE80";
   public static final String BAD = "#F87171";
   public static final String WARN = "#FBBF24";
   public static final String MUTED = "#71717A";
   public static final String SOFT = "#A1A1AA";
   public static final String HEART = new String(Character.toChars(10084));
   public static final String DOT = new String(Character.toChars(9679));
   public static final String SKULL = new String(Character.toChars(9760));
   public static final String SWORDS = new String(Character.toChars(9876));
   public static final String ARROW_RIGHT = new String(Character.toChars(8250));
   public static final String MINUS = new String(Character.toChars(8722));
   private static final int[] SMALL_CAPS = new int[]{
      7424, 665, 7428, 7429, 7431, 42800, 610, 668, 618, 7434, 7435, 671, 7437, 628, 7439, 7448, 491, 640, 42801, 7451, 7452, 7456, 7457, 120, 655, 7458
   };
   private static final MiniMessage MINI = MiniMessage.miniMessage();

   private Text() {
   }

   public static Component mm(String miniMessage) {
      return MINI.deserialize(miniMessage).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
   }

   /**
    * Parses MiniMessage with {@code <key>} placeholders. String values are inserted as plain text (never parsed),
    * component values as they are.
    */
   public static Component mm(String miniMessage, Map<String, ?> placeholders) {
      List<TagResolver> resolvers = new ArrayList<>(placeholders.size());
      for (Map.Entry<String, ?> entry : placeholders.entrySet()) {
         String key = entry.getKey().toLowerCase(Locale.ROOT);
         if (entry.getValue() instanceof ComponentLike component) {
            resolvers.add(Placeholder.component(key, component));
         } else {
            resolvers.add(Placeholder.unparsed(key, String.valueOf(entry.getValue())));
         }
      }

      return MINI.deserialize(miniMessage, TagResolver.resolver(resolvers)).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
   }

   /** The item's name in each player's own language. */
   public static Component itemName(Material material) {
      try {
         return Component.translatable(material.translationKey());
      } catch (RuntimeException | LinkageError e) {
         return Component.text(pretty(material));
      }
   }

   /** "ENCHANTED_GOLDEN_APPLE" -> "Enchanted Golden Apple", for logs and the console. */
   public static String pretty(Material material) {
      String[] words = material.name().toLowerCase(Locale.ROOT).split("_");
      StringBuilder out = new StringBuilder();
      for (String word : words) {
         if (!word.isEmpty()) {
            if (out.length() > 0) {
               out.append(' ');
            }

            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
         }
      }

      return out.toString();
   }

   /** Small caps for plain letters; MiniMessage tags are left alone. */
   public static String caps(String text) {
      StringBuilder out = new StringBuilder(text.length());
      boolean inTag = false;

      for (int i = 0; i < text.length(); i++) {
         char c = text.charAt(i);
         if (c == '<') {
            inTag = true;
         }

         if (inTag) {
            out.append(c);
            if (c == '>') {
               inTag = false;
            }
         } else {
            char lower = Character.toLowerCase(c);
            if (lower >= 'a' && lower <= 'z') {
               out.appendCodePoint(SMALL_CAPS[lower - 'a']);
            } else {
               out.append(c);
            }
         }
      }

      return out.toString();
   }

   public static String escape(String text) {
      return MINI.escapeTags(text);
   }

   public static String plain(Component component) {
      return PlainTextComponentSerializer.plainText().serialize(component);
   }

   public static String points(double healthPoints) {
      return Long.toString((long)Math.ceil(Math.max(0.0, healthPoints)));
   }

   public static String trim(double value) {
      String formatted = String.format(Locale.ROOT, "%.2f", value);
      if (formatted.indexOf('.') >= 0) {
         formatted = formatted.replaceAll("0+$", "");
         if (formatted.endsWith(".")) {
            formatted = formatted.substring(0, formatted.length() - 1);
         }
      }

      return formatted.equals("-0") ? "0" : formatted;
   }

   public static String id(Enum<?> constant) {
      return constant.name().toLowerCase(Locale.ROOT).replace('_', '-');
   }
}
