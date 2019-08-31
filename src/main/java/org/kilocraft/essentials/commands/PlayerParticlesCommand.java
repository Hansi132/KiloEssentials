package org.kilocraft.essentials.commands;

import org.kilocraft.essentials.Mod;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Style;
import net.minecraft.util.Formatting;

public class PlayerParticlesCommand {

	public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
		dispatcher.register(CommandManager.literal("donatorparticles").executes(context -> {
			context.getSource()
					.sendFeedback(new LiteralText(Mod.lang.getProperty("command.donatorparticles.onlyoneargument"))
							.setStyle(new Style().setColor(Formatting.RED)), false);
			return 1;
		}).then(CommandManager.literal("set").executes(context -> {
			context.getSource()
					.sendFeedback(new LiteralText(Mod.lang.getProperty("command.donatorparticles.noparticleschosen"))
							.setStyle(new Style().setColor(Formatting.RED)), false);
			return 1;
		}).then(CommandManager.argument("name", PlayerParticlesCommandArgument.particles()).executes(context -> {
			// TODO: Change particle
			String particle = PlayerParticlesCommandArgument.getParticleName(context, "name");
			if (PlayerParticlesCommandArgument.NAMES.contains(particle)) {
				context.getSource().sendFeedback(
						new LiteralText(Mod.lang.getProperty("command.donatorparticles.particleset")), false);
				return 0;
			} else {
				context.getSource().sendFeedback(
						new LiteralText(Mod.lang.getProperty("command.donatorparticles.incorrectparticle"))
								.setStyle(new Style().setColor(Formatting.RED)),
						false);
				return 1;
			}
		}))).then(CommandManager.literal("disable").executes(context -> {
			// TODO: Disable particles
			context.getSource().sendFeedback(new LiteralText(Mod.lang.getProperty("command.donatorparticles.disable")),
					false);
			return 0;
		})));
	}

}