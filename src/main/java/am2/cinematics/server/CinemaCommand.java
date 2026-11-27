package am2.cinematics.server;

import am2.cinematics.AMCinematics;
import java.io.IOException;
import java.util.List;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentText;

/** /cinema play|preview|guardiao|reset|ritual|stop|list (nível de operador 2). */
public class CinemaCommand extends CommandBase {
    @Override
    public String getCommandName() {
        return "cinema";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/cinema play <cena> [raio 1-50] [jogador] | preview <cena> | guardiao <nome> | reset [jogador] "
                + "| ritual treino|dar|reset [jogador] | stop | list";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        if (args.length == 1 && "list".equals(args[0])) {
            reply(sender, "Cenas: " + SceneStore.names());
        } else if ((args.length == 1 || args.length == 2) && "reset".equals(args[0])) {
            EntityPlayerMP player = args.length == 2 ? getPlayer(sender, args[1]) : getCommandSenderAsPlayer(sender);
            FirstBookTrigger.forget(player);
            reply(sender, "Cinemáticas automáticas liberadas de novo para " + player.getCommandSenderName()
                    + " (o gatilho do livro também exige nível mágico 0)");
        } else if ((args.length == 2 || args.length == 3) && "ritual".equals(args[0]) && "treino".equals(args[1])) {
            EntityPlayerMP player = args.length == 3 ? getPlayer(sender, args[2]) : getCommandSenderAsPlayer(sender);
            if (AMCinematics.ritual() == null) throw new CommandException("O ritual novo está desligado ou indisponível");
            try {
                AMCinematics.ritual().practice(player);
            } catch (IllegalArgumentException e) {
                throw new CommandException(e.getMessage());
            }
            reply(sender, "Treino do ritual iniciado em " + player.getCommandSenderName()
                    + " (nada é consumido nem concedido)");
        } else if ((args.length == 2 || args.length == 3) && "ritual".equals(args[0]) && "dar".equals(args[1])) {
            EntityPlayerMP player = args.length == 3 ? getPlayer(sender, args[2]) : getCommandSenderAsPlayer(sender);
            if (AMCinematics.ritual() == null) throw new CommandException("O ritual novo está desligado ou indisponível");
            AMCinematics.ritual().grant(player);
            reply(sender, player.getCommandSenderName() + " recebeu a fusão e a recompensa do ritual novo "
                    + "(desfaça com /cinema ritual reset)");
        } else if ((args.length == 2 || args.length == 3) && "ritual".equals(args[0]) && "reset".equals(args[1])) {
            EntityPlayerMP player = args.length == 3 ? getPlayer(sender, args[2]) : getCommandSenderAsPlayer(sender);
            if (AMCinematics.ritual() == null) throw new CommandException("O ritual novo está desligado ou indisponível");
            AMCinematics.ritual().forget(player);
            reply(sender, player.getCommandSenderName() + " pode fazer o ritual de novo (a fusão atual foi desfeita)");
        } else if (args.length == 2 && "guardiao".equals(args[0])) {
            GuardianVictoryTrigger.Guardian guardian = GuardianVictoryTrigger.Guardian.byId(args[1]);
            if (guardian == null) throw new CommandException("Guardiões: " + guardianIds());
            EntityPlayerMP player = getCommandSenderAsPlayer(sender);
            GuardianVictoryTrigger.play(player, guardian, player.rotationYaw, true);
            reply(sender, "Cena do " + guardian.title + " (só para você; nada é marcado)");
        } else if (args.length == 1 && "stop".equals(args[0])) {
            reply(sender, "Cinemáticas encerradas: " + CinematicServer.INSTANCE.stopAll());
        } else if (args.length >= 2 && args.length <= 4 && "play".equals(args[0])) {
            int radius = args.length >= 3
                    ? parseIntBounded(sender, args[2], 1, CinematicServer.MAX_RADIUS)
                    : CinematicServer.DEFAULT_RADIUS;
            EntityPlayerMP anchor = args.length == 4 ? getPlayer(sender, args[3]) : getCommandSenderAsPlayer(sender);
            start(sender, args[1], anchor, radius, false);
        } else if (args.length == 2 && "preview".equals(args[0])) {
            start(sender, args[1], getCommandSenderAsPlayer(sender), CinematicServer.MAX_RADIUS, true);
        } else {
            throw new WrongUsageException(getCommandUsage(sender));
        }
    }

    private void start(ICommandSender sender, String name, EntityPlayerMP anchor, int radius, boolean preview) {
        try {
            CinematicServer.INSTANCE.playOn(anchor, SceneStore.read(name), radius, preview);
        } catch (IOException e) {
            throw new CommandException(e.getMessage());
        } catch (IllegalArgumentException e) {
            throw new CommandException("Cena " + name + " inválida: " + e.getMessage());
        }
        reply(sender, "Cinemática '" + name + "' iniciada em " + anchor.getCommandSenderName()
                + (preview ? " (só para você)" : " (raio " + radius + ")"));
    }

    private static String guardianIds() {
        StringBuilder ids = new StringBuilder();
        for (GuardianVictoryTrigger.Guardian g : GuardianVictoryTrigger.Guardian.values()) {
            if (ids.length() > 0) ids.append(", ");
            ids.append(g.id);
        }
        return ids.toString();
    }

    private static void reply(ICommandSender sender, String text) {
        sender.addChatMessage(new ChatComponentText(text));
    }

    @Override
    public List addTabCompletionOptions(ICommandSender sender, String[] args) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, "play", "preview", "guardiao", "reset", "ritual", "stop", "list");
        }
        if (args.length == 2 && "guardiao".equals(args[0])) {
            return getListOfStringsMatchingLastWord(args, guardianIds().split(", "));
        }
        if (args.length == 2 && "ritual".equals(args[0])) {
            return getListOfStringsMatchingLastWord(args, "treino", "dar", "reset");
        }
        if (args.length == 2 && ("play".equals(args[0]) || "preview".equals(args[0]))) {
            List<String> names = SceneStore.names();
            return getListOfStringsMatchingLastWord(args, names.toArray(new String[names.size()]));
        }
        return null;
    }
}
