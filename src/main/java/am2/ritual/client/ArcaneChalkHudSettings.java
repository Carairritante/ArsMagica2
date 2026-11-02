package am2.ritual.client;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraftforge.common.config.Configuration;

import java.io.File;

/** Local presentation preferences; never sent to the server or stored on the tool. */
@SideOnly(Side.CLIENT)
public final class ArcaneChalkHudSettings {
    private static ArcaneChalkHudSettings instance;
    private final Configuration config;
    private double x, y;
    private int percent;
    private int transparency;

    private ArcaneChalkHudSettings() {
        config = new Configuration(new File(Minecraft.getMinecraft().mcDataDir, "config/AM2/ArcaneChalkHUD.cfg"));
        config.load();
        x = fraction(config.get("hud", "PositionX", 0.0).getDouble(0.0));
        y = fraction(config.get("hud", "PositionY", 0.0).getDouble(0.0));
        percent = Math.max(50, Math.min(150, config.get("hud", "SizePercent", 100).getInt(100)));
        transparency = Math.max(0, Math.min(100, config.get("hud", "BackgroundTransparency", 45).getInt(45)));
    }

    public static ArcaneChalkHudSettings get() {
        if (instance == null) instance = new ArcaneChalkHudSettings();
        return instance;
    }

    private static double fraction(double value) {
        return Double.isNaN(value) || Double.isInfinite(value) ? 0 : Math.max(0, Math.min(1, value));
    }

    public int percent() { return percent; }
    public int transparency() { return transparency; }
    public void fade(int delta) { transparency = Math.max(0, Math.min(100, transparency + delta)); }
    public void resize(int delta) { percent = Math.max(50, Math.min(150, percent + delta)); }
    public void reset() { x = y = 0; percent = 100; transparency = 45; }

    public int left(int screenWidth, int panelWidth) {
        return 9 + (int)Math.round(Math.max(0, screenWidth - panelWidth - 18) * x);
    }

    public int top(int screenHeight, int panelHeight) {
        return 8 + (int)Math.round(Math.max(0, screenHeight - panelHeight - 16) * y);
    }

    public void move(int left, int top, int screenWidth, int screenHeight, int panelWidth, int panelHeight) {
        x = fraction((left - 9.0) / Math.max(1, screenWidth - panelWidth - 18));
        y = fraction((top - 8.0) / Math.max(1, screenHeight - panelHeight - 16));
    }

    public void save() {
        config.get("hud", "PositionX", 0.0).set(x);
        config.get("hud", "PositionY", 0.0).set(y);
        config.get("hud", "SizePercent", 100).set(percent);
        config.get("hud", "BackgroundTransparency", 45).set(transparency);
        config.save();
    }
}
