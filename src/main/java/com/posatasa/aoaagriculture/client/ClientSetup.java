package com.posatasa.aoaagriculture.client;

import com.posatasa.aoaagriculture.registry.ChilliWater;
import com.posatasa.aoaagriculture.registry.FarmlandBlocks;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.RenderTypeLookup;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Client-side rendering setup: the chilli water block has to be drawn translucent.
 *
 * <p>Fluids are drawn through their block's render layer, and vanilla registers water and lava there
 * by hand - water as {@code translucent}. A fluid block of our own is not registered anywhere, so it
 * gets the default solid layer, which draws it opaque however transparent its texture is. That is the
 * whole difference between this fluid and AoA's candied water, which reuses vanilla's water textures
 * and therefore vanilla's registration.
 */
@Mod.EventBusSubscriber(modid = FarmlandBlocks.MOD_ID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ClientSetup {
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        RenderTypeLookup.setRenderLayer(ChilliWater.BLOCK.get(), RenderType.translucent());
    }

    private ClientSetup() {}
}
