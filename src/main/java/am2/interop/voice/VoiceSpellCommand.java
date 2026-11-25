package am2.interop.voice;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

import java.util.List;
import java.util.Locale;

/**
 * Client-side command {@code /amvoice} to bind, rebind, clear, test and tune voice-triggered spells.
 */
@SideOnly(Side.CLIENT)
public final class VoiceSpellCommand extends CommandBase {

	@Override
	public String getCommandName() {
		return "amvoice";
	}

	@Override
	public String getCommandUsage(ICommandSender sender) {
		return "/amvoice <status|bind <palavra>|rebind <palavra>|list|unbind <palavra|all>|clear|listen [on|off]|threshold <0.42-0.95>|vad <0.003-0.30>|test <palavra>>";
	}

	@Override
	public int getRequiredPermissionLevel() {
		return 0;
	}

	@Override
	public boolean canCommandSenderUseCommand(ICommandSender sender) {
		return true;
	}

	@Override
	public void processCommand(ICommandSender sender, String[] args) {
		VoiceSpellController controller = VoiceSpellController.get();
		if (controller == null) {
			reply(sender, EnumChatFormatting.RED + "Sistema AM2 Voice não foi inicializado.");
			return;
		}
		if (args == null || args.length == 0) {
			reply(sender, EnumChatFormatting.YELLOW + getCommandUsage(sender));
			return;
		}

		String sub = args[0].toLowerCase(Locale.ROOT);
		if ("status".equals(sub)) {
			long ageMs = AkashicVoiceBridge.getLastFrameMillis() > 0L
					? (System.currentTimeMillis() - AkashicVoiceBridge.getLastFrameMillis())
					: -1L;
			reply(sender, EnumChatFormatting.AQUA + "Status: " + EnumChatFormatting.WHITE + AkashicVoiceBridge.getStatusReason()
					+ EnumChatFormatting.GRAY + " | Mudo=" + AkashicVoiceBridge.isMuted()
					+ " | Silenciado=" + AkashicVoiceBridge.isLocalPlayerSilenced()
					+ " | EscutaContínua=" + controller.isContinuousListen());
			reply(sender, EnumChatFormatting.GRAY + String.format(Locale.ROOT,
					"RMS=%.4f | RuídoFundo=%.4f | VAD_Efetivo=%.3f | Threshold=%.2f | ÚltimoFrame=%dms | Palavras=%d",
					AkashicVoiceBridge.getLatestRms(), AkashicVoiceBridge.getNoiseFloorRms(),
					controller.getEffectiveVadThreshold(), controller.getMatchThreshold(),
					ageMs, controller.getMatcher().bindingCount()));
			return;
		}

		if ("bind".equals(sub) || "rebind".equals(sub)) {
			if (args.length < 2) {
				reply(sender, EnumChatFormatting.YELLOW + "Uso: /amvoice " + sub + " <palavra> (segurando uma magia ou Spell Book)");
				return;
			}
			String word = joinArgs(args, 1);
			ItemStack spell = VoiceSpellController.getHeldSpellStack(Minecraft.getMinecraft().thePlayer);
			controller.startRecording(word, spell, "rebind".equals(sub));
			return;
		}

		if ("list".equals(sub)) {
			List<VoiceAcousticMatcher.VoiceBinding> list = controller.getMatcher().listBindings();
			if (list.isEmpty()) {
				reply(sender, EnumChatFormatting.GRAY + "Nenhuma magia vinculada ainda. Segure uma magia e use /amvoice bind <palavra>.");
				return;
			}
			reply(sender, EnumChatFormatting.AQUA + "Encantamentos vocais (" + list.size() + "):");
			for (VoiceAcousticMatcher.VoiceBinding b : list) {
				reply(sender, EnumChatFormatting.WHITE + " - '" + b.word + "' -> " + EnumChatFormatting.AQUA
						+ b.spellDisplayName + EnumChatFormatting.GRAY + " (" + b.samples.size() + "/4 amostras)");
			}
			return;
		}

		if ("clear".equals(sub)) {
			int removed = controller.getMatcher().clearAll();
			reply(sender, EnumChatFormatting.GREEN + "Todos os vínculos vocais foram removidos (" + removed + ").");
			return;
		}

		if ("unbind".equals(sub)) {
			if (args.length < 2) {
				reply(sender, EnumChatFormatting.YELLOW + "Uso: /amvoice unbind <palavra|all>");
				return;
			}
			String word = joinArgs(args, 1);
			if ("all".equalsIgnoreCase(word) || "*".equals(word)) {
				int removed = controller.getMatcher().clearAll();
				reply(sender, EnumChatFormatting.GREEN + "Todos os vínculos vocais foram removidos (" + removed + ").");
				return;
			}
			if (controller.getMatcher().removeBinding(word)) {
				reply(sender, EnumChatFormatting.GREEN + "Vínculo '" + word + "' removido.");
			} else {
				reply(sender, EnumChatFormatting.RED + "Palavra '" + word + "' não encontrada.");
			}
			return;
		}

		if ("listen".equals(sub)) {
			boolean next = args.length >= 2
					? ("on".equalsIgnoreCase(args[1]) || "true".equalsIgnoreCase(args[1]))
					: !controller.isContinuousListen();
			controller.setContinuousListen(next);
			reply(sender, EnumChatFormatting.AQUA + "Escuta contínua: " + (next ? EnumChatFormatting.GREEN + "ATIVADA" : EnumChatFormatting.GRAY + "DESATIVADA (segure a tecla Foco Vocal para conjurar)"));
			return;
		}

		if ("threshold".equals(sub)) {
			if (args.length < 2) {
				reply(sender, EnumChatFormatting.GRAY + String.format(Locale.ROOT, "Threshold atual: %.2f (ex.: /amvoice threshold 0.56)", controller.getMatchThreshold()));
				return;
			}
			try {
				double val = Double.parseDouble(args[1]);
				controller.setMatchThreshold(val);
				reply(sender, EnumChatFormatting.GREEN + String.format(Locale.ROOT, "Threshold ajustado para %.2f", controller.getMatchThreshold()));
			} catch (NumberFormatException e) {
				reply(sender, EnumChatFormatting.RED + "Valor inválido. Use um número entre 0.42 e 0.95.");
			}
			return;
		}

		if ("vad".equals(sub)) {
			if (args.length < 2) {
				reply(sender, EnumChatFormatting.GRAY + String.format(Locale.ROOT, "VAD base=%.3f | efetivo=%.3f", controller.getVadThreshold(), controller.getEffectiveVadThreshold()));
				return;
			}
			try {
				double val = Double.parseDouble(args[1]);
				controller.setVadThreshold(val);
				reply(sender, EnumChatFormatting.GREEN + String.format(Locale.ROOT, "VAD base ajustado para %.3f (efetivo=%.3f)", controller.getVadThreshold(), controller.getEffectiveVadThreshold()));
			} catch (NumberFormatException e) {
				reply(sender, EnumChatFormatting.RED + "Valor inválido. Use um número entre 0.003 e 0.30.");
			}
			return;
		}

		if ("test".equals(sub)) {
			if (args.length < 2) {
				reply(sender, EnumChatFormatting.YELLOW + "Uso: /amvoice test <palavra>");
				return;
			}
			controller.triggerTestCast(joinArgs(args, 1));
			return;
		}

		reply(sender, EnumChatFormatting.YELLOW + getCommandUsage(sender));
	}

	@Override
	public List addTabCompletionOptions(ICommandSender sender, String[] args) {
		if (args.length == 1) {
			return getListOfStringsMatchingLastWord(args, "status", "bind", "rebind", "list", "unbind", "clear", "listen", "threshold", "vad", "test");
		}
		return null;
	}

	private static String joinArgs(String[] args, int from) {
		StringBuilder sb = new StringBuilder();
		for (int i = from; i < args.length; i++) {
			if (i > from) sb.append(' ');
			sb.append(args[i]);
		}
		return sb.toString().trim();
	}

	private static void reply(ICommandSender sender, String msg) {
		sender.addChatMessage(new ChatComponentText(EnumChatFormatting.DARK_AQUA + "[AM2 Voice] " + msg));
	}
}
