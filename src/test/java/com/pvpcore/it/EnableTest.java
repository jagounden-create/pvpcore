package com.pvpcore.it;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.pvpcore.PvPCore;
import com.pvpcore.menu.Menu;
import java.io.File;
import org.bukkit.inventory.Inventory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

class EnableTest {
   ServerMock server;
   PvPCore plugin;

   @BeforeEach
   void setUp() {
      this.server = MockBukkit.mock();
      this.plugin = MockBukkit.load(PvPCore.class);
      this.plugin.settings().set(com.pvpcore.Feature.HEALTH_BELOW_NAME, false);
      this.plugin.applyAll();
   }

   @AfterEach
   void tearDown() {
      MockBukkit.unmock();
   }

   @Test
   void enablesAndWritesItsFiles() {
      assertTrue(this.plugin.isEnabled());
      assertTrue(new File(this.plugin.getDataFolder(), "config.yml").isFile());
      assertTrue(new File(this.plugin.getDataFolder(), "rules.yml").isFile());
   }

   @Test
   void commandOpensTheMenu() {
      PlayerMock admin = this.server.addPlayer();
      admin.setOp(true);
      admin.performCommand("pvpcore");
      this.server.getScheduler().performTicks(2);
      Inventory top = admin.getOpenInventory().getTopInventory();
      assertNotNull(top);
      assertInstanceOf(Menu.class, this.plugin.menus().find(top));
      assertEquals(27, top.getSize());
   }
}
