package org.kilocraft.essentials.user;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.SharedConstants;
import net.minecraft.network.NetworkThreadUtils;
import net.minecraft.network.packet.c2s.play.ChatMessageC2SPacket;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.server.*;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Formatting;
import net.minecraft.util.Pair;
import net.minecraft.util.Util;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.kilocraft.essentials.EssentialPermission;
import org.kilocraft.essentials.KiloDebugUtils;
import org.kilocraft.essentials.api.KiloEssentials;
import org.kilocraft.essentials.api.KiloServer;
import org.kilocraft.essentials.api.event.player.PlayerOnChatMessageEvent;
import org.kilocraft.essentials.api.feature.TickListener;
import org.kilocraft.essentials.api.text.TextFormat;
import org.kilocraft.essentials.api.user.OnlineUser;
import org.kilocraft.essentials.api.user.PunishmentManager;
import org.kilocraft.essentials.api.user.User;
import org.kilocraft.essentials.api.user.UserManager;
import org.kilocraft.essentials.api.user.punishment.Punishment;
import org.kilocraft.essentials.api.util.Cached;
import org.kilocraft.essentials.chat.KiloChat;
import org.kilocraft.essentials.chat.LangText;
import org.kilocraft.essentials.chat.ServerChat;
import org.kilocraft.essentials.chat.TextMessage;
import org.kilocraft.essentials.config.ConfigObjectReplacerUtil;
import org.kilocraft.essentials.config.KiloConfig;
import org.kilocraft.essentials.events.player.PlayerOnChatMessageEventImpl;
import org.kilocraft.essentials.extensions.betterchairs.SeatManager;
import org.kilocraft.essentials.user.setting.Settings;
import org.kilocraft.essentials.util.*;
import org.kilocraft.essentials.util.player.UserUtils;
import org.kilocraft.essentials.util.text.AnimatedText;
import org.kilocraft.essentials.util.text.Texter;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

public class ServerUserManager implements UserManager, TickListener {
    private static final Pattern DAT_FILE_PATTERN = Pattern.compile(".dat");
    private static final Pattern UUID_PATTERN = Pattern.compile("([a-f0-9]{8}(-[a-f0-9]{4}){4}[a-f0-9]{8})");
    private static final Pattern USER_FILE_NAME = Pattern.compile(UUID_PATTERN + "\\.dat");
    private final UserHandler handler = new UserHandler();
    private final ServerPunishmentManager punishmentManager = new ServerPunishmentManager();
    private final List<OnlineUser> users = new ArrayList<>();
    private final Map<String, UUID> nicknameToUUID = new HashMap<>();
    private final Map<String, UUID> usernameToUUID = new HashMap<>();
    private final Map<UUID, OnlineServerUser> onlineUsers = new HashMap<>();
    private final Map<UUID, Pair<Pair<UUID, Boolean>, Long>> teleportRequestsMap = new HashMap<>();
    private final Map<UUID, SimpleProcess<?>> inProcessUsers = new HashMap<>();
    private final MutedPlayerList mutedPlayerList = new MutedPlayerList(new File(KiloEssentials.getDataDirPath() + "/mutes.json"));
    private Map<UUID, String> cachedNicknames = new HashMap<>();

    public ServerUserManager(PlayerManager manager) {
    }

    @Override
    public CompletableFuture<List<User>> getAll() {
        List<User> users = new ArrayList<>();

        for (File file : this.handler.getUserFiles()) {
            if (!file.exists() || !USER_FILE_NAME.matcher(file.getName()).matches()) {
                continue;
            }

            try {
                ServerUser user = new ServerUser(UUID.fromString(DAT_FILE_PATTERN.matcher(file.getName()).replaceFirst("")));
                this.handler.loadUserAndResolveName(user);

                if (user.getUsername() != null) {
                    users.add(user);
                }

            } catch (Exception e) {
                KiloEssentials.getLogger().error("Can not load the user file \"{}\"!", file.getName(), e);
            }
        }

        return CompletableFuture.completedFuture(users);
    }

    @Override
    public CompletableFuture<Optional<User>> getOffline(String username) {
        OnlineUser user = this.getOnlineNickname(username);
        if (user != null) {
            return CompletableFuture.completedFuture(Optional.of(user));
        }

        UUID ret = usernameToUUID.get(username);
        if (ret != null) {
            return getOffline(ret, username);
        }

        return this.getUserAsync(username);
    }

    private CompletableFuture<Optional<User>> getUserAsync(String username) {
        CompletableFuture<GameProfile> profileCompletableFuture = CompletableFuture.supplyAsync(() ->
                KiloServer.getServer().getMinecraftServer().getUserCache().findByName(username)
        );

        return profileCompletableFuture.thenApplyAsync(profile -> this.getOffline(profile).join());
    }

    @Override
    public CompletableFuture<Optional<User>> getOffline(UUID uuid, String username) {
        OnlineUser online = getOnline(uuid);
        if (online != null)
            return CompletableFuture.completedFuture(Optional.of(online));

        if (handler.userExists(uuid)) {
            ServerUser serverUser = new ServerUser(uuid);
            serverUser.name = username;
            return CompletableFuture.completedFuture(Optional.of(serverUser));
        }

        return CompletableFuture.completedFuture(Optional.of(new NeverJoinedUser()));
    }

    @Override
    public CompletableFuture<Optional<User>> getOffline(UUID uuid) {
        OnlineUser online = getOnline(uuid);
        if (online != null)
            return CompletableFuture.completedFuture(Optional.of(online));

        if (handler.userExists(uuid)) {
            ServerUser serverUser = new ServerUser(uuid).useSavedName();
            return CompletableFuture.completedFuture(Optional.of(serverUser));
        }

        return CompletableFuture.completedFuture(Optional.of(new NeverJoinedUser()));
    }

    @Override
    @Nullable
    public CompletableFuture<Optional<User>> getOffline(GameProfile profile) {
        if (profileHasID(profile)) return getOffline(profile.getId(), profile.getName());
        return CompletableFuture.completedFuture(Optional.empty());
    }

    @Override
    public Map<UUID, OnlineServerUser> getOnlineUsers() {
        return onlineUsers;
    }

    @Override
    public List<OnlineUser> getOnlineUsersAsList() {
        return users;
    }

    @Override
    @Nullable
    public OnlineUser getOnline(GameProfile profile) {
        if (profileIsComplete(profile)) return getOnline(profile.getId());
        return null;
    }

    @Override
    @Nullable
    public OnlineUser getOnline(UUID uuid) {
        return onlineUsers.get(uuid);
    }

    @Override
    @Nullable
    public OnlineUser getOnline(String username) {
        OnlineUser user = getOnline(usernameToUUID.get(username));
        return user == null ? getOnlineNickname(username) : user;
    }

    @Override
    @Nullable
    public OnlineUser getOnlineNickname(String nickname) {
        if (usernameToUUID.containsKey(nickname)) {
            return this.getOnline(nickname);
        }

        if (nicknameToUUID.containsKey(nickname)) {
            return this.getOnline(nicknameToUUID.get(nickname));
        }

        for (OnlineUser user : users) {
            if (user.hasNickname()) {
                String nick = org.kilocraft.essentials.api.util.StringUtils.stringToUsername(
                        TextFormat.clearColorCodes(user.getDisplayName()).replaceAll("\\s+", "")
                );

                if (nick.equals(nickname)) {
                    return user;
                }
            }
        }

        return null;
    }

    @Override
    public OnlineUser getOnline(ServerPlayerEntity player) {
        return getOnline(player.getUuid());
    }

    @Override
    public OnlineUser getOnline(ServerCommandSource source) throws CommandSyntaxException {
        return getOnline(source.getPlayer());
    }

    @Override
    public boolean isOnline(User user) {
        return this.onlineUsers.containsKey(user.getUuid());
    }

    public Map<UUID, Pair<Pair<UUID, Boolean>, Long>> getTeleportRequestsMap() {
        return this.teleportRequestsMap;
    }

    public Map<UUID, SimpleProcess<?>> getInProcessUsers() {
        return this.inProcessUsers;
    }

    @Override
    public void saveAllUsers() {
        if (SharedConstants.isDevelopment) {
            KiloDebugUtils.getLogger().info("Saving users data, this may take a while...");
        }

        for (OnlineServerUser user : onlineUsers.values()) {
            try {
                if (SharedConstants.isDevelopment) {
                    KiloDebugUtils.getLogger().info("Saving user \"{}\"", user.getUsername());
                }
                this.handler.save(user);
            } catch (IOException e) {
                KiloEssentials.getLogger().fatal("An unexpected exception occurred when saving a user's data!", e);
            }
        }

        if (SharedConstants.isDevelopment) {
            KiloDebugUtils.getLogger().info("Saved the users data!");
        }
    }

    @Override
    public void onChangeNickname(User user, String oldNick) {
        if (oldNick != null) {
            this.nicknameToUUID.remove(oldNick);
            this.cachedNicknames.remove(user.getUuid());

            user.getNickname().ifPresent((nick) -> {
                this.nicknameToUUID.put(nick, user.getUuid());
                this.cachedNicknames.put(user.getUuid(), org.kilocraft.essentials.api.util.StringUtils.uniformNickname(nick));
            });
        }

        if (user.isOnline()) {
            KiloServer.getServer().sendGlobalPacket(new PlayerListS2CPacket(PlayerListS2CPacket.Action.UPDATE_DISPLAY_NAME, ((OnlineUser) user).asPlayer()));
        }
    }

    @Override
    public PunishmentManager getPunishmentManager() {
        return this.punishmentManager;
    }

    @Override
    public MutedPlayerList getMutedPlayerList() {
        return this.mutedPlayerList;
    }

    @Override
    public void performPunishment(@NotNull Punishment punishment, Punishment.@NotNull Type type, @NotNull Action<Punishment.ActionResult> action) {
        MinecraftServer server = KiloServer.getServer().getMinecraftServer();
        String victimIP = punishment.getVictimIP();
        String source = punishment.getArbiter().getName();
        String reason = punishment.getReason();
        Date date = new Date();
        Date expiry = punishment.getExpiry();
        if (type == Punishment.Type.DENY_ACCESS) {
            GameProfile victim = server.getUserCache().getByUuid(punishment.getVictim().getId());
            if (victim != null) action.perform(Punishment.ActionResult.FAILED);
            String time = expiry == null ? "PERMANENT" : TimeDifferenceUtil.formatDateDiff(date, expiry);
            if (KiloConfig.main().moderation().meta().broadcast) {
                ServerChat.Channel.PUBLIC.sendLangMessage("command.ban.staff", source, victim.getName(), reason, time);
            } else {
                ServerChat.Channel.STAFF.sendLangMessage("command.ban.staff", source, victim.getName(), reason, time);
            }
            BannedPlayerEntry bannedPlayerEntry = new BannedPlayerEntry(victim, date, source, expiry, reason);
            server.getPlayerManager().getUserBanList().add(bannedPlayerEntry);
            ServerPlayerEntity serverPlayerEntity = server.getPlayerManager().getPlayer(victim.getId());
            if (serverPlayerEntity != null) {
                serverPlayerEntity.networkHandler.disconnect(new TextMessage(replaceBanVariables(KiloConfig.main().moderation().disconnectReasons().permBan, bannedPlayerEntry)).toText());
            }
            action.perform(Punishment.ActionResult.SUCCESS);
        } else if (type == Punishment.Type.DENY_ACCESS_IP) {
            String target = punishment.getVictim() == null ? victimIP : victimIP + " (" + punishment.getVictim().getName() + ")";
            String time = expiry == null ? "PERMANENT" : TimeDifferenceUtil.formatDateDiff(date, expiry);
            ServerChat.Channel.STAFF.sendLangMessage("command.ipban.staff", source, target, reason, time);
            BannedIpEntry bannedIpEntry = new BannedIpEntry(victimIP, date, source, expiry, reason);
            server.getPlayerManager().getIpBanList().add(bannedIpEntry);
            List<ServerPlayerEntity> list = server.getPlayerManager().getPlayersByIp(victimIP);
            for (ServerPlayerEntity serverPlayerEntity : list) {
                serverPlayerEntity.networkHandler.disconnect(new TextMessage(replaceBanVariables(KiloConfig.main().moderation().disconnectReasons().permIpBan, bannedIpEntry)).toText());
            }
            action.perform(Punishment.ActionResult.SUCCESS);
        } else {
            GameProfile victim = server.getUserCache().getByUuid(punishment.getVictim().getId());
            String time = expiry == null ? "PERMANENT" : TimeDifferenceUtil.formatDateDiff(date, expiry);
            ServerChat.Channel.STAFF.sendLangMessage("command.mute.staff", source, victim.getName(), reason, time);
            mutedPlayerList.add(new MutedPlayerEntry(victim, date, source, expiry, reason));
            action.perform(Punishment.ActionResult.SUCCESS);
        }
    }

    public String replaceBanVariables(final String str, final BanEntry banEntry) {
        return new ConfigObjectReplacerUtil("ban", str, true)
                .append("reason", banEntry.getReason())
                .append("expiry", banEntry.getExpiryDate() == null ? "Error, please report to administrator" : banEntry.getExpiryDate().toString())
                .append("source", banEntry.getSource())
                .toString();
    }

    public boolean shouldNotUseNickname(OnlineUser user, String rawNickname) {
        String NICKNAME_CACHE = "nicknames";
        if (!CacheManager.shouldUse(NICKNAME_CACHE)) {
            Map<UUID, String> map = new HashMap<>();
            KiloEssentials.getInstance().getAllUsersThenAcceptAsync(user, "general.please_wait", (list) -> {
                for (User victim : list) {
                    victim.getNickname().ifPresent(nick -> {
                        map.put(
                                victim.getUuid(),
                                org.kilocraft.essentials.api.util.StringUtils.uniformNickname(nick).toLowerCase(Locale.ROOT)
                        );
                    });

                    map.put(victim.getUuid(), org.kilocraft.essentials.api.util.StringUtils.uniformNickname(victim.getUsername()).toLowerCase(Locale.ROOT));
                }
            });

            cachedNicknames = map;
            Cached<Map<UUID, String>> cached = new Cached<>(NICKNAME_CACHE, map);
            CacheManager.cache(cached);
        }

        AtomicBoolean canUse = new AtomicBoolean(true);
        String uniformedNickname = org.kilocraft.essentials.api.util.StringUtils.uniformNickname(rawNickname).toLowerCase(Locale.ROOT);

        for (Map.Entry<UUID, String> entry : cachedNicknames.entrySet()) {
            UUID uuid = entry.getKey();
            String string = entry.getValue();
            if (string.equalsIgnoreCase(uniformedNickname) && !user.getUuid().equals(uuid)) {
                canUse.set(false);
                break;
            }
        }

        return !canUse.get();
    }

    private boolean profileIsComplete(GameProfile profile) {
        return profile != null && profile.isComplete();
    }

    private boolean profileHasID(GameProfile profile) {
        return profile != null && profile.getId() != null;
    }

    public void onJoin(ServerPlayerEntity playerEntity) {
        OnlineServerUser serverUser = new OnlineServerUser(playerEntity);

        this.onlineUsers.put(playerEntity.getUuid(), serverUser);
        this.usernameToUUID.put(playerEntity.getGameProfile().getName(), playerEntity.getUuid());
        this.users.add(serverUser);

        serverUser.getNickname().ifPresent((nick) -> this.nicknameToUUID.put(nick, playerEntity.getUuid()));
    }

    public void onJoined(ServerPlayerEntity playerEntity) {
        OnlineServerUser user = (OnlineServerUser) this.getOnline(playerEntity);
        user.onJoined();
        KiloChat.onUserJoin(user);
    }

    public void onLeave(ServerPlayerEntity player) {
        OnlineServerUser user = this.onlineUsers.get(player.getUuid());
        user.onLeave();
        this.teleportRequestsMap.remove(user.getId());
        if (user.getNickname().isPresent()) {
            this.nicknameToUUID.remove(user.getNickname().get());
        }
        this.usernameToUUID.remove(player.getEntityName());
        this.users.remove(user);

        if (UserUtils.Process.isInAny(user)) {
            UserUtils.Process.remove(user);
        }

        try {
            this.handler.save(user);
        } catch (IOException e) {
            KiloEssentials.getLogger().fatal("Failed to Save User Data [" + player.getEntityName() + "/" + player.getUuidAsString() + "]", e);
        }

        this.onlineUsers.remove(player.getUuid());
        KiloChat.onUserLeave(user);
    }

    public void onChatMessage(OnlineUser user, ChatMessageC2SPacket packet) {
        ServerPlayerEntity player = user.asPlayer();
        NetworkThreadUtils.forceMainThread(packet, player.networkHandler, player.getServerWorld());

        PlayerOnChatMessageEvent event = KiloServer.getServer().triggerEvent(new PlayerOnChatMessageEventImpl(player, packet.getChatMessage()));
        if (event.isCancelled()) {
            if (event.getCancelReason() != null) {
                user.sendError(event.getCancelReason());
            }

            return;
        }


        String string = StringUtils.normalizeSpace(event.getMessage());
        if (punishmentManager.isMuted(user) && !string.startsWith("/")) {
            GameProfile gameProfile = KiloServer.getServer().getMinecraftServer().getUserCache().getByUuid(user.getId());
            user.sendLangError("mute.reason", mutedPlayerList.get(gameProfile).getReason(), TimeDifferenceUtil.formatDateDiff(new Date(), mutedPlayerList.get(gameProfile).getExpiryDate()));
            return;
        }
        player.updateLastActionTime();

        for (int i = 0; i < string.length(); ++i) {
            if (!SharedConstants.isValidChar(string.charAt(i))) {
                if (KiloConfig.main().chat().kickForUsingIllegalCharacters) {
                    player.networkHandler.disconnect(new TranslatableText("multiplayer.disconnect.illegal_characters"));
                } else {
                    player.getCommandSource().sendError(new TranslatableText("multiplayer.disconnect.illegal_characters"));
                }

                return;
            }
        }

        ((OnlineServerUser) user).messageCoolDown += 20;
        if (((ServerUser) user).messageCoolDown > 200 && !user.hasPermission(EssentialPermission.CHAT_BYPASS)) {
            if (KiloConfig.main().chat().kickForSpamming) {
                player.networkHandler.disconnect(new TranslatableText("disconnect.spam"));
            } else {
                if (((ServerUser) user).systemMessageCoolDown > 400) {
                    user.sendMessage(KiloConfig.main().chat().spamWarning);
                }
            }

            return;
        }

        try {
            if (string.startsWith("/")) {
                KiloEssentials.getInstance().getCommandHandler().execute(player.getCommandSource(), string);
            } else {
                ServerChat.send(user, new TextMessage(string), user.getSetting(Settings.CHAT_CHANNEL));
            }
        } catch (Exception e) {
            MutableText text = Texter.newTranslatable("command.failed");
            if (SharedConstants.isDevelopment) {
                text.append("\n").append(Util.getInnermostMessage(e));
                KiloDebugUtils.getLogger().error("Processing a chat message throw an exception", e);
            }

            user.getCommandSource().sendError(text);
        }

    }

    @Override
    public void onTick() {
        for (OnlineUser user : users) {
            if (user == null) {
                continue;
            }

            try {
                ((OnlineServerUser) user).onTick();
            } catch (Exception e) {
                KiloEssentials.getLogger().fatal("DEBUG: ServerUserManager.onTick() -> user.onTick()", e);
            }
        }
    }

    public void onDeath(OnlineUser user) {
        user.saveLocation();

        if (SeatManager.isEnabled() && SeatManager.getInstance().isSitting(user.asPlayer())) {
            SeatManager.getInstance().unseat(user);
        }
    }

    public UserHandler getHandler() {
        return this.handler;
    }

    public void onServerReady() {
        if (KiloConfig.main().autoUserUpgrade) {
            this.handler.upgrade();
        }
    }

    public void appendCachedName(ServerUser user) {
        user.name = user.savedName;
    }

    public static class LoadingText {
        private AnimatedText animatedText;

        public LoadingText(ServerPlayerEntity player) {
            this.animatedText = new AnimatedText(0, 315, TimeUnit.MILLISECONDS, player, TitleS2CPacket.Action.ACTIONBAR)
                    .append(LangText.get(true, "general.wait_server.frame1"))
                    .append(LangText.get(true, "general.wait_server.frame2"))
                    .append(LangText.get(true, "general.wait_server.frame3"))
                    .append(LangText.get(true, "general.wait_server.frame4"))
                    .build();
        }

        public LoadingText(ServerPlayerEntity player, String key) {
            this.animatedText = new AnimatedText(0, 315, TimeUnit.MILLISECONDS, player, TitleS2CPacket.Action.ACTIONBAR)
                    .append(LangText.get(true, key + ".frame1"))
                    .append(LangText.get(true, key + ".frame2"))
                    .append(LangText.get(true, key + ".frame3"))
                    .append(LangText.get(true, key + ".frame4"))
                    .build();
        }

        public LoadingText start() {
            this.animatedText.setStyle(Style.EMPTY.withFormatting(Formatting.YELLOW)).start();
            return this;
        }

        public void stop() {
            this.animatedText.remove();
            this.animatedText = null;
        }
    }

}
