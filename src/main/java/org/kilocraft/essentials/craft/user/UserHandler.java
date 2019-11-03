package org.kilocraft.essentials.craft.user;

import net.minecraft.nbt.NbtIo;
import org.kilocraft.essentials.craft.config.KiloConifg;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Date;

public class UserHandler {
    private static File saveDir = new File(System.getProperty("user.dir") + "/KiloEssentials/users/");

    public void handleUser(User user) {
        try {
            if (!loadData(user)) {
                saveData(user);
                user.setFirstJoin(new Date());
            }
        } catch (IOException ignored) {
            //PASS
        }
    }

    public boolean loadData(User user) throws IOException {
        boolean exists = getUserFile(user).exists();
        if (exists) {
            user.deserialize(
                    NbtIo.readCompressed(new FileInputStream(getUserFile(user)))
            );
        }
        return exists;
    }

    public boolean saveData(User user) throws IOException {
        if (!saveDir.exists()) saveDir.mkdirs();
        boolean made = getUserFile(user).createNewFile();
        if (getUserFile(user).exists())
            NbtIo.writeCompressed(
                    user.serialize(),
                    new FileOutputStream(getUserFile(user))
            );

        return made;
    }

    private File getUserFile(User user) {
        return new File( KiloConifg.getWorkingDirectory() + "/KiloEssentials/users/" + user.getUuidAsString() + ".dat");
    }

}
