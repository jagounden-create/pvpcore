package com.pvpcore.module;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.Test;

class CartModuleTest {
   /** A minecart on a rail at 0,64,0: 0.98 wide, 0.7 tall. */
   static final BoundingBox CART = new BoundingBox(-0.49, 64.0, -0.49, 0.49, 64.7, 0.49).expand(CartModule.BOX_SLACK);

   @Test
   void windowFollowsPingWithinTheCap() {
      assertEquals(1, CartModule.window(0, 250));
      assertEquals(3, CartModule.window(100, 250));
      assertEquals(5, CartModule.window(1000, 250));
      assertEquals(1, CartModule.window(-5, 50));
      assertEquals(10, CartModule.window(1000, 500));
   }

   @Test
   void pathThroughTheCartCounts() {
      assertTrue(CartModule.crosses(CART, new CartModule.Segment(0, -3, 64.3, 0, 3, 64.3, 0)));
      assertTrue(CartModule.crosses(CART, new CartModule.Segment(0, 0, 66, 0, 0, 64.2, 0)));
   }

   @Test
   void arrowStuckWhereTheCartAppearedCounts() {
      assertTrue(CartModule.crosses(CART, new CartModule.Segment(0, 0.1, 64.05, 0.1, 0.1, 64.05, 0.1)));
   }

   @Test
   void nearMissesDoNot() {
      assertFalse(CartModule.crosses(CART, new CartModule.Segment(0, -3, 66, 0, 3, 66, 0)));
      assertFalse(CartModule.crosses(CART, new CartModule.Segment(0, 2, 64.2, 2, 2, 64.2, 2)));
      assertFalse(CartModule.crosses(CART, new CartModule.Segment(0, 3, 64.3, 3, 3, 64.3, 3)));
   }
}
