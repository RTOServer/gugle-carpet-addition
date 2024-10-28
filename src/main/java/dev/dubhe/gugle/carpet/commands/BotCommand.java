package dev.dubhe.gugle.carpet.commands;

import carpet.fakes.ServerPlayerInterface;
import carpet.patches.EntityPlayerMPFake;
import carpet.utils.CommandHelper;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.dubhe.gugle.carpet.GcaExtension;
import dev.dubhe.gugle.carpet.GcaSetting;
import dev.dubhe.gugle.carpet.tools.FakePlayerSerializer;
import dev.dubhe.gugle.carpet.tools.FilesUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

public class BotCommand {
    public static final FilesUtil<String, BotInfo> BOT_INFO = new FilesUtil<>("bot", Object::toString, BotInfo.class);

    public static void register(@NotNull CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("bot")
            .requires(stack -> CommandHelper.canUseCommand(stack, GcaSetting.commandBot))
            .then(
                Commands.literal("list").executes(BotCommand::listBot)
                    .then(
                        Commands.argument("page", IntegerArgumentType.integer(1))
                            .executes(BotCommand::listBot)
                    )
            )
            .then(
                Commands.literal("add")
                    .then(
                        Commands.argument("player", EntityArgument.player())
                            .then(
                                Commands.argument("desc", StringArgumentType.greedyString())
                                    .executes(BotCommand::addBot)
                            )
                    )
            )
            .then(
                Commands.literal("load")
                    .then(
                        Commands.argument("player", StringArgumentType.string())
                            .suggests(BotCommand::suggestPlayer)
                            .executes(BotCommand::loadBot)
                    )
            )
            .then(
                Commands.literal("remove")
                    .then(
                        Commands.argument("player", StringArgumentType.string())
                            .suggests(BotCommand::suggestPlayer)
                            .executes(BotCommand::removeBot)
                    )
            )
        );
    }

    private static int listBot(CommandContext<CommandSourceStack> context) {
        BOT_INFO.init(context);
        int page;
        try {
            page = IntegerArgumentType.getInteger(context, "page");
        } catch (IllegalArgumentException ignored) {
            page = 1;
        }
        final int pageSize = 8;
        int size = BOT_INFO.map.size();
        int maxPage = size / pageSize + 1;
        BotInfo[] botInfos = BOT_INFO.map.values().toArray(new BotInfo[0]);
        context.getSource().sendSystemMessage(
            Component.literal("======= Bot List (Page %s/%s) =======".formatted(page, maxPage))
                .withStyle(ChatFormatting.YELLOW)
        );
        for (int i = (page - 1) * pageSize; i < size && i < page * pageSize; i++) {
            context.getSource().sendSystemMessage(botToComponent(botInfos[i]));
        }
        Component prevPage = page <= 1 ?
            Component.literal("<<<").withStyle(ChatFormatting.GRAY) :
            Component.literal("<<<").withStyle(
                Style.EMPTY
                    .applyFormat(ChatFormatting.GREEN)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/bot list " + (page - 1)))
            );
        Component nextPage = page >= maxPage ?
            Component.literal(">>>").withStyle(ChatFormatting.GRAY) :
            Component.literal(">>>").withStyle(
                Style.EMPTY
                    .applyFormat(ChatFormatting.GREEN)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/bot list " + (page + 1)))
            );
        context.getSource().sendSystemMessage(
            Component.literal("=======")
                .withStyle(ChatFormatting.YELLOW)
                .append(" ")
                .append(prevPage)
                .append(" ")
                .append(Component.literal("(Page %s/%s)".formatted(page, maxPage)).withStyle(ChatFormatting.YELLOW))
                .append(" ")
                .append(nextPage)
                .append(" ")
                .append(Component.literal("=======").withStyle(ChatFormatting.YELLOW))
        );
        return 1;
    }

    private static MutableComponent botToComponent(BotInfo botInfo) {
        MutableComponent component = Component.literal(botInfo.desc).withStyle(
            Style.EMPTY
                .applyFormat(ChatFormatting.GRAY)
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(botInfo.name)))
        );
        MutableComponent load = Component.literal("[↑]").withStyle(
            Style.EMPTY
                .applyFormat(ChatFormatting.GREEN)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/bot load %s".formatted(botInfo.name)))
        );
        MutableComponent remove = Component.literal("[↓]").withStyle(
            Style.EMPTY
                .applyFormat(ChatFormatting.RED)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/player %s kill".formatted(botInfo.name)))
        );
        MutableComponent delete = Component.literal("[\uD83D\uDDD1]").withStyle(
            Style.EMPTY
                .applyFormat(ChatFormatting.RED)
                .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/bot remove %s".formatted(botInfo.name)))
        );
        return Component.literal("▶ ").append(component)
            .append(" ").append(load)
            .append(" ").append(remove)
            .append(" ").append(delete);
    }

    private static int loadBot(CommandContext<CommandSourceStack> context) {
        BOT_INFO.init(context);
        CommandSourceStack source = context.getSource();
        String name = StringArgumentType.getString(context, "player");
        if (FilesUtil.server.getPlayerList().getPlayerByName(name) != null) {
            source.sendFailure(Component.literal("player %s is already exist.".formatted(name)));
            return 0;
        }
        BotInfo botInfo = BOT_INFO.map.getOrDefault(name, null);
        if (botInfo == null) {
            source.sendFailure(Component.literal("%s is not exist."));
            return 0;
        }
        boolean success = EntityPlayerMPFake.createFake(
            name,
            FilesUtil.server,
            botInfo.pos,
            botInfo.facing.y,
            botInfo.facing.x,
            botInfo.dimType,
            botInfo.mode,
            botInfo.flying
        );
        if (success) {
            if (botInfo.actions != null) {
                GcaExtension.ON_PLAYER_LOGGED_IN.put(
                    name,
                    (player) -> FakePlayerSerializer.applyActionPackFromJson(botInfo.actions, player)
                );
            }
            source.sendSuccess(() -> Component.literal("%s is loaded.".formatted(name)), false);
            return 1;
        } else {
            source.sendFailure(Component.literal("%s is not loaded.".formatted(name)));
            return 0;
        }
    }

    private static int addBot(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        BOT_INFO.init(context);
        CommandSourceStack source = context.getSource();
        ServerPlayer p;
        if (!((p = EntityArgument.getPlayer(context, "player")) instanceof EntityPlayerMPFake player)) {
            source.sendFailure(Component.literal("%s is not a fake player.".formatted(p.getGameProfile().getName())));
            return 0;
        }
        String name = player.getGameProfile().getName();
        if (BOT_INFO.map.containsKey(name)) {
            source.sendFailure(Component.literal("%s is already save.".formatted(name)));
            return 0;
        }
        BotCommand.BOT_INFO.map.put(
            name,
            new BotInfo(
                name,
                StringArgumentType.getString(context, "desc"),
                player.position(),
                player.getRotationVector(),
                player.level().dimension(),
                player.gameMode.getGameModeForPlayer(),
                player.getAbilities().flying,
                FakePlayerSerializer.actionPackToJson(((ServerPlayerInterface) player).getActionPack())
            )
        );
        BOT_INFO.save();
        source.sendSuccess(() -> Component.literal("%s is added.".formatted(name)), false);
        return 1;
    }

    private static int removeBot(CommandContext<CommandSourceStack> context) {
        BOT_INFO.init(context);
        String name = StringArgumentType.getString(context, "player");
        BotInfo remove = BotCommand.BOT_INFO.map.remove(name);
        if (remove == null) {
            context.getSource().sendFailure(Component.literal("Bot %s is not exist.".formatted(name)));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal("%s is removed.".formatted(name)), false);
        BOT_INFO.save();
        return 1;
    }

    private static @NotNull CompletableFuture<Suggestions> suggestPlayer(final CommandContext<CommandSourceStack> context, final SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(BOT_INFO.map.keySet(), builder);
    }

    public record BotInfo(
        String name,
        String desc,
        Vec3 pos,
        Vec2 facing,
        @SerializedName("dim_type") ResourceKey<Level> dimType,
        GameType mode,
        boolean flying,
        JsonObject actions
    ) {
    }
}
