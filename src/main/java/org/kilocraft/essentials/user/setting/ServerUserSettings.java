package org.kilocraft.essentials.user.setting;

import com.google.common.collect.Maps;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.NotNull;
import org.kilocraft.essentials.api.KiloEssentials;
import org.kilocraft.essentials.api.user.settting.Setting;
import org.kilocraft.essentials.api.user.settting.UserSettings;

import java.util.HashMap;
import java.util.Map;

public class ServerUserSettings implements UserSettings {
    private final Map<String, Object> SETTINGS;

    public ServerUserSettings() {
        this.SETTINGS = Maps.newHashMap();
    }

    @Override
    public <T> void set(Setting<T> setting, T value) {
        this.SETTINGS.put(setting.getId(), value);
    }

    @Override
    public <T> T get(Setting<T> setting) {
        return (T) this.SETTINGS.getOrDefault(setting.getId(), setting.getDefault());
    }

    @Override
    public <T> void reset(Setting<T> setting) {
        this.SETTINGS.remove(setting.getId());
    }

    @Override
    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        for (Map.Entry<String, Object> entry : this.SETTINGS.entrySet()) {
            Setting<?> setting = Settings.getById(entry.getKey());
            if (setting != null) {
                try {
                    setting.toTag(tag, entry.getValue());
                } catch (IllegalArgumentException e) {
                    KiloEssentials.getLogger().fatal("Exception while serializing a User Setting: Can not save the Value", e);
                }
            }
        }

        return tag;
    }

    @Override
    public void fromTag(@NotNull final CompoundTag tag) {
        for (String key : tag.getKeys()) {
            Setting<?> setting = Settings.getById(key);
            if (setting != null) {
                try {
                    this.SETTINGS.put(setting.getId(), setting.fromTag(tag));
                } catch (IllegalArgumentException e) {
                    KiloEssentials.getLogger().fatal("Exception while de-serializing a User Setting: Using Default Value", e);
                }
            }
        }
    }
}
