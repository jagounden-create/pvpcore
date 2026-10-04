package com.pvpcore.module;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.block.BlockFace;

public final class PearlLanding {
   static final double GAP = 0.01;
   static final double FLOOR_LIFT = 0.001;

   private PearlLanding() {
   }

   public static double[] find(double x, double y, double z, PearlLanding.Face face, double width, double height, PearlLanding.Collision world) {
      if (Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z) && width > 0.0 && height > 0.0 && !(width > 4.0) && !(height > 8.0)) {
         double half = width / 2.0;
         double feetX = x;
         double feetY = y;
         double feetZ = z;
         switch (face) {
            case UP:
               feetY = y + 0.001;
               break;
            case DOWN:
               feetY = y - height - 0.01;
               break;
            case NORTH:
            case SOUTH:
            case EAST:
            case WEST:
               feetX = x + face.nx * (half + 0.01);
               feetZ = z + face.nz * (half + 0.01);
         }

         for (double[] step : steps(face, half + 0.01)) {
            double cx = feetX + step[0];
            double cy = feetY + step[1];
            double cz = feetZ + step[2];
            if (!world.collides(cx - half, cy, cz - half, cx + half, cy + height, cz + half)) {
               return new double[]{cx, cy, cz};
            }
         }

         return null;
      } else {
         return null;
      }
   }

   static List<double[]> steps(PearlLanding.Face face, double nudge) {
      List<double[]> steps = new ArrayList<>();
      steps.add(new double[]{0.0, 0.0, 0.0});
      if (face.side()) {
         boolean alongX = face.nz != 0;
         vertical(steps, -0.25, 0.25, -0.5, 0.5);
         sideways(steps, nudge, alongX, !alongX);
      } else if (face == PearlLanding.Face.DOWN) {
         vertical(steps, -0.25, -0.5);
         sideways(steps, nudge, true, true);
      } else {
         sideways(steps, nudge, true, true);
      }

      return steps;
   }

   private static void vertical(List<double[]> steps, double... amounts) {
      for (double amount : amounts) {
         steps.add(new double[]{0.0, amount, 0.0});
      }
   }

   private static void sideways(List<double[]> steps, double nudge, boolean x, boolean z) {
      if (x) {
         steps.add(new double[]{nudge, 0.0, 0.0});
         steps.add(new double[]{-nudge, 0.0, 0.0});
      }

      if (z) {
         steps.add(new double[]{0.0, 0.0, nudge});
         steps.add(new double[]{0.0, 0.0, -nudge});
      }

      if (x && z) {
         steps.add(new double[]{nudge, 0.0, nudge});
         steps.add(new double[]{nudge, 0.0, -nudge});
         steps.add(new double[]{-nudge, 0.0, nudge});
         steps.add(new double[]{-nudge, 0.0, -nudge});
      }
   }

   /** Offsets tried by {@link #nearestClear}, nearest first: within 0.6 blocks sideways and a block up or down. */
   static final double[] SIDEWAYS = {0.0, 0.3, -0.3, 0.6, -0.6};
   static final double[] VERTICAL = {0.0, 0.5, -0.5, 1.0, -1.0};
   static final double MAX_SIDEWAYS = 0.6 + 1.0E-9;
   /** The pearl rests on a block's surface, so a line from it may touch that surface right at the pearl. */
   static final double SURFACE = 0.05;
   /** Height of the pearl's centre above its position. */
   static final double PEARL_MIDDLE = 0.125;

   /**
    * The nearest place to put a player whose landing spot ({@code x, y, z}: feet, where the pearl stopped) is inside a
    * block, preferring spots on the side they threw from. Only spots the pearl could reach in a straight line without
    * passing through a block are used, checked both ways, so the far side of a pane, door, bars or wall is never
    * picked. Null when there is none, and the pearl should not land.
    */
   public static double[] nearestClear(double x, double y, double z, double fromX, double fromY, double fromZ, double width, double height,
                                       Collision world, Passage passage) {
      if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z) || !(width > 0.0) || !(height > 0.0) || width > 4.0 || height > 8.0) {
         return null;
      }

      double half = width / 2.0;
      List<double[]> candidates = new ArrayList<>();
      for (double dy : VERTICAL) {
         for (double dx : SIDEWAYS) {
            for (double dz : SIDEWAYS) {
               if (dx * dx + dz * dz <= MAX_SIDEWAYS * MAX_SIDEWAYS) {
                  candidates.add(new double[]{x + dx, y + dy, z + dz, dx * dx + dy * dy + dz * dz});
               }
            }
         }
      }

      candidates.sort((a, b) -> {
         int byShift = Double.compare(a[3], b[3]);
         if (byShift != 0) {
            return byShift;
         }

         return Double.compare(square(a[0] - fromX, a[1] - fromY, a[2] - fromZ), square(b[0] - fromX, b[1] - fromY, b[2] - fromZ));
      });
      double pearlY = y + PEARL_MIDDLE;
      for (double[] c : candidates) {
         if (world.collides(c[0] - half, c[1], c[2] - half, c[0] + half, c[1] + height, c[2] + half)) {
            continue;
         }

         double distance = Math.sqrt(square(x - c[0], y - c[1], z - c[2]));
         if (distance < 1.0E-6) {
            return new double[]{c[0], c[1], c[2]};
         }

         double spotY = c[1] + PEARL_MIDDLE;
         if (clear(passage.blockedAt(x, pearlY, z, c[0], spotY, c[2]), distance)
            && clear(passage.blockedAt(c[0], spotY, c[2], x, pearlY, z), distance)) {
            return new double[]{c[0], c[1], c[2]};
         }
      }

      return null;
   }

   /** Nothing in the way, or only the surface the pearl itself rests on, right at the end of the line. */
   private static boolean clear(double blockedAt, double distance) {
      return blockedAt < 0.0 || blockedAt >= distance - SURFACE;
   }

   private static double square(double dx, double dy, double dz) {
      return dx * dx + dy * dy + dz * dz;
   }

   /** How far along the straight line from the first point to the second a block is hit, or -1 when nothing is. */
   @FunctionalInterface
   public interface Passage {
      double blockedAt(double x1, double y1, double z1, double x2, double y2, double z2);
   }

   @FunctionalInterface
   public interface Collision {
      boolean collides(double var1, double var3, double var5, double var7, double var9, double var11);
   }

   public enum Face {
      UP(0, 0),
      DOWN(0, 0),
      NORTH(0, -1),
      SOUTH(0, 1),
      EAST(1, 0),
      WEST(-1, 0),
      NONE(0, 0);

      private final int nx;
      private final int nz;

      Face(int nx, int nz) {
         this.nx = nx;
         this.nz = nz;
      }

      public static PearlLanding.Face of(BlockFace face) {
         if (face == null) {
            return NONE;
         }

         return switch (face) {
            case UP -> UP;
            case DOWN -> DOWN;
            case NORTH -> NORTH;
            case SOUTH -> SOUTH;
            case EAST -> EAST;
            case WEST -> WEST;
            default -> NONE;
         };
      }

      boolean side() {
         return this.nx != 0 || this.nz != 0;
      }
   }
}
