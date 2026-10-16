package com.xxsx.builder.modelimport;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Optional PMX/OBJ model-import extension for Xxsx Smart Core. */
@Mod(ModelImportMod.MODID)
public final class ModelImportMod {
    public static final String MODID = "xxsx_builder";
    private ModelImportHandler handler;

    public ModelImportMod() { MinecraftForge.EVENT_BUS.register(this); }

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
