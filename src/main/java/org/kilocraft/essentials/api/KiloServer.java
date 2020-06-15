package org.kilocraft.essentials.api;

import net.minecraft.server.MinecraftServer;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.kilocraft.essentials.KiloDebugUtils;
import org.kilocraft.essentials.KiloEssentialsImpl;
import org.kilocraft.essentials.ServerImpl;
import org.kilocraft.essentials.api.server.Server;
import org.kilocraft.essentials.events.EventRegistryImpl;
import org.kilocraft.essentials.user.ServerUserManager;

public class KiloServer {
    private static Server server;

    /**
     * Get the global server instance
     *
     * @return The server instance
     */
    public static Server getServer() {
        if (server == null) {
            throw new RuntimeException("Server isn't set!");
        }

        return server;
    }

    /**
     * Sets the global server instance
     * <b>Should not be used by mods!</b>
     *
     * @param minecraftServer Server instance
     */
    public static void setupServer(@NotNull final MinecraftServer minecraftServer) {
        new ModConstants().loadConstants();
        String brand = String.format(
                ModConstants.getProperties().getProperty("server.brand.full"),
                ModConstants.getMinecraftVersion(),
                ModConstants.getLoaderVersion(),
                ModConstants.getMappingsVersion(),
                ModConstants.getVersion()
        );

        server = new ServerImpl(
                minecraftServer,
                new EventRegistryImpl(),
                new ServerUserManager(minecraftServer.getPlayerManager()),
                brand
        );

        KiloServer.getLogger().info("Server set: " + brand);
        KiloEssentialsImpl.onServerSet(server);
    }

    public static Logger getLogger() {
        return server.getLogger();
    }

}
