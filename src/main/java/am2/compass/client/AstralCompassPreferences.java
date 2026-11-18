package am2.compass.client;

import am2.compass.StructureTargetInfo;
import am2.items.ItemAstralCompass;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.util.StatCollector;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Client presentation and personal bookmarks. Never grants a server target or teleports. */
@SideOnly(Side.CLIENT)
public final class AstralCompassPreferences {
    private static AstralCompassPreferences instance;
    private final Properties data = new Properties();
    private final Set<String> favorites = new LinkedHashSet<String>();
    private final File file;
    private String lastRecorded = "";
    private java.lang.ref.WeakReference<Object> scopeWorld = new java.lang.ref.WeakReference<Object>(null);
    private String cachedScope;
    public double x, y;
    public int size = 100, transparency = 45;
    public static final Map<Integer,String> dimensionNames = new java.util.concurrent.ConcurrentHashMap<Integer,String>();

    private AstralCompassPreferences() {
        file = new File(Minecraft.getMinecraft().mcDataDir, "config/AM2/AstralCompass.properties");
        if (file.isFile()) try (InputStream in = new FileInputStream(file)) { data.load(in); }
        catch (IOException e) { am2.LogHelper.warn("Astral Compass preferences: %s", e.toString()); }
        x = number("hud.x", 0, 0, 1); y = number("hud.y", 0, 0, 1);
        size = (int) number("hud.size", 100, 50, 150);
        transparency = (int) number("hud.transparency", 45, 0, 100);
        for (String id : data.getProperty("favorites", "").split(","))
            if (favorites.size() < 256 && am2.compass.AstralStructureLocator.validId(id)) favorites.add(id);
    }
    public static AstralCompassPreferences get() { if (instance == null) instance = new AstralCompassPreferences(); return instance; }
    private double number(String key, double fallback, double min, double max) {
        try { double n = Double.parseDouble(data.getProperty(key, "")); return Double.isNaN(n) || Double.isInfinite(n) ? fallback : Math.max(min, Math.min(max, n)); }
        catch (NumberFormatException e) { return fallback; }
    }
    public boolean favorite(String id) { return favorites.contains(id); }
    public void toggle(String id) { if(!am2.compass.AstralStructureLocator.validId(id))return; if (!favorites.remove(id) && favorites.size() < 256) favorites.add(id); save(); }
    public void save() {
        data.setProperty("favorites", String.join(",", favorites));
        data.setProperty("hud.x", Double.toString(x)); data.setProperty("hud.y", Double.toString(y));
        data.setProperty("hud.size", Integer.toString(size)); data.setProperty("hud.transparency", Integer.toString(transparency));
        try {
            Files.createDirectories(file.toPath().getParent());
            Path temp = file.toPath().resolveSibling(file.getName() + ".tmp");
            try (OutputStream out = Files.newOutputStream(temp)) { data.store(out, "AM2 Astral Compass - client preferences"); }
            Files.move(temp, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) { am2.LogHelper.warn("Astral Compass preferences: %s", e.toString()); }
    }
    public static String dimensionName(int dim) {
        String key = dim == 0 ? "overworld" : dim == -1 ? "nether" : dim == 1 ? "end" : "";
        String name = key.isEmpty() ? dimensionNames.get(dim) : StatCollector.translateToLocal("am2.astral_compass.dim." + key);
        Minecraft mc = Minecraft.getMinecraft();
        if (name == null && mc.theWorld != null && mc.theWorld.provider.dimensionId == dim) name = mc.theWorld.provider.getDimensionName();
        return name == null || name.isEmpty() ? StatCollector.translateToLocalFormatted("am2.astral_compass.gui.dimension_id", dim) : name + " (" + dim + ")";
    }
    private String scope() {
        Minecraft mc = Minecraft.getMinecraft();
        if (scopeWorld.get() == mc.theWorld && cachedScope != null) return cachedScope;
        String world;
        if (mc.getIntegratedServer() != null) world = "local:" + mc.getIntegratedServer().getFolderName();
        else { ServerData server = mc.func_147104_D(); world = "server:" + (server == null ? "unknown" : server.serverIP.toLowerCase(Locale.ROOT)); }
        if (mc.thePlayer != null) world += ":" + mc.thePlayer.getUniqueID();
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(world.getBytes("UTF-8"));
            StringBuilder key = new StringBuilder("history."); for (byte b : bytes) key.append(String.format(Locale.ROOT, "%02x", b & 255));
            String value=key.toString();
            if(cachedScope!=null&&!cachedScope.equals(value)) dimensionNames.clear();
            ItemAstralCompass.setClientSearchStatus(ItemAstralCompass.STATUS_IDLE, "");
            scopeWorld=new java.lang.ref.WeakReference<Object>(mc.theWorld);cachedScope=value;return cachedScope;
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
    public void enterWorld() { scope(); }
    public static final class Visit {
        public final String id, name; public final int dimension, x, y, z; public final boolean hasY;
        Visit(String id, String name, int dimension, int x, int y, int z, boolean hasY) { this.id=id; this.name=name; this.dimension=dimension; this.x=x; this.y=y; this.z=z; this.hasY=hasY; }
        public StructureTargetInfo info() { return new StructureTargetInfo(id, name, StructureTargetInfo.categoryForId(id), dimension, true, id.hashCode()); }
        public String coordinates() { return "X: " + x + (hasY ? " Y: " + y : "") + " Z: " + z; }
    }
    public List<Visit> history() {
        List<Visit> visits = new ArrayList<Visit>();
        String encoded = data.getProperty(scope(), "");
        if (encoded.length() > 32768 || encoded.isEmpty()) return visits;
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(encoded)))) {
            int count = in.readInt(); if (count < 0 || count > 12) return visits;
            for (int i=0;i<count;i++) {
                Visit v = new Visit(in.readUTF(), in.readUTF(), in.readInt(), in.readInt(), in.readInt(), in.readInt(), in.readBoolean());
                if (am2.compass.AstralStructureLocator.validId(v.id) && Math.abs((long)v.x)<=30000000 && Math.abs((long)v.z)<=30000000) visits.add(v);
            }
        } catch (IOException | IllegalArgumentException ignored) { visits.clear(); }
        return visits;
    }
    public void remember(ItemAstralCompass.TargetData target) {
        if (target == null) return;
        String scope = scope(), key = scope + ":" + target.id + ":" + target.dimension + ":" + target.x + ":" + target.y + ":" + target.z;
        if (key.equals(lastRecorded)) return;
        lastRecorded = key;
        List<Visit> visits = history();
        for (Iterator<Visit> it=visits.iterator();it.hasNext();) { Visit v=it.next(); if (v.id.equals(target.id) && v.dimension==target.dimension && v.x==target.x && v.z==target.z) it.remove(); }
        visits.add(0, new Visit(target.id, target.name, target.dimension, target.x, target.y, target.z, target.hasY));
        while(visits.size()>12) visits.remove(visits.size()-1);
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(); DataOutputStream out = new DataOutputStream(bytes);
            out.writeInt(visits.size()); for(Visit v:visits) { out.writeUTF(v.id); out.writeUTF(v.name); out.writeInt(v.dimension); out.writeInt(v.x); out.writeInt(v.y); out.writeInt(v.z); out.writeBoolean(v.hasY); }
            data.setProperty(scope, Base64.getEncoder().encodeToString(bytes.toByteArray()));
            List<String> scopes=new ArrayList<String>(); for(String k:data.stringPropertyNames()) if(k.startsWith("history.")) scopes.add(k);
            int count=scopes.size();
            for(String remove:scopes) if(count>32 && !remove.equals(scope)) {data.remove(remove);count--;}
            save();
        } catch(IOException e) { am2.LogHelper.warn("Astral Compass history: %s",e.toString()); }
    }
}
