package org.kilocraft.essentials.commands.moderation;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import net.minecraft.command.arguments.GameProfileArgumentType.GameProfileArgument;
import net.minecraft.server.command.CommandSource;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.TranslatableText;

import org.kilocraft.essentials.KiloCommands;
import org.kilocraft.essentials.api.KiloServer;
import org.kilocraft.essentials.api.chat.LangText;
import org.kilocraft.essentials.api.command.DateArgument;
import org.kilocraft.essentials.user.punishment.BanEntryType;
import org.kilocraft.essentials.user.punishment.PunishmentManager;
import org.kilocraft.essentials.util.messages.nodes.ExceptionMessageNode;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;

import static com.mojang.brigadier.arguments.StringArgumentType.*;
import static net.minecraft.command.arguments.GameProfileArgumentType.gameProfile;
import static net.minecraft.command.arguments.GameProfileArgumentType.getProfileArgument;
import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;
import static org.kilocraft.essentials.KiloCommands.*;
import static org.kilocraft.essentials.user.punishment.BanEntryType.IP;
import static org.kilocraft.essentials.user.punishment.BanEntryType.PROFILE;

public class ProfileBanCommand {
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        LiteralArgumentBuilder<ServerCommandSource> banCommand = literal("ke_ban")
                .requires(src -> hasPermission(src, "ban", 3))
                .executes(KiloCommands::executeSmartUsage);

        buildArguments(banCommand);
        dispatcher.register(banCommand);
    }

    private static void buildArguments(LiteralArgumentBuilder<ServerCommandSource> mainArgument) {
        LiteralArgumentBuilder<ServerCommandSource> setArgument = literal("set")
                .requires(src -> hasPermission(src, "ban.set", 3));

        RequiredArgumentBuilder<ServerCommandSource, GameProfileArgument> targetArgument = argument("gameProfile", gameProfile());
        
        RequiredArgumentBuilder<ServerCommandSource, String> banTypeArgument = argument("type", string())
                .suggests(ProfileBanCommand::CompletableBanType);

        LiteralArgumentBuilder<ServerCommandSource> banPermanentArgument = literal("permanent")
                .then(argument("reason", greedyString()).executes(ctx -> {
                    executeSet(ctx, BanEntryType.PROFILE);
                    return 1;
            }));

        LiteralArgumentBuilder<ServerCommandSource> banTemporaryArgument = literal("temporarly")
                .then(argument("time", string())
                    .suggests(DateArgument::suggestions)
                    .then(argument("reason", greedyString())
                    .executes(ctx -> {
                    	executeSet(ctx, BanEntryType.PROFILE);
                        return 1;
                    }))
                );


        targetArgument.then(banPermanentArgument);
        targetArgument.then(banTemporaryArgument);
        banTypeArgument.then(targetArgument);
        setArgument.then(banTypeArgument);
        
        LiteralArgumentBuilder<ServerCommandSource> removeArgument = literal("clear")
                .requires(src -> hasPermission(src, "ban.clear", 3))
                .then(argument("gameProfile", gameProfile())
                        .then(argument("type", StringArgumentType.string())
                        .executes(ctx -> executeClear(ctx, ""))
                        .suggests(ProfileBanCommand::CompletableBanType)
                            .then(argument("reason", greedyString())
                                .executes(ctx -> executeClear(ctx, getString(ctx, "reason")))
                            )
                        )
                );

        LiteralArgumentBuilder<ServerCommandSource> listArgument = literal("list")
                .requires(src -> hasPermission(src, "ban.list", 3)).executes(ctx -> executeList(ctx));

        LiteralArgumentBuilder<ServerCommandSource> checkArgument = literal("check")
                .requires(src -> hasPermission(src, "ban.check", 3))
                .then(argument("gameProfile", gameProfile())).executes(ctx -> executeCheck(ctx));


        mainArgument.then(setArgument);
        mainArgument.then(removeArgument);
        mainArgument.then(listArgument);
        mainArgument.then(checkArgument);
    }

    private static PunishmentManager punishmentManager = KiloServer.getServer().getUserManager().getPunishmentManager();

    private static int executeSet(CommandContext<ServerCommandSource> ctx, BanEntryType type) throws CommandSyntaxException {
        String reason = getString(ctx, "reason");
        ServerCommandSource src = ctx.getSource();
        Collection<GameProfile> gameProfiles = getProfileArgument(ctx, "gameProfile");
        if (gameProfiles.size() > 1)
            throw getException(ExceptionMessageNode.TOO_MANY_SELECTIONS).create();

        while (gameProfiles.iterator().hasNext()) {
            GameProfile target = gameProfiles.iterator().next();

            if (ctx.getInput().contains("permanent")) {
            	src.getPlayer().sendMessage(
    					LangText.getFormatter(true, "command.ban.success", target.getName()));
            	punishmentManager.ban(target, type, reason);         	
        	}
            else if (ctx.getInput().contains("temporarly")) {
                DateArgument dArg = DateArgument.complex(getString(ctx, "time"));
                src.getPlayer().sendMessage(
    					LangText.getFormatter(true, "command.ban.success.temporarly", target.getName(), dArg.getDate()));
            
                punishmentManager.ban(target, type, reason, dArg.getDate());
            }
            else {
                throw getException(ExceptionMessageNode.ILLEGAL_STRING_ARGUMENT, "time argument").create();
            }
        }

        return SUCCESS();
    }

    public static int executeClear(CommandContext<ServerCommandSource> ctx, String reason) throws CommandSyntaxException {
        ServerCommandSource src = ctx.getSource();
        Collection<GameProfile> gameProfiles = getProfileArgument(ctx, "gameProfile");
        String type = getString(ctx, "type");

        if (gameProfiles.size() > 1)
            throw getException(ExceptionMessageNode.TOO_MANY_SELECTIONS).create();

        while (gameProfiles.iterator().hasNext()) {
            GameProfile target = gameProfiles.iterator().next();

            if (type.equals(PROFILE.name().toLowerCase()))
                punishmentManager.pardon(target, PROFILE);
            else
                punishmentManager.pardon(target, IP);
        }

        return SUCCESS();
    }

    private static int executeList(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        //TODO: Make a list with multiple pages
    	ctx.getSource().sendFeedback(new TranslatableText("commands.banlist.list", new Object[]{ctx.getSource().getMinecraftServer().getPlayerManager().getUserBanList().values()}), false);
        return SUCCESS();
    }

    private static int executeCheck(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        //TODO: Accually check
    	ServerCommandSource src = ctx.getSource();
        Collection<GameProfile> gameProfiles = getProfileArgument(ctx, "gameProfile");

        if (gameProfiles.size() > 1)
            throw getException(ExceptionMessageNode.TOO_MANY_SELECTIONS).create();

        while (gameProfiles.iterator().hasNext()) {
            GameProfile target = gameProfiles.iterator().next();
        }

        return SUCCESS();
    }

    private static CompletableFuture<Suggestions> CompletableBanType(CommandContext<ServerCommandSource> context, SuggestionsBuilder builder) {
        return CommandSource.suggestMatching(new String[]{"profile", "ip"}, builder);
    }

}
