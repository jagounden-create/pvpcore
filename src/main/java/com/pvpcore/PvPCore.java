package com.pvpcore;

import com.pvpcore.command.PvPCoreCommand;
import com.pvpcore.menu.MenuListener;
import com.pvpcore.menu.Menus;
import com.pvpcore.module.CartModule;
import com.pvpcore.module.ClumpsModule;
import com.pvpcore.module.CombatModule;
import com.pvpcore.module.CombatTagModule;
import com.pvpcore.module.CrystalModule;
import com.pvpcore.module.GeneralModule;
import com.pvpcore.module.GhostModule;
import com.pvpcore.module.HealthModule;
import com.pvpcore.module.KnockbackModule;
import com.pvpcore.module.LegacyShieldModule;
import com.pvpcore.module.MaceModule;
import com.pvpcore.module.ParticleModule;
import com.pvpcore.module.PearlModule;
import com.pvpcore.module.PotModule;
import com.pvpcore.module.ShieldModule;
import com.pvpcore.module.WeaponsModule;
import com.pvpcore.rules.Rules;
import com.pvpcore.rules.RulesModule;
import com.pvpcore.util.Text;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public class PvPCore extends JavaPlugin {
   private Settings settings;
   private Rules rules;
   private ServerTweaks tweaks;
   private Menus menus;
   private final List<Module> modules = new ArrayList<>();
   private CartModule carts;
   private ParticleModule particles;
   private CombatModule combat;
   private GhostModule ghosts;
   private RulesModule itemRules;
   private boolean modernShields;

   @Override
   public void onEnable() {
      this.settings = new Settings(this);
      this.settings.load();
      this.rules = new Rules(this);
      this.rules.load();
      this.tweaks = new ServerTweaks(this.getLogger());
      this.menus = new Menus(this);
      PluginManager plugins = this.getServer().getPluginManager();

      this.add(new PearlModule(this));
      this.modernShields = Compat.DATA_DRIVEN_SHIELDS;
      if (this.modernShields) {
         this.add(new ShieldModule(this));
      } else {
         LegacyShieldModule legacy = new LegacyShieldModule(this);
         this.add(legacy);
         if (Compat.SHIELD_DISABLE_EVENT) {
            plugins.registerEvents(legacy.disableListener(), this);
         }
      }

      this.add(new PotModule(this));
      this.combat = new CombatModule(this);
      this.add(this.combat);
      if (Compat.PRE_ATTACK_EVENT) {
         plugins.registerEvents(this.combat.swapListener(), this);
      }

      if (Compat.KNOCKBACK_EVENT) {
         this.add(new KnockbackModule(this));
      }

      this.add(new WeaponsModule(this));
      this.add(new HealthModule(this));
      this.add(new ClumpsModule(this));
      this.add(new CrystalModule(this));
      MaceModule mace = new MaceModule(this);
      this.add(mace);
      if (Compat.PRE_ATTACK_EVENT) {
         plugins.registerEvents(mace.preAttackListener(), this);
      }

      this.carts = new CartModule(this);
      this.add(this.carts);
      this.add(new GeneralModule(this));
      this.add(new CombatTagModule(this));
      this.itemRules = new RulesModule(this);
      this.add(this.itemRules);
      this.ghosts = new GhostModule(this);
      this.add(this.ghosts);
      this.particles = new ParticleModule(this);
      this.add(this.particles);

      for (Module module : this.modules) {
         plugins.registerEvents(module, this);
      }

      plugins.registerEvents(new MenuListener(this), this);
      PluginCommand command = this.getCommand("pvpcore");
      if (command != null) {
         PvPCoreCommand executor = new PvPCoreCommand(this);
         command.setExecutor(executor);
         command.setTabCompleter(executor);
      }

      for (Module module : this.modules) {
         try {
            module.start();
         } catch (RuntimeException | LinkageError e) {
            this.getLogger().log(Level.WARNING, "Could not start " + module.getClass().getSimpleName(), e);
         }
      }

      this.applyAll();
      this.logSummary();
   }

   private void add(Module module) {
      this.modules.add(module);
   }

   @Override
   public void onDisable() {
      if (this.menus != null) {
         this.menus.closeAll();
      }

      for (Module module : this.modules) {
         try {
            module.stop();
         } catch (RuntimeException | LinkageError e) {
            this.getLogger().log(Level.WARNING, "Could not shut down " + module.getClass().getSimpleName(), e);
         }
      }

      this.modules.clear();
      if (this.tweaks != null) {
         this.tweaks.restore();
      }

      if (this.settings != null) {
         this.settings.flush();
      }

      if (this.rules != null) {
         this.rules.flush();
      }
   }

   /** Re-reads config.yml and rules.yml. @return false if either had an error (and was left untouched). */
   public boolean reload() {
      boolean config = this.settings.load();
      boolean rules = this.rules.load();
      this.applyAll();
      return config && rules;
   }

   public void applyAll() {
      for (Module module : this.modules) {
         try {
            module.apply();
         } catch (RuntimeException | LinkageError e) {
            this.getLogger().log(Level.WARNING, "Could not apply " + module.getClass().getSimpleName(), e);
         }
      }

      if (this.menus != null) {
         this.menus.refreshAll();
      }
   }

   public Settings settings() {
      return this.settings;
   }

   public Rules rules() {
      return this.rules;
   }

   public ServerTweaks tweaks() {
      return this.tweaks;
   }

   public Menus menus() {
      return this.menus;
   }

   public boolean modernShields() {
      return this.modernShields;
   }

   /** Tells the item rules that {@code player} used {@code material}, so its cooldown starts. */
   public void itemUsed(Player player, Material material) {
      if (this.itemRules != null) {
         this.itemRules.itemUsed(player, material);
      }
   }

   public GhostModule ghosts() {
      return this.ghosts;
   }

   public CombatModule combat() {
      return this.combat;
   }

   public boolean particlesAvailable() {
      return this.particles != null && this.particles.available();
   }

   /** The player responsible for a hit: the attacker, a projectile's shooter, or whoever set off a blast. */
   public Player attacker(EntityDamageByEntityEvent event) {
      Entity damager = event.getDamager();
      if (damager instanceof Player player) {
         return player;
      }

      if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
         return player;
      }

      Player causing = CartModule.causingPlayer(event);
      if (causing != null) {
         return causing;
      }

      if (damager instanceof TNTPrimed tnt && tnt.getSource() instanceof Player player) {
         return player;
      }

      return this.carts == null ? null : this.carts.ownerOf(damager);
   }

   public void actionBar(Player player, String key) {
      this.actionBar(player, key, Map.of());
   }

   public void actionBar(Player player, String key, Map<String, ?> placeholders) {
      String text = this.settings.message(key);
      if (!text.isEmpty()) {
         player.sendActionBar(Text.mm(text, placeholders));
      }
   }

   public void send(Player player, String key, Map<String, ?> placeholders) {
      String text = this.settings.message(key);
      if (!text.isEmpty()) {
         player.sendMessage(Text.mm(text, placeholders));
      }
   }

   public String version() {
      return this.getDescription().getVersion();
   }

   private void logSummary() {
      this.getLogger().info("Enabled on " + Bukkit.getName() + " " + Compat.serverVersion() + " - "
         + this.settings.enabledCount() + " of " + Feature.values().length + " switches on, " + this.rules.all().size() + " item rules. Menu: /pvpcore");
      if (!this.modernShields) {
         this.getLogger().info("Shields run in compatibility mode for this Minecraft version (per-player shield delay, vanilla axe disables).");
      }

      if (!Compat.KNOCKBACK_EVENT) {
         this.getLogger().warning("This server has no Paper knockback event, so Knockback Distance and Vertical Knockback are unavailable.");
      }

      if (Integrations.knockbackSync()) {
         this.getLogger().info("KnockbackSync found - Vertical Knockback stands down for it.");
      }

      if (Integrations.attributeSwapPlugin()) {
         this.getLogger().info("PaperAttributeSwapFix found - it owns attribute swapping.");
      }

      if (!this.particlesAvailable()) {
         this.getLogger().info("Particle filtering is unavailable on this server; the particle switches do nothing here.");
      }
   }
}
