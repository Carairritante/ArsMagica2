package am2.cinematics;

import am2.cinematics.network.GameMessage;
import am2.cinematics.network.PlayMessage;
import am2.cinematics.network.RitualReportMessage;
import am2.cinematics.network.StopMessage;

/** Lado servidor: não referencia nenhuma classe do cliente. */
public class CommonProxy {
    public void init() {}

    public void play(PlayMessage message) {}

    public void stop(StopMessage message) {}

    public void report(RitualReportMessage message) {}

    public void game(GameMessage message) {}
}
