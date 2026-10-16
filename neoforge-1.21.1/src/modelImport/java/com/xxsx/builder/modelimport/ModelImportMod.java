package com.xxsx.builder.modelimport;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

/** Optional PMX/OBJ model-import extension for Xxsx Smart Core. */
@Mod(value = ModelImportMod.MODID, dist = Dist.CLIENT)
public final class ModelImportMod {
    public static final String MODID = "xxsx_builder";
    private ModelImportHandler handler;

    public ModelImportMod(IEventBus modBus, ModContainer modContainer) {
        NeoForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        handler = new ModelImportHandler(event.getServer());
        ModelImportBridge.register(handler);
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        ModelImportBridge.unregister(handler);
        handler = null;
    }
}
