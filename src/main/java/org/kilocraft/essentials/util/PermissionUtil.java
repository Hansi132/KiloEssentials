package org.kilocraft.essentials.util;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.indicode.fabric.permissions.PermChangeBehavior;
import io.github.indicode.fabric.permissions.Thimble;
import net.fabricmc.loader.api.FabricLoader;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.query.QueryOptions;
import net.minecraft.SharedConstants;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import org.apache.logging.log4j.core.Logger;
import org.jetbrains.annotations.NotNull;
import org.kilocraft.essentials.CommandPermission;
import org.kilocraft.essentials.EssentialPermission;
import org.kilocraft.essentials.api.KiloEssentials;
import org.kilocraft.essentials.config.KiloConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class PermissionUtil {
    private static final Logger logger = (Logger) KiloEssentials.getLogger();
    private static final List<String> pendingPermissions = new ArrayList<>();
    public static String COMMAND_PERMISSION_PREFIX = "kiloessentials.command.";
    public static String PERMISSION_PREFIX = "kiloessentials.";
    private boolean present;
    private Manager manager;

    public PermissionUtil() {
        logger.info("Setting up Permissions...");
        this.manager = Manager.fromString(KiloConfig.main().permissionManager());

        if (manager == Manager.VANILLA) {
            this.present = false;
            return;
        }

        logger.info("Checking {} for Availability", manager.getName());

        this.present = this.checkPresent();

        if (!this.present) {
            logger.warn("**** {} is not present! Switching to vanilla operator system", manager.getName());
            logger.warn("     You need to install either LuckPerms for Fabric Or Thimble to manage the permissions");
            this.manager = Manager.NONE;
            return;
        }

        logger.info("Using {} as the Permission Manager", manager.getName());

        if (manager == Manager.THIMBLE) {
            Thimble.permissionWriters.add((map, server) -> {
                for (String pendingPermission : pendingPermissions) {
                    if (!map.permissionExists(pendingPermission)) {
                        map.registerPermission(pendingPermission, PermChangeBehavior.UPDATE_COMMAND_TREE);
                    }
                }

                for (final EssentialPermission perm : EssentialPermission.values()) {
                    if (!map.permissionExists(perm.getNode())) {
                        if(SharedConstants.isDevelopment) KiloEssentials.getLogger().info("Registering: " + perm.getNode());
                        map.registerPermission(perm.getNode(), PermChangeBehavior.UPDATE_COMMAND_TREE);
                    }
                }

                for (final CommandPermission perm : CommandPermission.values()) {
                    if (!map.permissionExists(perm.getNode())) {
                        if(SharedConstants.isDevelopment) KiloEssentials.getLogger().info("Registering: " + perm.getNode());
                        map.registerPermission(perm.getNode(), PermChangeBehavior.UPDATE_COMMAND_TREE);
                    }
                }

                if (KiloConfig.main().features().playerHomes) {
                    for (int i = 1; i <= KiloConfig.main().homesLimit; i++) {
                        String perm = CommandPermission.HOME_LIMIT.getNode() + "." + i;
                        if (!map.permissionExists(perm)) {
                            if(SharedConstants.isDevelopment) KiloEssentials.getLogger().info("Registering: " + perm);
                            map.registerPermission(perm, PermChangeBehavior.UPDATE_COMMAND_TREE);
                        }
                    }
                }

                if (KiloConfig.main().features().playerWarps) {
                    for (int i = 1; i <= KiloConfig.main().playerWarpsLimit; i++) {
                        String perm = CommandPermission.PLAYER_WARP_LIMIT.getNode() + "." + i;
                        if (!map.permissionExists(perm)) {
                            if(SharedConstants.isDevelopment) KiloEssentials.getLogger().info("Registering: " + perm);
                            map.registerPermission(perm, PermChangeBehavior.UPDATE_COMMAND_TREE);
                        }
                    }
                }
            });
        }

        logger.info("Registered " + (CommandPermission.values().length + EssentialPermission.values().length) + " permission nodes.");
    }

    public boolean hasPermission(ServerCommandSource src, String permission, int opLevel) {
        if (this.present) {
            if (manager == Manager.LUCKPERMS) {
                return fromLuckPerms(src, permission, opLevel);
            }

            if (manager == Manager.THIMBLE) {
                return fromThimble(src, permission, opLevel);
            }
        }

        return src.hasPermissionLevel(opLevel);
    }

    public static void registerNode(final String node) {
        pendingPermissions.add(node);
    }

    private boolean fromLuckPerms(ServerCommandSource src, String perm, int op) {
        LuckPerms luckPerms = LuckPermsProvider.get();

        try {
            ServerPlayerEntity player = src.getPlayer();
            User user = luckPerms.getUserManager().getUser(player.getUuid());

            if (user != null) {
                QueryOptions options = luckPerms.getContextManager().getQueryOptions(player);
                return user.getCachedData().getPermissionData(options).checkPermission(perm).asBoolean();
            }

        } catch (CommandSyntaxException ignored) {
        }

        return src.hasPermissionLevel(op);
    }

    private boolean fromThimble(ServerCommandSource src, String perm, int op) {
        return Thimble.hasPermissionOrOp(src, perm, op);
    }

    private boolean checkPresent() {
        if (manager == Manager.NONE) {
            return false;
        }

        try {
            if (manager == Manager.LUCKPERMS) {
                try {
                    LuckPermsProvider.get();
                    return true;
                } catch (Throwable ignored) {
                }
            }

            if (manager == Manager.THIMBLE) {
                Thimble.permissionWriters.get(0);
            }

            return FabricLoader.getInstance().getModContainer(manager.getName().toLowerCase(Locale.ROOT)).isPresent();
        } catch (Exception ignored) {
            return false;
        }
    }

    public boolean managerPresent() {
        return this.present;
    }

    public Manager getManager() {
        return this.manager;
    }

    public enum Manager {
        NONE("none", ""),
        VANILLA("Vanilla", ""),
        LUCKPERMS("LuckPerms", "net.luckperms.api.LuckPerms"),
        THIMBLE("Thimble", "io.github.indicode.fabric.permissions.Thimble");

        private final String name;
        private final String classPath;

        Manager(final String name, final String classPath) {
            this.name = name;
            this.classPath = classPath;
        }

        public String getName() {
            return this.name;
        }

        @NotNull
        public static Manager fromString(@NotNull final String str) {
            for (Manager value : Manager.values()) {
                if (value.name.equalsIgnoreCase(str)) {
                    return value;
                }
            }

            return Manager.NONE;
        }
    }

    @Override
    public String toString() {
        return this.manager.name;
    }

}
