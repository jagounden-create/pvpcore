package com.pvpcore.module;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PearlLandingTest {
   /** A world with a floor below y = 64 and a wall at x >= 5. */
   static final PearlLanding.Collision WORLD = (minX, minY, minZ, maxX, maxY, maxZ) -> minY < 64.0 || maxX > 5.0;

   @Test
   void landsOnTopOfTheFloor() {
      double[] feet = PearlLanding.find(0.5, 64.0, 0.5, PearlLanding.Face.UP, 0.6, 1.8, WORLD);
      assertNotNull(feet);
      assertArrayEquals(new double[]{0.5, 64.001, 0.5}, feet, 1.0E-9);
   }

   @Test
   void stepsOutOfAWallItHit() {
      double[] feet = PearlLanding.find(5.0, 65.0, 0.5, PearlLanding.Face.WEST, 0.6, 1.8, WORLD);
      assertNotNull(feet);
      assertTrue(feet[0] + 0.3 <= 5.0, "feet box must be clear of the wall");
   }

   @Test
   void givesUpWhenEverySpotIsBlocked() {
      assertNull(PearlLanding.find(0.5, 64.0, 0.5, PearlLanding.Face.UP, 0.6, 1.8, (a, b, c, d, e, f) -> true));
      assertNull(PearlLanding.find(Double.NaN, 64.0, 0.5, PearlLanding.Face.UP, 0.6, 1.8, WORLD));
   }

   // ------------------------------------------------------------------ anti-glitch

   /** Boxes {minX, minY, minZ, maxX, maxY, maxZ}; a world made of them. */
   record Boxes(double[]... boxes) implements PearlLanding.Collision, PearlLanding.Passage {
      @Override
      public boolean collides(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
         for (double[] b : this.boxes) {
            if (minX < b[3] && maxX > b[0] && minY < b[4] && maxY > b[1] && minZ < b[5] && maxZ > b[2]) {
               return true;
            }
         }

         return false;
      }

      /** Like the game's ray clip: a box is hit where the line enters it through a face. */
      @Override
      public double blockedAt(double x1, double y1, double z1, double x2, double y2, double z2) {
         double[] from = {x1, y1, z1};
         double[] d = {x2 - x1, y2 - y1, z2 - z1};
         double length = Math.sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2]);
         double best = Double.POSITIVE_INFINITY;
         for (double[] b : this.boxes) {
            for (int axis = 0; axis < 3; axis++) {
               if (d[axis] == 0.0) {
                  continue;
               }

               double plane = d[axis] > 0.0 ? b[axis] : b[axis + 3];
               double t = (plane - from[axis]) / d[axis];
               if (t < 0.0 || t > 1.0) {
                  continue;
               }

               boolean inside = true;
               for (int other = 0; other < 3; other++) {
                  if (other != axis) {
                     double at = from[other] + d[other] * t;
                     inside &= at >= b[other] && at <= b[other + 3];
                  }
               }

               if (inside) {
                  best = Math.min(best, t * length);
               }
            }
         }

         return best == Double.POSITIVE_INFINITY ? -1.0 : best;
      }
   }

   static final double[] FLOOR = {-50, 0, -50, 50, 64, 50};
   /** A three-high glass pane wall along z, 1/8 block thick. */
   static final double[] PANE = {5.4375, 64, -50, 5.5625, 67, 50};

   @Test
   void aClearLandingIsKept() {
      Boxes world = new Boxes(FLOOR, PANE);
      assertArrayEquals(new double[]{2.0, 64.0, 0.5}, PearlLanding.nearestClear(2.0, 64.0, 0.5, 0.0, 64.0, 0.5, 0.6, 1.8, world, world), 1.0E-9);
   }

   @Test
   void aPearlAgainstAPaneLandsOnTheThrowersSide() {
      Boxes world = new Boxes(FLOOR, PANE);
      double[] feet = PearlLanding.nearestClear(5.4375, 64.0, 0.5, 0.0, 64.0, 0.5, 0.6, 1.8, world, world);
      assertNotNull(feet);
      assertTrue(feet[0] + 0.3 <= 5.4375 + 1.0E-9, "stays west of the pane: " + feet[0]);
      assertTrue(feet[1] >= 64.0, "never in the floor");
      assertFalse(world.collides(feet[0] - 0.3, feet[1], feet[2] - 0.3, feet[0] + 0.3, feet[1] + 1.8, feet[2] + 0.3));
   }

   @Test
   void neverThroughAPaneEvenWhenTheNearSideIsBlocked() {
      double[] wall = {4.5, 64, -50, 5.0, 70, 50};
      Boxes world = new Boxes(FLOOR, PANE, wall);
      assertNull(PearlLanding.nearestClear(5.4375, 64.0, 0.5, 0.0, 64.0, 0.5, 0.6, 1.8, world, world));
   }

   @Test
   void neverThroughTheFloorUnderALowCeiling() {
      double[] ceiling = {-50, 65.5, -50, 50, 70, 50};
      Boxes world = new Boxes(FLOOR, ceiling);
      assertNull(PearlLanding.nearestClear(0.5, 64.0, 0.5, 0.0, 64.0, -5.0, 0.6, 1.8, world, world));
   }

   @Test
   void stepsOutOfACornerOnTheOpenSide() {
      double[] wallX = {1.0, 64, -50, 2.0, 70, 50};
      Boxes world = new Boxes(FLOOR, wallX);
      // feet box pokes 0.2 into the wall east of the landing spot
      double[] feet = PearlLanding.nearestClear(0.9, 64.0, 0.5, -5.0, 64.0, 0.5, 0.6, 1.8, world, world);
      assertNotNull(feet);
      assertEquals(0.6, feet[0], 1.0E-9);
      assertEquals(64.0, feet[1], 1.0E-9);
   }

   @Test
   void rejectsNonsense() {
      Boxes world = new Boxes(FLOOR);
      assertNull(PearlLanding.nearestClear(Double.NaN, 64.0, 0.5, 0.0, 64.0, 0.5, 0.6, 1.8, world, world));
      assertNull(PearlLanding.nearestClear(0.5, 64.0, 0.5, 0.0, 64.0, 0.5, 0.0, 1.8, world, world));
   }
}
