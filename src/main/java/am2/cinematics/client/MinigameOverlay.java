package am2.cinematics.client;

import am2.cinematics.ritual.Minigame;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.settings.GameSettings;
import org.lwjgl.opengl.GL11;

/**
 * Desenho 2D dos três minigames do ritual no estilo do Grimório do AM2.5:
 * cabeçalho e barra inferior contidos entre as barras pretas (12%–88%),
 * área central vazada preservando o personagem, e geometria fiel às regras de {@link Minigame}.
 */
final class MinigameOverlay {
    private static final float[] CANDLE_DX = { 0F, 1.25F, 0F, -1.25F };
    private static final float[] CANDLE_DY = { -1.0F, 0F, 1.0F, 0F };

    private MinigameOverlay() {}

    static void draw(Minecraft mc, MinigameView view, float partial) {
        ScaledResolution res = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        int width = res.getScaledWidth();
        int height = res.getScaledHeight();
        FontRenderer font = mc.fontRenderer;

        // Zona livre entre as barras pretas de 12% em cima e embaixo
        int bar = (int) Math.round(height * 0.12);
        int safeTop = bar;
        int safeBot = height - bar;
        boolean compact = height <= 260;

        double play = view.playTick() + (view.verdict() == 0 ? partial : 0);
        float timeSec = (Minecraft.getSystemTime() % 3600000L) / 1000F;

        Minigame game = view.game;
        String id = game.type == Minigame.Type.CANDLES ? "velas"
                : game.type == Minigame.Type.LIGHTNING ? "raios" : "pulso";
        int accent = game.type == Minigame.Type.CANDLES ? ArcaneHud.GOLD
                : game.type == Minigame.Type.LIGHTNING ? ArcaneHud.CYAN : ArcaneHud.EMERALD;
        String actionKey = GameSettings.getKeyDisplayString(mc.gameSettings.keyBindJump.getKeyCode());

        // Dimensões do cabeçalho e rodapé adaptadas inclusive para altura 240
        float headH = compact ? 25F : 32F;
        float headW = Math.min(width - 16F, compact ? 276F : 340F);
        float headX = (width - headW) * 0.5F;
        float headY = safeTop + (compact ? 2F : 5F);

        float footH = compact ? 24F : 30F;
        float footW = Math.min(width - 20F, compact ? 246F : 296F);
        float footX = (width - footW) * 0.5F;
        float footY = safeBot - footH - (compact ? 2F : 5F);

        float midTop = headY + headH + 3F;
        float midBot = footY - 3F;
        float cx = width * 0.5F;
        float cy = (midTop + midBot) * 0.5F;
        float r = Math.min((midBot - midTop) * 0.31F, height * 0.135F);

        mc.entityRenderer.setupOverlayRendering();
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushMatrix();
        try {
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_CULL_FACE);

            // Sombra radial sutil apenas atrás dos glifos centrais (mantém o personagem visível)
            ArcaneHud.disc(cx, cy, r * 2.05F, 0x070A18, 0.36F, 0F);

            // 1. Minigame central
            if (game.type == Minigame.Type.CANDLES) {
                candles(mc, font, view, cx, cy, r, compact, timeSec);
            } else if (game.type == Minigame.Type.LIGHTNING) {
                lightning(font, view, cx, cy, r, play, actionKey, compact, timeSec);
            } else {
                pulse(font, view, cx, cy, r, play, actionKey, compact, timeSec);
            }

            // 2. Cabeçalho do Grimório (título + instrução / espectador)
            drawHeader(mc, font, id, accent, actionKey, view, width, headX, headY, headW, headH, compact);

            // 3. Rodapé (Observe / Prepare-se / Contador 5s / Conseguiu! / Falhou)
            drawFooter(mc, font, view, play, accent, width, footX, footY, footW, footH, compact, timeSec);
        } finally {
            GL11.glPopMatrix();
            GL11.glPopAttrib();
            GL11.glShadeModel(GL11.GL_FLAT);
            GL11.glColor4f(1F, 1F, 1F, 1F);
        }
    }

    private static void drawHeader(Minecraft mc, FontRenderer font, String id, int accent, String actionKey,
                                   MinigameView view, int screenW, float x, float y, float w, float h, boolean compact) {
        ArcaneHud.grimoireBanner(x, y, w, h, view.mine ? accent : ArcaneHud.BORDER, 0.82F);

        String title = I18n.format("amcinematics.game." + id + ".nome");
        float titleScale = compact ? 1.15F : 1.45F;
        int maxTitleW = (int) (w - 42F);
        int rawTitleW = font.getStringWidth(title);
        float fitTitle = rawTitleW > 0 ? Math.min(titleScale, maxTitleW / (float) rawTitleW) : titleScale;
        float halfTitleW = rawTitleW * fitTitle * 0.5F;
        float titleY = y + (compact ? 3.5F : 5F);
        float runeY = titleY + 4F * fitTitle;
        float runeSize = compact ? 7.5F : 9.5F;

        ArcaneHud.rune(mc, 1, screenW * 0.5F - halfTitleW - 10F, runeY, runeSize, accent, 0.88F, true);
        ArcaneHud.rune(mc, 4, screenW * 0.5F + halfTitleW + 10F, runeY, runeSize, accent, 0.88F, true);
        ArcaneHud.textCentered(font, title, screenW * 0.5F, titleY, titleScale, maxTitleW, ArcaneHud.GOLD_HI);

        String hint = view.mine
                ? I18n.format("amcinematics.game." + id + ".dica", actionKey)
                : I18n.format("amcinematics.game.assistindo", view.caster);
        float hintScale = compact ? 0.78F : 0.90F;
        float hintY = y + h - (compact ? 9.5F : 11.5F);
        ArcaneHud.textCentered(font, hint, screenW * 0.5F, hintY, hintScale, (int) (w - 14F),
                view.mine ? ArcaneHud.TEXT : ArcaneHud.CYAN);
    }

    private static void drawFooter(Minecraft mc, FontRenderer font, MinigameView view, double play, int accent,
                                   int screenW, float x, float y, float w, float h, boolean compact, float timeSec) {
        int verdict = view.verdict();
        float midX = screenW * 0.5F;

        if (verdict != 0) {
            boolean passed = verdict > 0;
            int col = passed ? ArcaneHud.GOLD_HI : ArcaneHud.CRIMSON_HI;
            ArcaneHud.grimoireBanner(x, y, w, h, col, 0.90F);
            ArcaneHud.glow(midX, y + h * 0.5F, w * 0.34F, col, 0.22F);

            float starSize = compact ? 4.2F : 5.2F;
            ArcaneHud.star(x + 15F, y + h * 0.5F, starSize, col, 0.92F, timeSec * 1.4F);
            ArcaneHud.star(x + w - 15F, y + h * 0.5F, starSize, col, 0.92F, -timeSec * 1.4F);
            if (!passed) {
                float sealR = compact ? 4.2F : 5.2F;
                ArcaneHud.waxSeal(x + 30F, y + h * 0.5F, sealR, timeSec);
                ArcaneHud.waxSeal(x + w - 30F, y + h * 0.5F, sealR, timeSec);
            }

            String label = I18n.format(passed ? "amcinematics.game.passou" : "amcinematics.game.falhou");
            float scale = compact ? 1.35F : 1.65F;
            ArcaneHud.textCentered(font, label, midX, y + (h - 8F * scale) * 0.5F + 0.5F, scale,
                    (int) (w - 44F), passed ? ArcaneHud.GOLD_HI : ArcaneHud.CRIMSON_HI);
            return;
        }

        if (play < 0) {
            ArcaneHud.grimoireBanner(x, y, w, h, accent, 0.84F);
            String prep = I18n.format(view.game.type == Minigame.Type.CANDLES
                    ? "amcinematics.game.observe" : "amcinematics.game.prepare");
            float scale = compact ? 1.05F : 1.25F;
            ArcaneHud.textCentered(font, prep, midX, y + (compact ? 4F : 5F), scale, (int) (w - 20F), ArcaneHud.GOLD_HI);
            ArcaneHud.runeStrip(mc, x + 22F, y + h - (compact ? 5.5F : 7F), w - 44F, compact ? 7 : 9,
                    accent, 0.38F, timeSec);
            return;
        }

        // Contador de 5 segundos com alerta carmim nos últimos 1,5s (< 30 ticks)
        double left = Math.max(0.0, Minigame.PLAY_TICKS - play);
        float frac = (float) Math.max(0.0, Math.min(1.0, left / Minigame.PLAY_TICKS));
        boolean urgent = left < 30.0;
        float pulse = urgent ? 0.65F + 0.35F * (float) Math.sin(timeSec * 14F) : 1F;
        int barAccent = urgent ? ArcaneHud.CRIMSON_HI : accent;

        ArcaneHud.grimoireBanner(x, y, w, h, barAccent, 0.84F);
        if (urgent) {
            ArcaneHud.glow(midX, y + h * 0.5F, w * 0.38F, ArcaneHud.CRIMSON_HI, 0.22F * pulse);
        }

        String timeText = String.format(Locale.ROOT, "%.1fs", left / 20.0);
        float timeScale = compact ? 1.15F : 1.45F;
        ArcaneHud.textCentered(font, timeText, midX, y + (compact ? 3.5F : 4.5F), timeScale,
                (int) (w - 20F), urgent ? ArcaneHud.CRIMSON_HI : ArcaneHud.GOLD_HI);

        float bx = x + 14F;
        float bw = w - 28F;
        float bh = compact ? 4F : 5F;
        float by = y + h - bh - (compact ? 4F : 5F);

        // Calha e borda da barra de tempo
        ArcaneHud.rect(bx - 1F, by - 1F, bw + 2F, bh + 2F, urgent ? ArcaneHud.CRIMSON_HI : ArcaneHud.BORDER, 0.90F);
        ArcaneHud.rect(bx, by, bw, bh, ArcaneHud.BG, 0.96F);

        float fillW = bw * frac;
        if (fillW > 0.5F) {
            int leftCol = urgent ? ArcaneHud.CRIMSON : ArcaneHud.GOLD_MID;
            int rightCol = urgent ? ArcaneHud.CRIMSON_HI : barAccent;
            ArcaneHud.hGrad(bx, by, fillW, bh, leftCol, 0.95F, rightCol, 0.98F, false);
            ArcaneHud.hGrad(bx, by, fillW, 1F, 0xFFFFFF, 0.25F, 0xFFFFFF, 0.48F, true);
            if (fillW > 2F) {
                ArcaneHud.glow(bx + fillW, by + bh * 0.5F, 7F, urgent ? ArcaneHud.CRIMSON_HI : ArcaneHud.GOLD_HI, 0.72F * pulse);
                ArcaneHud.star(bx + fillW, by + bh * 0.5F, 2.6F, ArcaneHud.TEXT, 0.95F, timeSec * 4F);
            }
        }
    }

    // ------------------------------------------------------------------ 1. VELAS

    private static void candles(Minecraft mc, FontRenderer font, MinigameView view,
                                float cx, float cy, float r, boolean compact, float timeSec) {
        int flash = view.flash();
        boolean errFlash = flash < 0;

        // Astrolábio vazado conectando as 4 velas (preserva o personagem no centro)
        ArcaneHud.ring(cx, cy, r * 1.10F, 1.0F, ArcaneHud.BORDER, 0.46F, 0F, 1, 1F, false);
        ArcaneHud.tickRing(cx, cy, r * 1.10F, compact ? 2.2F : 3.0F, 24, ArcaneHud.GOLD_MID, 0.42F, timeSec * 0.14F);
        ArcaneHud.polygon(cx, cy, r * 1.10F, 4, 1.0F, errFlash ? ArcaneHud.CRIMSON_HI : ArcaneHud.GOLD_MID,
                errFlash ? 0.78F : 0.35F, 0F, true);

        int lit = view.lit();
        float medR = r * 0.37F;
        float keyScale = compact ? 0.78F : 0.92F;

        for (int i = 0; i < 4; i++) {
            float x = cx + CANDLE_DX[i] * r;
            float y = cy + CANDLE_DY[i] * r;
            boolean on = (lit == i);
            boolean fail = on && errFlash;

            if (on) {
                ArcaneHud.glow(x, y, medR * 2.35F, fail ? ArcaneHud.CRIMSON_HI : ArcaneHud.AMBER, 0.48F);
                ArcaneHud.ring(x, y, medR * 1.28F, 1.3F, fail ? ArcaneHud.CRIMSON_HI : ArcaneHud.GOLD_HI,
                        0.92F, timeSec * 1.8F, 6, 0.55F, true);
            }
            ArcaneHud.disc(x, y, medR, fail ? 0x360C18 : on ? 0x33240E : ArcaneHud.PANEL, 0.90F, 0.84F);
            ArcaneHud.ring(x, y, medR, on ? 1.5F : 1.05F,
                    fail ? ArcaneHud.CRIMSON_HI : on ? ArcaneHud.GOLD_HI : ArcaneHud.BORDER,
                    on ? 0.98F : 0.85F, 0F, 1, 1F, on);

            ArcaneHud.flame(x, y - medR * 0.66F, medR * 0.52F, on, fail, timeSec);

            String keyName = GameSettings.getKeyDisplayString(moveKeyCode(mc, i));
            ArcaneHud.arcaneKeycap(font, x, y + medR * 0.18F, medR * 1.22F, Math.max(10F, medR * 0.78F),
                    keyName, ArcaneHud.GOLD_MID, on, fail, keyScale);
        }

        // Progresso 0..3 no centro da cruz
        int done = view.game.progress();
        int total = view.game.count();
        float ms = Math.max(3.6F, r * 0.135F);
        for (int i = 0; i < total; i++) {
            float mx = cx + (i - (total - 1) * 0.5F) * ms * 3.1F;
            int status = (errFlash && i == 0) ? 2 : (i < done ? 1 : 0);
            ArcaneHud.progressMark(mx, cy - ms * 0.4F, ms, status, ArcaneHud.GOLD_HI, timeSec);
        }
        ArcaneHud.textCentered(font, done + "/" + total, cx, cy + ms * 1.3F, compact ? 0.75F : 0.85F,
                (int) (r * 0.9F), errFlash ? ArcaneHud.CRIMSON_HI : ArcaneHud.GOLD);
    }

    private static int moveKeyCode(Minecraft mc, int index) {
        if (index == 0) return mc.gameSettings.keyBindForward.getKeyCode();
        if (index == 1) return mc.gameSettings.keyBindRight.getKeyCode();
        if (index == 2) return mc.gameSettings.keyBindBack.getKeyCode();
        return mc.gameSettings.keyBindLeft.getKeyCode();
    }

    // ------------------------------------------------------------------ 2. RAIOS

    private static void lightning(FontRenderer font, MinigameView view,
                                  float cx, float cy, float r, double play,
                                  String actionKey, boolean compact, float timeSec) {
        Minigame game = view.game;
        float target = r * 0.62F;
        float outerR = r * 1.48F;
        double speed = (outerR - target) / 30.0;
        float bandW = (float) (speed * game.window() * 2.0);
        int flash = view.flash();

        boolean inWindow = false;
        for (int i = 0; i < game.count(); i++) {
            if (game.judged(i) == Minigame.PENDING && Math.abs(game.note(i) - play) <= game.window()) {
                inWindow = true;
                break;
            }
        }

        // Moldura de astrolábio e faixa de folga exata (game.window())
        ArcaneHud.ring(cx, cy, outerR, 0.9F, ArcaneHud.BORDER, 0.34F, 0F, 1, 1F, false);
        ArcaneHud.tickRing(cx, cy, target + bandW * 0.54F, compact ? 2.2F : 3.0F, 32, ArcaneHud.CYAN, 0.48F, -timeSec * 0.25F);

        int bandCol = flash < 0 ? ArcaneHud.CRIMSON : inWindow ? ArcaneHud.CYAN : 0x2B4C7E;
        float bandAlpha = flash < 0 ? 0.44F : inWindow ? 0.34F : 0.24F;
        ArcaneHud.ring(cx, cy, target, bandW, bandCol, bandAlpha, 0F, 1, 1F, true);
        ArcaneHud.ring(cx, cy, Math.max(2F, target - bandW * 0.5F), 0.9F, ArcaneHud.CYAN, 0.45F, timeSec * 0.4F, 12, 0.45F, true);
        ArcaneHud.ring(cx, cy, target + bandW * 0.5F, 0.9F, ArcaneHud.CYAN, 0.45F, -timeSec * 0.4F, 12, 0.45F, true);

        // Círculo-alvo + 4 estrelas cardeais
        int targetCol = flash > 0 ? ArcaneHud.EMERALD : flash < 0 ? ArcaneHud.CRIMSON_HI
                : inWindow ? ArcaneHud.GOLD_HI : ArcaneHud.TEXT;
        ArcaneHud.ring(cx, cy, target, 2.0F, targetCol, 0.95F, 0F, 1, 1F, true);
        float starR = compact ? 2.4F : 3.2F;
        for (int i = 0; i < 4; i++) {
            double ang = i * Math.PI * 0.5;
            ArcaneHud.star(cx + (float) Math.cos(ang) * target, cy + (float) Math.sin(ang) * target,
                    starR, inWindow ? ArcaneHud.GOLD_HI : ArcaneHud.CYAN, 0.90F, timeSec);
        }
        if (flash > 0) {
            ArcaneHud.glow(cx, cy, r * 1.15F, ArcaneHud.CYAN, 0.38F);
        }

        // Anéis fechando fiéis a game.note(i) e playTick
        for (int i = 0; i < game.count(); i++) {
            double wait = game.note(i) - play;
            if (game.judged(i) != Minigame.PENDING || wait > 30.0 || wait < -game.window()) continue;
            float fade = (float) Math.max(0.0, Math.min(1.0, (30.0 - wait) / 8.0));
            float ringR = (float) Math.max(3.0, target + wait * speed);
            boolean hitZone = Math.abs(wait) <= game.window();
            int ringCol = hitZone ? ArcaneHud.GOLD_HI : 0xA9D0FF;
            ArcaneHud.ring(cx, cy, ringR, 2.8F, ringCol, fade * 0.96F, 0F, 1, 1F, true);
            ArcaneHud.ring(cx, cy, ringR + 3.2F, 1.0F, ArcaneHud.TEXT, fade * 0.48F, timeSec * 2.2F, 8, 0.36F, true);
        }

        // 3 marcas de acerto/erro acima da zona-alvo
        float ms = Math.max(3.8F, r * 0.145F);
        float marksY = cy - target - bandW * 0.5F - ms * 2.1F;
        drawResults(game, cx, marksY, ms, ArcaneHud.CYAN, timeSec);

        // Tecla de pular abaixo do alvo (para quem joga)
        if (view.mine) {
            float btnH = compact ? 12F : 14F;
            float btnY = cy + target + bandW * 0.5F + btnH * 0.85F;
            ArcaneHud.arcaneKeycap(font, cx, btnY, compact ? 42F : 50F, btnH, actionKey,
                    ArcaneHud.CYAN, inWindow || flash > 0, flash < 0, compact ? 0.78F : 0.88F);
        }
    }

    // ------------------------------------------------------------------ 3. PULSO DO MUNDO

    private static void pulse(FontRenderer font, MinigameView view,
                              float cx, float cy, float r, double play,
                              String actionKey, boolean compact, float timeSec) {
        Minigame game = view.game;
        float heartX = cx - r * 1.22F;
        float trackLen = r * 2.56F;
        double speed = trackLen / 40.0;
        float winHalf = (float) (game.window() * speed);

        double swell = 1.0;
        boolean inWindow = false;
        for (int i = 0; i < game.count(); i++) {
            double dist = Math.abs(play - game.note(i));
            swell += 0.28 * Math.max(0.0, 1.0 - dist / 4.0);
            if (game.judged(i) == Minigame.PENDING && dist <= game.window()) {
                inWindow = true;
            }
        }
        int flash = view.flash();

        // Linha ley com gradiente e marcações rítmicas
        ArcaneHud.hGrad(heartX, cy - 1.3F, trackLen * 0.75F, 2.6F, ArcaneHud.EMERALD, 0.92F, ArcaneHud.CYAN, 0.52F, true);
        ArcaneHud.hGrad(heartX + trackLen * 0.75F, cy - 1.3F, trackLen * 0.25F, 2.6F, ArcaneHud.CYAN, 0.52F, ArcaneHud.CYAN, 0.06F, true);
        for (int s = 1; s <= 8; s++) {
            float tx = heartX + trackLen * (s / 8F);
            ArcaneHud.rect(tx, cy - 3.5F, 0.9F, 7F, ArcaneHud.CYAN, 0.24F);
        }

        // Colchete de folga exato em volta do coração (± game.window() * speed)
        float bracketH = r * 0.84F;
        int winCol = flash < 0 ? ArcaneHud.CRIMSON : inWindow ? ArcaneHud.EMERALD : ArcaneHud.CYAN;
        ArcaneHud.rect(heartX - winHalf, cy - bracketH * 0.5F, winHalf * 2F, bracketH, winCol, inWindow ? 0.24F : 0.13F);
        ArcaneHud.rect(heartX - winHalf, cy - bracketH * 0.5F, 1F, bracketH, flash < 0 ? ArcaneHud.CRIMSON_HI : ArcaneHud.EMERALD, 0.65F);
        ArcaneHud.rect(heartX + winHalf - 1F, cy - bracketH * 0.5F, 1F, bracketH, flash < 0 ? ArcaneHud.CRIMSON_HI : ArcaneHud.EMERALD, 0.65F);
        ArcaneHud.rect(heartX - winHalf, cy - bracketH * 0.5F, winHalf * 2F, 1F, ArcaneHud.EMERALD, 0.55F);
        ArcaneHud.rect(heartX - winHalf, cy + bracketH * 0.5F - 1F, winHalf * 2F, 1F, ArcaneHud.EMERALD, 0.55F);

        // Coração arcano com mandala pulsando com swell
        float hs = (float) (r * 0.48 * swell);
        int haloCol = flash > 0 || inWindow ? ArcaneHud.EMERALD : ArcaneHud.CRIMSON_HI;
        ArcaneHud.glow(heartX, cy, hs * 1.95F, haloCol, 0.36F);
        ArcaneHud.ring(heartX, cy, hs * 1.30F, 1.1F, ArcaneHud.GOLD_MID, 0.68F, timeSec * 0.7F, 8, 0.55F, true);
        ArcaneHud.polygon(heartX, cy, hs * 1.10F, 6, 0.9F, ArcaneHud.EMERALD, 0.36F, -timeSec * 0.4F, true);

        int heartCore = flash > 0 ? ArcaneHud.EMERALD : flash < 0 ? 0x6E1624 : (inWindow ? ArcaneHud.GOLD_HI : ArcaneHud.CRIMSON_HI);
        int heartEdge = flash > 0 ? 0x1FA67A : flash < 0 ? 0x420C14 : ArcaneHud.CRIMSON;
        ArcaneHud.heart(heartX, cy, hs, heartCore, heartEdge, 0.94F);
        ArcaneHud.ring(heartX, cy, r * 0.19F, 1.5F, ArcaneHud.GOLD_HI, 0.92F, 0F, 1, 1F, true);

        // Batidas viajando fiéis a heartX + wait * speed
        float tailLen = r * 0.42F;
        for (int i = 0; i < game.count(); i++) {
            double wait = game.note(i) - play;
            if (game.judged(i) != Minigame.PENDING || wait > 40.0 || wait < -game.window()) continue;
            float bx = (float) (heartX + wait * speed);
            boolean near = Math.abs(wait) <= game.window();
            int orbCol = near ? ArcaneHud.GOLD_HI : ArcaneHud.EMERALD;
            ArcaneHud.glow(bx, cy, r * 0.36F, orbCol, 0.52F);
            ArcaneHud.hGrad(bx, cy - 1.8F, tailLen, 3.6F, ArcaneHud.EMERALD, 0.72F, ArcaneHud.EMERALD, 0F, true);
            ArcaneHud.ring(bx, cy, r * 0.155F, 1.5F, orbCol, 0.96F, 0F, 1, 1F, true);
            ArcaneHud.star(bx, cy, r * 0.175F, ArcaneHud.TEXT, 0.96F, timeSec * 3.2F);
        }

        // 4 marcas acima da trilha (precisa de 3)
        float ms = Math.max(3.6F, r * 0.14F);
        drawResults(game, cx, cy - r * 0.86F, ms, ArcaneHud.EMERALD, timeSec);

        // Tecla de pular abaixo da trilha (para quem joga)
        if (view.mine) {
            float btnH = compact ? 12F : 14F;
            ArcaneHud.arcaneKeycap(font, cx + r * 0.08F, cy + r * 0.78F, compact ? 42F : 50F, btnH, actionKey,
                    ArcaneHud.EMERALD, inWindow || flash > 0, flash < 0, compact ? 0.78F : 0.88F);
        }
    }

    private static void drawResults(Minigame game, float cx, float cy, float size, int accentRgb, float timeSec) {
        int count = game.count();
        for (int i = 0; i < count; i++) {
            int judged = game.judged(i);
            int status = judged == Minigame.HIT ? 1 : judged == Minigame.MISS ? 2 : 0;
            float mx = cx + (i - (count - 1) * 0.5F) * size * 3.2F;
            ArcaneHud.progressMark(mx, cy, size, status, accentRgb, timeSec);
        }
    }
}
