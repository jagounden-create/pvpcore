package com.pvpcore.module;

import com.pvpcore.Feature;
import com.pvpcore.Module;
import com.pvpcore.PvPCore;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Trident;
import org.bukkit.inventory.ItemStack;
import org.bukkit.entity.minecart.ExplosiveMinecart;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.vehicle.VehicleCreateEvent;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

/**
 * TNT minecart PvP.
 * <p>
 * Cart hit reg: the usual play is shoot a flame arrow, then place a rail and a cart in its path. With ping, the cart
 * reaches the server after the arrow has already flown through (or landed in) the spot where the player saw it hit.
 * The paths of burning arrows are remembered for a few ticks, and a freshly placed cart that one of them crossed within
 * the placer's ping goes off, with the arrow's speed as its power, exactly as the hit would have.
 */
public final class CartModule extends Module {
   /** How many ticks of arrow path are remembered. 12 ticks = 600ms, above the largest window the menu allows. */
   static final int HISTORY_TICKS = 12;
   static final int MAX_TRACKED_ARROWS = 1024;
   /** Arrows that have flown this long without landing are no longer interesting. */
   static final int MAX_FLIGHT_TICKS = 20 * 20;
   static final double BOX_SLACK = 0.25;
   /** Carts going off within this distance of another blast in the same tick are merged into it. */
   static final double MERGE_DISTANCE = 1.5;
   /** A cart set off by another cart's blast in the same tick, this close, counts as lit by the same weapon. */
   static final double CHAIN_DISTANCE = 8.0;
   private final Map<UUID, Trail> arrows = new HashMap<>();
   /** What lit each cart: a bow or a crossbow arrow. Kept for the tick of the blast. */
   private final Map<UUID, Ignition> ignitions = new HashMap<>();
   /** Blasts that happened this tick, for merging and chain reactions. */
   private final List<Blast> blasts = new ArrayList<>();
   private int blastTick = -1;
   private final Map<UUID, Cart> carts = new HashMap<>();
   private final List<Pending> pending = new ArrayList<>();
   private BukkitTask ticker;

   public CartModule(PvPCore plugin) {
      super(plugin);
   }

   @Override
   public void start() {
      this.ticker = Bukkit.getScheduler().runTaskTimer(this.plugin, this::tick, 1L, 1L);
   }

   @Override
   public void apply() {
      if (!this.on(Feature.CART_HIT_REG)) {
         this.arrows.clear();
         this.pending.clear();
      }
   }

   @Override
   public void stop() {
      if (this.ticker != null) {
         this.ticker.cancel();
      }

      this.arrows.clear();
      this.carts.clear();
      this.pending.clear();
      this.ignitions.clear();
      this.blasts.clear();
   }

   /** The player who set this cart off or placed it, for kill credit and health bars. */
   public Player ownerOf(Entity entity) {
      if (entity instanceof ExplosiveMinecart) {
         Cart cart = this.carts.get(entity.getUniqueId());
         if (cart != null) {
            UUID owner = cart.igniter != null ? cart.igniter : cart.placer;
            return owner == null ? null : Bukkit.getPlayer(owner);
         }
      }

      return null;
   }

   // ------------------------------------------------------------------ ticking

   private void tick() {
      int now = Bukkit.getCurrentTick();
      if (!this.ignitions.isEmpty()) {
         this.ignitions.values().removeIf(ignition -> now - ignition.tick() > 2);
      }

      if (this.arrows.isEmpty() && this.carts.isEmpty() && this.pending.isEmpty()) {
         return;
      }

      this.trackArrows(now);
      if (!this.pending.isEmpty()) {
         List<Pending> due = new ArrayList<>(this.pending);
         this.pending.clear();
         for (Pending check : due) {
            this.compensate(check);
         }
      }

      this.tickCarts(now);
   }

   private void trackArrows(int now) {
      Iterator<Trail> iterator = this.arrows.values().iterator();

      while (iterator.hasNext()) {
         Trail trail = iterator.next();
         AbstractArrow arrow = trail.arrow;
         if (!arrow.isValid()) {
            iterator.remove();
            continue;
         }

         Location at = arrow.getLocation();
         boolean inBlock = arrow.isInBlock();
         if (!inBlock) {
            trail.flightVelocity = arrow.getVelocity();
         }

         UUID world = at.getWorld().getUID();
         if (trail.world != null && trail.world.equals(world) && arrow.getFireTicks() > 0) {
            trail.segments.addLast(new Segment(now, trail.x, trail.y, trail.z, at.getX(), at.getY(), at.getZ()));
         }

         trail.world = world;
         trail.x = at.getX();
         trail.y = at.getY();
         trail.z = at.getZ();

         while (!trail.segments.isEmpty() && now - trail.segments.peekFirst().tick() > HISTORY_TICKS) {
            trail.segments.pollFirst();
         }

         if (inBlock) {
            if (trail.landed < 0) {
               trail.landed = now;
            } else if (now - trail.landed > HISTORY_TICKS) {
               iterator.remove();
            }
         } else if (now - trail.launched > MAX_FLIGHT_TICKS) {
            iterator.remove();
         }
      }
   }

   private void compensate(Pending check) {
      ExplosiveMinecart cart = check.cart;
      if (!cart.isValid() || !this.on(Feature.CART_HIT_REG)) {
         return;
      }

      int window = window(check.ping, this.settings().value(Feature.CART_HIT_REG));
      BoundingBox box = cart.getBoundingBox().expand(BOX_SLACK);
      UUID world = cart.getWorld().getUID();

      for (Trail trail : this.arrows.values()) {
         if (!trail.arrow.isValid() || !world.equals(trail.world)) {
            continue;
         }

         for (Segment segment : trail.segments) {
            if (check.tick - segment.tick() <= window && crosses(box, segment)) {
               this.detonate(cart, trail);
               return;
            }
         }
      }
   }

   /** Ticks of arrow path a cart placed with this ping may still be hit by. */
   static int window(int pingMs, double maxMs) {
      int cap = Math.max(1, (int)Math.ceil(maxMs / 50.0));
      int wanted = (int)Math.ceil(Math.max(0, pingMs) / 50.0) + 1;
      return Math.max(1, Math.min(cap, wanted));
   }

   static boolean crosses(BoundingBox box, Segment segment) {
      if (box.contains(segment.x1(), segment.y1(), segment.z1()) || box.contains(segment.x2(), segment.y2(), segment.z2())) {
         return true;
      }

      Vector start = new Vector(segment.x1(), segment.y1(), segment.z1());
      Vector path = new Vector(segment.x2() - segment.x1(), segment.y2() - segment.y1(), segment.z2() - segment.z1());
      double length = path.length();
      return length > 1.0E-6 && box.rayTrace(start, path.multiply(1.0 / length), length) != null;
   }

   private void detonate(ExplosiveMinecart cart, Trail trail) {
      Cart tracked = this.carts.get(cart.getUniqueId());
      ProjectileSource shooter = trail.arrow.getShooter();
      if (tracked != null && shooter instanceof Player player) {
         tracked.igniter = player.getUniqueId();
      }

      Vector velocity = trail.flightVelocity != null ? trail.flightVelocity : trail.arrow.getVelocity();
      Launcher launcher = launcher(trail.arrow);
      if (launcher != null) {
         this.ignitions.put(cart.getUniqueId(), new Ignition(launcher, Bukkit.getCurrentTick()));
      }

      trail.arrow.remove();
      this.arrows.remove(trail.arrow.getUniqueId());
      // Vanilla uses the burning arrow's squared speed as the blast's speed factor; the API caps it at 25 (speed 5).
      cart.explode(Math.max(0.0, Math.min(25.0, velocity.lengthSquared())));
   }

   private void tickCarts(int now) {
      boolean fuse = this.on(Feature.CART_FUSE);
      boolean cleanup = this.on(Feature.CART_CLEANUP);
      int fuseTicks = this.settings().ticks(Feature.CART_FUSE);
      int maxAge = (int)Math.round(this.settings().value(Feature.CART_CLEANUP) * 20.0);
      Iterator<Cart> iterator = this.carts.values().iterator();

      while (iterator.hasNext()) {
         Cart tracked = iterator.next();
         ExplosiveMinecart cart = tracked.cart;
         if (!cart.isValid()) {
            // Not in the world yet only for the tick it was placed in.
            if (now - tracked.placed > 1) {
               iterator.remove();
            }
            continue;
         }

         if (cart.isIgnited()) {
            if (fuse && !tracked.fuseSet) {
               tracked.fuseSet = true;
               if (cart.getFuseTicks() > fuseTicks) {
                  cart.setFuseTicks(fuseTicks);
               }
            }
         } else if (cleanup && tracked.placer != null && now - tracked.placed > maxAge) {
            cart.remove();
            iterator.remove();
         }
      }
   }

   // ------------------------------------------------------------------ events

   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onLaunch(ProjectileLaunchEvent event) {
      if (event.getEntity() instanceof AbstractArrow arrow && !(arrow instanceof Trident) && this.on(Feature.CART_HIT_REG) && this.arrows.size() < MAX_TRACKED_ARROWS) {
         this.arrows.put(arrow.getUniqueId(), new Trail(arrow, Bukkit.getCurrentTick()));
      }
   }

   @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
   public void onPlace(EntityPlaceEvent event) {
      if (event.getEntity() instanceof ExplosiveMinecart && this.on(Feature.CART_LIMIT) && this.full(event.getEntity().getLocation().getChunk())) {
         event.setCancelled(true);
         if (event.getPlayer() != null) {
            this.plugin.actionBar(event.getPlayer(), "cart-limit");
         }
      }
   }

   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onPlaced(EntityPlaceEvent event) {
      if (event.getEntity() instanceof ExplosiveMinecart cart) {
         Player player = event.getPlayer();
         int now = Bukkit.getCurrentTick();
         this.carts.put(cart.getUniqueId(), new Cart(cart, player == null ? null : player.getUniqueId(), now));
         if (player != null && this.on(Feature.CART_HIT_REG) && !this.arrows.isEmpty()) {
            this.pending.add(new Pending(cart, Math.max(0, player.getPing()), now));
         }
      }
   }

   /** Dispensers and other plugins: the limit still applies, silently. */
   @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
   public void onCreate(VehicleCreateEvent event) {
      if (event.getVehicle() instanceof ExplosiveMinecart cart && !this.carts.containsKey(cart.getUniqueId())) {
         if (this.on(Feature.CART_LIMIT) && this.full(cart.getLocation().getChunk())) {
            event.setCancelled(true);
         }
      }
   }

   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onCreated(VehicleCreateEvent event) {
      if (event.getVehicle() instanceof ExplosiveMinecart cart && !this.carts.containsKey(cart.getUniqueId())) {
         this.carts.put(cart.getUniqueId(), new Cart(cart, null, Bukkit.getCurrentTick()));
      }
   }

   private boolean full(Chunk chunk) {
      int max = this.settings().ticks(Feature.CART_LIMIT);
      int count = 0;
      for (Entity entity : chunk.getEntities()) {
         if (entity instanceof ExplosiveMinecart && entity.isValid() && ++count >= max) {
            return true;
         }
      }

      return false;
   }

   /** A burning arrow hitting a cart sets it off right after this event; remember which weapon fired it. */
   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onArrowHitCart(ProjectileHitEvent event) {
      if (event.getHitEntity() instanceof ExplosiveMinecart cart && event.getEntity() instanceof AbstractArrow arrow && !(arrow instanceof Trident) && arrow.getFireTicks() > 0) {
         Launcher launcher = launcher(arrow);
         if (launcher != null) {
            this.ignitions.put(cart.getUniqueId(), new Ignition(launcher, Bukkit.getCurrentTick()));
         }
      }
   }

   static Launcher launcher(AbstractArrow arrow) {
      try {
         ItemStack weapon = arrow.getWeapon();
         if (weapon != null) {
            if (weapon.getType() == Material.CROSSBOW) {
               return Launcher.CROSSBOW;
            }

            if (weapon.getType() == Material.BOW) {
               return Launcher.BOW;
            }
         }
      } catch (LinkageError | RuntimeException ignored) {
         // older API: fall back below
      }

      try {
         if (!(arrow.getShooter() instanceof org.bukkit.entity.LivingEntity)) {
            return null;
         }

         return arrow.isShotFromCrossbow() ? Launcher.CROSSBOW : Launcher.BOW;
      } catch (LinkageError | RuntimeException e) {
         return null;
      }
   }

   private List<Blast> blastsThisTick() {
      int now = Bukkit.getCurrentTick();
      if (this.blastTick != now) {
         this.blastTick = now;
         this.blasts.clear();
      }

      return this.blasts;
   }

   /** Stacked carts: the first blast in a spot goes off, the rest in the same tick just disappear. */
   @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
   public void onMerge(ExplosionPrimeEvent event) {
      if (event.getEntity() instanceof ExplosiveMinecart cart && this.on(Feature.CART_MERGE)) {
         Location at = cart.getLocation();
         for (Blast blast : this.blastsThisTick()) {
            if (blast.near(at, MERGE_DISTANCE)) {
               event.setCancelled(true);
               cart.remove();
               return;
            }
         }
      }
   }

   @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
   public void onPrime(ExplosionPrimeEvent event) {
      if (event.getEntity() instanceof ExplosiveMinecart && this.on(Feature.CART_POWER)) {
         event.setRadius((float)this.settings().value(Feature.CART_POWER));
      }
   }

   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onPrimed(ExplosionPrimeEvent event) {
      if (event.getEntity() instanceof ExplosiveMinecart cart) {
         Location at = cart.getLocation();
         Ignition ignition = this.ignitions.get(cart.getUniqueId());
         List<Blast> blasts = this.blastsThisTick();
         if (ignition == null) {
            // Set off by another cart's blast this tick: it inherits what lit that one.
            for (Blast blast : blasts) {
               if (blast.launcher() != null && blast.near(at, CHAIN_DISTANCE)) {
                  ignition = new Ignition(blast.launcher(), Bukkit.getCurrentTick());
                  this.ignitions.put(cart.getUniqueId(), ignition);
                  break;
               }
            }
         }

         blasts.add(new Blast(at.getWorld().getUID(), at.getX(), at.getY(), at.getZ(), ignition == null ? null : ignition.launcher()));
      }
   }

   @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
   public void onExplode(EntityExplodeEvent event) {
      if (event.getEntity() instanceof ExplosiveMinecart) {
         // The cart's entry stays until the next tick, so this blast's damage events can still find its owner.
         if (this.on(Feature.CART_TERRAIN)) {
            event.blockList().clear();
         } else if (this.on(Feature.CART_NO_DROPS)) {
            event.setYield(0.0F);
         }
      }
   }

   /** Bow and crossbow carts: crossbow arrows fly faster, so their carts hit far harder and are nerfed more. */
   @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
   public void onCartNerf(EntityDamageByEntityEvent event) {
      if (event.getDamager() instanceof ExplosiveMinecart cart && event.getEntity() instanceof Player) {
         Ignition ignition = this.ignitions.get(cart.getUniqueId());
         if (ignition == null) {
            return;
         }

         Feature feature = ignition.launcher() == Launcher.CROSSBOW ? Feature.CART_CROSSBOW_DAMAGE : Feature.CART_BOW_DAMAGE;
         if (this.on(feature)) {
            event.setDamage(event.getDamage() * this.settings().value(feature));
         }
      }
   }

   @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
   public void onBlastDamage(EntityDamageByEntityEvent event) {
      if (event.getDamager() instanceof ExplosiveMinecart cart && event.getEntity() instanceof Player victim && this.on(Feature.CART_SELF_DAMAGE)) {
         Player owner = causingPlayer(event);
         if (owner == null) {
            owner = this.ownerOf(cart);
         }

         if (victim.equals(owner)) {
            double percent = this.settings().value(Feature.CART_SELF_DAMAGE);
            if (percent <= 0.0) {
               event.setCancelled(true);
            } else {
               event.setDamage(event.getDamage() * percent / 100.0);
            }
         }
      }
   }

   /** A cart set off by hit reg has no player in its damage source; give the kill to the arrow's shooter. */
   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onBlastCredit(EntityDamageByEntityEvent event) {
      if (event.getDamager() instanceof ExplosiveMinecart cart && event.getEntity() instanceof Player victim) {
         Cart tracked = this.carts.get(cart.getUniqueId());
         if (tracked != null && tracked.igniter != null && !tracked.igniter.equals(victim.getUniqueId())) {
            Player igniter = Bukkit.getPlayer(tracked.igniter);
            if (igniter != null) {
               try {
                  victim.setKiller(igniter);
               } catch (LinkageError | RuntimeException ignored) {
                  // no kill credit API on this server
               }
            }
         }
      }
   }

   public static Player causingPlayer(EntityDamageByEntityEvent event) {
      try {
         return event.getDamageSource().getCausingEntity() instanceof Player player ? player : null;
      } catch (LinkageError | RuntimeException e) {
         return null;
      }
   }

   // ------------------------------------------------------------------ state

   static final class Trail {
      final AbstractArrow arrow;
      final int launched;
      final ArrayDeque<Segment> segments = new ArrayDeque<>();
      UUID world;
      double x;
      double y;
      double z;
      int landed = -1;
      Vector flightVelocity;

      Trail(AbstractArrow arrow, int launched) {
         this.arrow = arrow;
         this.launched = launched;
         Location at = arrow.getLocation();
         this.world = at.getWorld().getUID();
         this.x = at.getX();
         this.y = at.getY();
         this.z = at.getZ();
         this.flightVelocity = arrow.getVelocity();
      }
   }

   record Segment(int tick, double x1, double y1, double z1, double x2, double y2, double z2) {
   }

   static final class Cart {
      final ExplosiveMinecart cart;
      final UUID placer;
      final int placed;
      UUID igniter;
      boolean fuseSet;

      Cart(ExplosiveMinecart cart, UUID placer, int placed) {
         this.cart = cart;
         this.placer = placer;
         this.placed = placed;
      }
   }

   record Pending(ExplosiveMinecart cart, int ping, int tick) {
   }

   enum Launcher {
      BOW,
      CROSSBOW;
   }

   record Ignition(Launcher launcher, int tick) {
   }

   record Blast(UUID world, double x, double y, double z, Launcher launcher) {
      boolean near(Location at, double distance) {
         if (!this.world.equals(at.getWorld().getUID())) {
            return false;
         }

         double dx = this.x - at.getX();
         double dy = this.y - at.getY();
         double dz = this.z - at.getZ();
         return dx * dx + dy * dy + dz * dz <= distance * distance;
      }
   }
}
