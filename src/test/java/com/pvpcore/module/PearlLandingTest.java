package com.pvpcore.module;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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
}
