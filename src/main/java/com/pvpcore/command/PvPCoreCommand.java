package com.pvpcore.command;

import com.pvpcore.Compat;
import com.pvpcore.Feature;
import com.pvpcore.Preset;
import com.pvpcore.PvPCore;
import com.pvpcore.Settings;
import com.pvpcore.menu.Menus;
import com.pvpcore.menu.Status;
import com.pvpcore.module.GhostModule;
import com.pvpcore.rules.Rule;
import com.pvpcore.util.Text;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public final class PvPCoreCommand implements TabExecutor {
   private static final List<String> SUBCOMMANDS = List.of("menu", "status", "reload", "toggle", "set", "preset", "rules", "rule", "fix", "help");
   private static final List<String> RULE_SETTINGS = List.of("damage", "max-damage", "cooldown", "disabled", "chunk-limit", "player-limit", "worlds", "remove");
   private static final String ACCENT = "<#7DD3FC>";
   private static final String SOFT = "<" + Text.SOFT + ">";
   private static final String MUTED = "<" + Text.MUTED + ">";
   private static final String GOOD = "<" + Text.GOOD + ">";
   private static final String BAD = "<" + Text.BAD + ">";
   private static final String WARN = "<" + Text.WARN + ">";
   private final PvPCore plugin;

   public PvPCoreCommand(PvPCore plugin) {
      this.plugin = plugin;
   }

   @Override
   public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
      if (!sender.hasPermission(Menus.PERMISSION)) {
         say(sender, BAD + "You don't have permission to use this.");
         return true;
      }

      if (args.length == 0) {
         if (sender instanceof Player player) {
            this.plugin.menus().openHub(player);
         } else {
            this.status(sender);
         }

         return true;
      }

      switch (args[0].toLowerCase(Locale.ROOT)) {
         case "menu", "gui" -> this.menu(sender, args);
         case "status" -> this.status(sender);
         case "reload" -> {
            boolean clean = this.plugin.reload();
            say(sender, clean
               ? GOOD + "Reloaded config.yml and rules.yml. " + SOFT + this.plugin.settings().enabledCount() + " of " + Feature.values().length + " switches on, " + this.plugin.rules().all().size() + " item rules."
               : WARN + "Reloaded, but a file has an error and was left as it was - see the console.");
         }
         case "toggle" -> this.toggle(sender, label, args);
         case "set" -> this.set(sender, label, args);
         case "preset" -> this.preset(sender, label, args);
         case "rules" -> {
            if (sender instanceof Player player) {
               this.plugin.menus().openRules(player);
            } else {
               this.listRules(sender);
            }
         }
         case "rule" -> this.rule(sender, label, args);
         case "fix" -> this.fix(sender, label, args);
         default -> this.usage(sender, label);
      }

      return true;
   }

   private void menu(CommandSender sender, String[] args) {
      if (!(sender instanceof Player player)) {
         say(sender, BAD + "The menu is for players. Try /pvpcore status.");
         return;
      }

      if (args.length < 2) {
         this.plugin.menus().openHub(player);
         return;
      }

      String page = args[1].toLowerCase(Locale.ROOT);
      Feature.Category category = Feature.Category.byId(page);
      if (category != null) {
         this.plugin.menus().openCategory(player, category);
      } else if (page.equals("rules")) {
         this.plugin.menus().openRules(player);
      } else if (page.equals("presets")) {
         this.plugin.menus().openPresets(player);
      } else {
         this.plugin.menus().openHub(player);
      }
   }

   private void toggle(CommandSender sender, String label, String[] args) {
      if (args.length < 2) {
         say(sender, SOFT + "Usage: /" + label + " toggle <setting> [on|off]");
         return;
      }

      Feature feature = Feature.byId(args[1]);
      if (feature == null) {
         this.unknown(sender, args[1]);
         return;
      }

      Settings settings = this.plugin.settings();
      boolean on;
      if (args.length >= 3) {
         Boolean parsed = parseBoolean(args[2]);
         if (parsed == null) {
            say(sender, BAD + "Say on or off, not '" + Text.escape(args[2]) + "'.");
            return;
         }

         on = parsed;
      } else {
         on = !settings.on(feature);
      }

      settings.set(feature, on);
      this.plugin.applyAll();
      this.plugin.getLogger().info(sender.getName() + " turned " + feature.title() + (on ? " on." : " off."));
      say(sender, SOFT + feature.title() + " is now " + (on ? GOOD + "on" : BAD + "off") + SOFT + "." + this.saveWarning());
   }

   private void set(CommandSender sender, String label, String[] args) {
      if (args.length < 3) {
         say(sender, SOFT + "Usage: /" + label + " set <setting> <number>");
         return;
      }

      Feature feature = Feature.byId(args[1]);
      if (feature == null) {
         this.unknown(sender, args[1]);
         return;
      }

      Feature.Value value = feature.value();
      if (value == null) {
         say(sender, BAD + feature.title() + " is just on or off - use toggle.");
         return;
      }

      double amount;
      try {
         amount = Double.parseDouble(args[2]);
      } catch (NumberFormatException e) {
         say(sender, BAD + "'" + Text.escape(args[2]) + "' is not a number.");
         return;
      }

      if (!Double.isFinite(amount)) {
         say(sender, BAD + "'" + Text.escape(args[2]) + "' is not a number.");
         return;
      }

      double stored = this.plugin.settings().setValue(feature, amount);
      this.plugin.applyAll();
      this.plugin.getLogger().info(sender.getName() + " set " + feature.title() + " to " + value.unit().format(stored) + ".");
      String clamped = amount >= value.min() && amount <= value.max() ? "" : WARN + " (allowed " + Text.trim(value.min()) + " to " + Text.trim(value.max()) + ")";
      say(sender, SOFT + feature.title() + " set to <white>" + Text.escape(value.unit().format(stored)) + SOFT + "." + clamped + this.saveWarning());
   }

   private void preset(CommandSender sender, String label, String[] args) {
      if (args.length < 2) {
         say(sender, SOFT + "Usage: /" + label + " preset <" + String.join("|", presetIds()) + ">");
         return;
      }

      Preset preset = Preset.byId(args[1]);
      if (preset == null) {
         say(sender, BAD + "No preset called '" + Text.escape(args[1]) + "'. " + SOFT + "Try: " + String.join(", ", presetIds()));
         return;
      }

      this.plugin.settings().apply(preset);
      this.plugin.applyAll();
      this.plugin.getLogger().info(sender.getName() + " applied the " + preset.label() + " preset.");
      say(sender, GOOD + preset.label() + " preset applied. " + SOFT + "Item rules were kept." + this.saveWarning());
   }

   /** Re-shows a player to everyone near them, and everyone near them to the player. */
   private void fix(CommandSender sender, String label, String[] args) {
      GhostModule ghosts = this.plugin.ghosts();
      Player target;
      if (args.length >= 2) {
         target = Bukkit.getPlayerExact(args[1]);
         if (target == null) {
            say(sender, BAD + "No player online called '" + Text.escape(args[1]) + "'.");
            return;
         }
      } else if (sender instanceof Player player) {
         target = player;
      } else {
         say(sender, SOFT + "Usage: /" + label + " fix <player>");
         return;
      }

      if (ghosts == null) {
         say(sender, BAD + "Ghost fixes are not running.");
         return;
      }

      int fixed = ghosts.fix(target, GhostModule.Reason.COMMAND);
      say(sender, SOFT + "Re-sent <white>" + Text.escape(target.getName()) + SOFT + " to and from <white>" + fixed + SOFT + (fixed == 1 ? " view." : " views."));
   }

   private String saveWarning() {
      return this.plugin.settings().writable() ? "" : WARN + " Not saved: config.yml has an error.";
   }

   // ------------------------------------------------------------------ rules

   private void rule(CommandSender sender, String label, String[] args) {
      if (args.length < 2) {
         say(sender, SOFT + "Usage: /" + label + " rule <item> [" + String.join("|", RULE_SETTINGS) + "] [value]");
         return;
      }

      Material material = Rule.parseMaterial(args[1]);
      if (material == null) {
         say(sender, BAD + "'" + Text.escape(args[1]) + "' is not an item or block.");
         return;
      }

      Rule rule = this.plugin.rules().get(material);
      if (args.length == 2) {
         this.showRule(sender, material, rule);
         return;
      }

      String setting = args[2].toLowerCase(Locale.ROOT);
      if (setting.equals("remove") || setting.equals("delete")) {
         if (this.plugin.rules().remove(material)) {
            this.plugin.getLogger().info(sender.getName() + " removed the item rule for " + Text.pretty(material) + ".");
            say(sender, SOFT + "Removed the rule for <white>" + Text.pretty(material) + SOFT + ".");
         } else {
            say(sender, SOFT + "There is no rule for " + Text.pretty(material) + ".");
         }

         this.plugin.menus().refreshAll();
         return;
      }

      if (args.length < 4) {
         say(sender, SOFT + "Usage: /" + label + " rule " + material.name().toLowerCase(Locale.ROOT) + " " + setting + " <value>");
         return;
      }

      Rule current = rule == null ? Rule.empty(material) : rule;
      String raw = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
      Rule updated;
      try {
         updated = switch (setting) {
            case "damage" -> current.withDamage(number(raw));
            case "max-damage", "maxdamage", "cap" -> current.withMaxDamage(number(raw));
            case "cooldown" -> current.withCooldown(number(raw));
            case "chunk-limit", "chunklimit" -> current.withChunkLimit((int)Math.round(number(raw)));
            case "player-limit", "playerlimit" -> current.withPlayerLimit((int)Math.round(number(raw)));
            case "disabled", "disable" -> {
               Boolean value = parseBoolean(raw);
               if (value == null) {
                  throw new IllegalArgumentException("Say true or false.");
               }

               yield current.withDisabled(value);
            }
            case "worlds", "world" -> current.withWorlds(raw.equalsIgnoreCase("all") || raw.equals("*") ? List.of() : worlds(raw));
            default -> throw new IllegalArgumentException("Unknown setting '" + setting + "'. Try: " + String.join(", ", RULE_SETTINGS));
         };
      } catch (IllegalArgumentException e) {
         say(sender, BAD + Text.escape(e.getMessage()));
         return;
      }

      if ((setting.startsWith("chunk") || setting.startsWith("player")) && !material.isBlock()) {
         say(sender, WARN + Text.pretty(material) + " is not a block, so block limits do nothing for it.");
      }

      this.plugin.rules().put(updated);
      this.plugin.menus().refreshAll();
      this.plugin.getLogger().info(sender.getName() + " set " + setting + " = " + raw + " for " + Text.pretty(material) + ".");
      this.showRule(sender, material, updated);
      if (!this.plugin.rules().writable()) {
         say(sender, WARN + "Not saved: rules.yml has an error.");
      }
   }

   private static double number(String raw) {
      try {
         double value = Double.parseDouble(raw.trim());
         if (!Double.isFinite(value) || value < 0.0) {
            throw new IllegalArgumentException("Use a number of 0 or more.");
         }

         return value;
      } catch (NumberFormatException e) {
         throw new IllegalArgumentException("'" + raw + "' is not a number.");
      }
   }

   private static List<String> worlds(String raw) {
      List<String> worlds = new ArrayList<>();
      for (String part : raw.split("[,\\s]+")) {
         if (!part.isBlank()) {
            worlds.add(part.trim());
         }
      }

      return worlds;
   }

   private void showRule(CommandSender sender, Material material, Rule rule) {
      if (rule == null) {
         say(sender, SOFT + "No rule for " + Text.pretty(material) + ". Add one with /pvpcore rule " + material.name().toLowerCase(Locale.ROOT) + " <setting> <value>.");
         return;
      }

      say(sender, ACCENT + Text.pretty(material));
      say(sender, SOFT + "  damage <white>" + Text.trim(rule.damage()) + "x" + SOFT + "  max-damage <white>" + (rule.maxDamage() > 0 ? Text.trim(rule.maxDamage()) + " hearts" : "off")
         + SOFT + "  cooldown <white>" + (rule.cooldown() > 0 ? Text.trim(rule.cooldown()) + "s" : "off"));
      say(sender, SOFT + "  disabled <white>" + rule.disabled() + SOFT + "  chunk-limit <white>" + (rule.chunkLimit() > 0 ? rule.chunkLimit() : "off")
         + SOFT + "  player-limit <white>" + (rule.playerLimit() > 0 ? rule.playerLimit() : "off")
         + SOFT + "  worlds <white>" + (rule.worlds().isEmpty() ? "all" : Text.escape(String.join(", ", rule.worlds()))));
   }

   private void listRules(CommandSender sender) {
      List<Rule> rules = this.plugin.rules().sorted();
      say(sender, ACCENT + "Item rules " + SOFT + "(" + rules.size() + ")" + (this.plugin.settings().on(Feature.ITEM_RULES) ? "" : WARN + " - switched off"));
      for (Rule rule : rules) {
         this.showRule(sender, rule.material(), rule);
      }
   }

   // ------------------------------------------------------------------ status and help

   private void status(CommandSender sender) {
      Settings settings = this.plugin.settings();
      say(sender, ACCENT + "PvP settings " + SOFT + "- " + settings.enabledCount() + " of " + Feature.values().length + " switches on, "
         + this.plugin.rules().all().size() + " item rules");
      Feature.Section section = null;
      for (Feature feature : Feature.values()) {
         if (feature.section() != section) {
            section = feature.section();
            say(sender, MUTED + section.category().label() + " / " + section.label());
         }

         Status.State state = Status.of(this.plugin, feature);
         String onOff = !state.available() ? WARN + "unavailable" : state.on() ? GOOD + "on" : BAD + "off";
         String number = feature.value() != null ? " <white>" + Text.escape(feature.value().unit().format(settings.value(feature))) : "";
         say(sender, SOFT + "  " + feature.id() + " " + onOff + number);
      }

      say(sender, MUTED + "Server <white>" + Text.escape(Bukkit.getName() + " " + Compat.serverVersion()) + SOFT + " - shields "
         + (this.plugin.modernShields() ? "data-driven" : "in compatibility mode") + ", particles " + (this.plugin.particlesAvailable() ? "filterable" : "not filterable"));
      GhostModule ghosts = this.plugin.ghosts();
      if (ghosts != null) {
         StringBuilder fixes = new StringBuilder();
         for (GhostModule.Reason reason : GhostModule.Reason.values()) {
            long count = ghosts.count(reason);
            if (count > 0) {
               fixes.append(fixes.isEmpty() ? "" : ", ").append(reason.name().toLowerCase(Locale.ROOT)).append(' ').append(count);
            }
         }

         say(sender, MUTED + "Ghost fixes since start <white>" + (fixes.isEmpty() ? "none" : fixes) + SOFT + " - re-send "
            + (ghosts.lightResync() ? "light" : "by re-showing") + ", scanner " + (ghosts.scannerAvailable() ? "supported" : "not supported"));
      }
      if (!settings.writable()) {
         say(sender, WARN + "config.yml has an error and is not being saved: " + Text.escape(String.valueOf(settings.loadError())));
      }

      if (!this.plugin.rules().writable()) {
         say(sender, WARN + "rules.yml has an error and is not being saved: " + Text.escape(String.valueOf(this.plugin.rules().loadError())));
      }
   }

   private void usage(CommandSender sender, String label) {
      say(sender, ACCENT + "PvP settings");
      say(sender, SOFT + "/" + label + " <white>- open the menu");
      say(sender, SOFT + "/" + label + " status <white>- every switch and what this server supports");
      say(sender, SOFT + "/" + label + " reload <white>- re-read config.yml and rules.yml");
      say(sender, SOFT + "/" + label + " toggle <setting> [on|off]");
      say(sender, SOFT + "/" + label + " set <setting> <number>");
      say(sender, SOFT + "/" + label + " preset <" + String.join("|", presetIds()) + ">");
      say(sender, SOFT + "/" + label + " rules <white>- item nerfs, cooldowns and limits");
      say(sender, SOFT + "/" + label + " rule <item> <setting> <value> <white>- e.g. rule ender_pearl cooldown 15");
      say(sender, SOFT + "/" + label + " fix [player] <white>- re-show a player who went invisible to others");
   }

   private void unknown(CommandSender sender, String id) {
      say(sender, BAD + "No setting called '" + Text.escape(id) + "'. " + SOFT + "Tab-complete to see them all.");
   }

   private static Boolean parseBoolean(String text) {
      return switch (text.trim().toLowerCase(Locale.ROOT)) {
         case "on", "true", "yes", "enable", "enabled" -> Boolean.TRUE;
         case "off", "false", "no", "disable", "disabled" -> Boolean.FALSE;
         default -> null;
      };
   }

   private static void say(CommandSender sender, String miniMessage) {
      sender.sendMessage(Text.mm(miniMessage));
   }

   private static List<String> ids(boolean numbersOnly) {
      List<String> ids = new ArrayList<>();
      for (Feature feature : Feature.values()) {
         if (!numbersOnly || feature.value() != null) {
            ids.add(feature.id());
         }
      }

      return ids;
   }

   private static List<String> presetIds() {
      List<String> ids = new ArrayList<>();
      for (Preset preset : Preset.values()) {
         ids.add(preset.id());
      }

      return ids;
   }

   @Override
   public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
      if (!sender.hasPermission(Menus.PERMISSION)) {
         return List.of();
      }

      if (args.length == 1) {
         return matching(SUBCOMMANDS, args[0]);
      }

      String sub = args[0].toLowerCase(Locale.ROOT);
      if (args.length == 2) {
         return switch (sub) {
            case "toggle" -> matching(ids(false), args[1]);
            case "set" -> matching(ids(true), args[1]);
            case "preset" -> matching(presetIds(), args[1]);
            case "menu", "gui" -> {
               List<String> pages = new ArrayList<>();
               for (Feature.Category category : Feature.Category.values()) {
                  pages.add(category.id());
               }

               pages.add("rules");
               pages.add("presets");
               yield matching(pages, args[1]);
            }
            case "rule" -> materials(args[1]);
            case "fix" -> {
               List<String> names = new ArrayList<>();
               for (Player player : Bukkit.getOnlinePlayers()) {
                  names.add(player.getName());
               }

               yield matchingIgnoringCase(names, args[1]);
            }
            default -> List.of();
         };
      }

      if (args.length == 3) {
         return switch (sub) {
            case "toggle" -> matching(List.of("on", "off"), args[2]);
            case "rule" -> matching(RULE_SETTINGS, args[2]);
            default -> List.of();
         };
      }

      if (args.length == 4 && sub.equals("rule")) {
         String setting = args[2].toLowerCase(Locale.ROOT);
         if (setting.startsWith("disable")) {
            return matching(List.of("true", "false"), args[3]);
         }

         if (setting.startsWith("world")) {
            List<String> worlds = new ArrayList<>();
            worlds.add("all");
            Bukkit.getWorlds().forEach(world -> worlds.add(world.getName()));
            return matching(worlds, args[3]);
         }
      }

      return List.of();
   }

   private List<String> materials(String typed) {
      String prefix = typed.toLowerCase(Locale.ROOT);
      List<String> result = new ArrayList<>();
      for (Rule rule : this.plugin.rules().sorted()) {
         String name = rule.material().name().toLowerCase(Locale.ROOT);
         if (name.startsWith(prefix)) {
            result.add(name);
         }
      }

      if (prefix.length() >= 2) {
         for (Material material : Material.values()) {
            if (result.size() >= 60) {
               break;
            }

            if (!material.isLegacy() && material.isItem() && !material.isAir()) {
               String name = material.name().toLowerCase(Locale.ROOT);
               if (name.startsWith(prefix) && !result.contains(name)) {
                  result.add(name);
               }
            }
         }
      }

      return result;
   }

   private static List<String> matchingIgnoringCase(List<String> options, String typed) {
      String prefix = typed.toLowerCase(Locale.ROOT);
      List<String> result = new ArrayList<>();
      for (String option : options) {
         if (option.toLowerCase(Locale.ROOT).startsWith(prefix)) {
            result.add(option);
         }
      }

      return result;
   }

   private static List<String> matching(List<String> options, String typed) {
      String prefix = typed.toLowerCase(Locale.ROOT);
      List<String> result = new ArrayList<>();
      for (String option : options) {
         if (option.startsWith(prefix)) {
            result.add(option);
         }
      }

      return result;
   }
}
