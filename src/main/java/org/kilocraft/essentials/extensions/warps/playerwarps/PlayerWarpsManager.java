package org.kilocraft.essentials.extensions.warps.playerwarps;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.kilocraft.essentials.CommandPermission;
import org.kilocraft.essentials.KiloCommands;
import org.kilocraft.essentials.api.KiloEssentials;
import org.kilocraft.essentials.api.KiloServer;
import org.kilocraft.essentials.api.NBTStorage;
import org.kilocraft.essentials.api.feature.RelodableConfigurableFeature;
import org.kilocraft.essentials.config.KiloConfig;
import org.kilocraft.essentials.extensions.warps.playerwarps.commands.PlayerWarpCommand;
import org.kilocraft.essentials.extensions.warps.playerwarps.commands.PlayerWarpsCommand;
import org.kilocraft.essentials.provided.KiloFile;
import org.kilocraft.essentials.util.nbt.NBTStorageUtil;
import org.kilocraft.essentials.util.nbt.NBTTypes;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class PlayerWarpsManager implements RelodableConfigurableFeature, NBTStorage {
    private static boolean enabled = false;
    private static final List<UUID> warpOwners = new ArrayList<>();
    private static final ArrayList<String> byName = new ArrayList<>();
    private static final List<PlayerWarp> warps = new ArrayList<>();

    @Override
    public boolean register() {
        enabled = true;
        NBTStorageUtil.addCallback(this);

        KiloEssentials.getInstance().getCommandHandler().register(
                new PlayerWarpCommand("playerwarp", src ->
                        KiloCommands.hasPermission(src, CommandPermission.PLAYER_WARP_SELF) ||
                                KiloCommands.hasPermission(src, CommandPermission.PLAYER_WARP_OTHERS)
                        , new String[]{"pwarp"})
        );

        KiloEssentials.getInstance().getCommandHandler().register(
                new PlayerWarpsCommand("playerwarps", CommandPermission.PLAYER_WARPS, new String[]{"pwarps"})
        );

        load();
        return true;
    }

    @Override
    public void load() {
        PlayerWarp.Type.getTypes().clear();

        for (String type : KiloConfig.main().playerWarpTypes) {
            PlayerWarp.Type.add(type);
        }
    }

    public static List<PlayerWarp> getWarps() { // TODO Move all access to Feature Types in future.
        return warps;
    }

    public static ArrayList<String> getWarpsByName() {
        return byName;
    }

    public static void addWarp(PlayerWarp warp) {
        warps.add(warp);
        byName.add(warp.getName());
        warpOwners.add(warp.getOwner());
    }

    public static void removeWarp(PlayerWarp warp) {
        warps.remove(warp);
        byName.remove(warp.getName());
        warpOwners.remove(warp.getOwner());
    }

    public static void removeWarp(String name) {
        PlayerWarp warp = getWarp(name);

        if (warp != null) {
            removeWarp(warp);
        }
    }

    @Nullable
    public static PlayerWarp getWarp(String warp) {
        for (PlayerWarp w : warps) {
            if (w.getName().toLowerCase(Locale.ROOT).equals(warp.toLowerCase(Locale.ROOT))) {
                return w;
            }
        }

        return null;
    }

    public static List<PlayerWarp> getWarps(UUID owner) {
        final List<PlayerWarp> list = new ArrayList<>();
        for (PlayerWarp warp : warps) {
            if (warp.getOwner().equals(owner)) {
                list.add(warp);
            }
        }

        return list;
    }

    public static List<UUID> getOwners() {
        return warpOwners;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    @Override
    public KiloFile getSaveFile() {
        return new KiloFile("player_warps.dat", KiloEssentials.getDataDirPath());
    }

    @Override
    public CompoundTag serialize() {
        CompoundTag tag = new CompoundTag();
        for (PlayerWarp warp : warps) {
            tag.put(warp.getName(), warp.toTag());
        }

        return tag;
    }

    @Override
    public void deserialize(@NotNull CompoundTag compoundTag) {
        warps.clear();
        byName.clear();
        warpOwners.clear();

        for (String key : compoundTag.getKeys()) {
            PlayerWarp warp = new PlayerWarp(key, compoundTag.getCompound(key));
            warpOwners.add(warp.getOwner());
            warps.add(warp);
            byName.add(key);
        }

    }
}
