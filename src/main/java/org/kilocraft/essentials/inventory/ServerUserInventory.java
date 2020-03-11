package org.kilocraft.essentials.inventory;


import net.minecraft.inventory.EnderChestInventory;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.TranslatableText;
import org.kilocraft.essentials.user.ServerUser;

public class ServerUserInventory {

    public static void openPlayerContainer(ServerPlayerEntity source, ServerPlayerEntity target) {
    }

    public static void openEnderchest(ServerPlayerEntity source, ServerPlayerEntity target) {
        EnderChestInventory enderChestInventory = target.getEnderChestInventory();
        source.openHandledScreen(new SimpleNamedScreenHandlerFactory((syncId, pInv, pEntity) ->
                GenericContainerScreenHandler.createGeneric9x3(syncId, pInv, enderChestInventory), new TranslatableText("container.enderchest"))
        );
    }

//    public static void openEnderchest(ServerPlayerEntity source, EnderChestInventory inv) {
//        source.openContainer(new SimpleNamedContainerFactory((syncId, pInv, pEntity) ->
//                GenericContainer.createGeneric9x3(syncId, pInv, inv), new TranslatableText("container.enderchest"))
//        );
//    }

    private static void open(ServerUser livingUser, InvType type) {

    }


}
