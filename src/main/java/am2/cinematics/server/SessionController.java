package am2.cinematics.server;

import java.util.UUID;
import net.minecraft.entity.player.EntityPlayerMP;

/**
 * Quem manda numa sessão interativa (o ritual): pode parar a linha do tempo,
 * recebe as teclas e decide o encerramento. Tudo na thread do servidor.
 */
public interface SessionController {
    enum Step { ADVANCE, HOLD, STOP }

    /** Chamado antes de a cena avançar um tick. Não pode criar nem encerrar sessões. */
    Step tick(int age);

    void input(UUID player, int gate, int key, int tick);

    /** O cliente deste jogador saiu da cinemática. */
    void left(UUID player);

    /** Um espectador acabou de receber a cena. */
    void admitted(EntityPlayerMP viewer);

    /** Índice da próxima pausa por resolver. */
    int gate();

    /** Treino: quem joga também pode pular a cena. */
    boolean practice();

    /**
     * A sessão acabou para o controlador; aqui já se pode criar outras.
     *
     * @param completed a cena chegou ao fim
     */
    void closed(boolean completed);
}
