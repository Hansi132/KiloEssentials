package org.kilocraft.essentials;

import net.fabricmc.api.ModInitializer;

public class Init implements ModInitializer {
    @Override
    public void onInitialize() {
        KiloEssentials.getLogger.info("KiloEssentials is loading...");
    }
}