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
 * Client-side rendering setup: chilli water is drawn translucent.
 *
 * <p>A fluid is drawn through the render layer of the *fluid* - Forge adds a {@code setRenderLayer}
 * overload for fluids for exactly this. Vanilla registers water and lava there by hand; a fluid of our
 * own is registered nowhere and gets the default solid layer, which draws it opaque however transparent
 * its texture is. That is the whole difference from AoA's candied water, which reuses vanilla's water
 * textures and so rides on vanilla's registration - while AoA's own fluids register both the block and
 * the fluid here.
 */
@Mod.EventBusSubscriber(modid = FarmlandBlocks.MOD_ID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ClientSetup {
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        RenderType translucent = RenderType.translucent();
        RenderTypeLookup.setRenderLayer(ChilliWater.STILL.get(), translucent);
        RenderTypeLookup.setRenderLayer(ChilliWater.FLOWING.get(), translucent);
        RenderTypeLookup.setRenderLayer(ChilliWater.BLOCK.get(), translucent);
    }

    private ClientSetup() {}
}
