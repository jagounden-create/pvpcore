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
