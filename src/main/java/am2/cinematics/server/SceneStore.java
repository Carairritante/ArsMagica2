package am2.cinematics.server;

import am2.cinematics.AMCinematics;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.zip.CRC32;

import am2.cinematics.scene.Scene;

/** Cenas em config/amcinematics/scenes, relidas a cada execução (edição a quente). */
public final class SceneStore {
    private static final String[] BUNDLED = { "orbita", "demo", FirstBookTrigger.SCENE, "fusao", "fusao_falha" };
    private static File folder;

    private SceneStore() {}

    public static void init(File configDirectory) {
        folder = new File(configDirectory, "amcinematics/scenes");
        if (!folder.isDirectory() && !folder.mkdirs()) {
            AMCinematics.log.error("Não foi possível criar " + folder);
            return;
        }
        // Guarda como cada cena foi instalada, para saber depois se foi editada.
        File record = new File(folder.getParentFile(), "instaladas.properties");
        Properties installed = new Properties();
        try {
            if (record.isFile()) {
                InputStream in = new FileInputStream(record);
                try {
                    installed.load(in);
                } finally {
                    in.close();
                }
            }
            for (String name : BUNDLED) {
                InputStream in = SceneStore.class.getResourceAsStream("/assets/amcinematics/scenes/" + name + ".json");
                if (in == null) throw new IOException("recurso ausente: " + name);
                byte[] data = readAll(in);
                File target = new File(folder, name + ".json");
                if (target.exists()) {
                    String current = checksum(readAll(new FileInputStream(target)));
                    // Cena editada pelo usuário (ou de origem desconhecida) é preservada.
                    if (!current.equals(checksum(data)) && !current.equals(installed.getProperty(name))) continue;
                }
                write(target, data);
                installed.setProperty(name, checksum(data));
            }
            OutputStream out = new FileOutputStream(record);
            try {
                installed.store(out, "AMCinematics: cenas incluidas, como foram instaladas");
            } finally {
                out.close();
            }
        } catch (IOException e) {
            AMCinematics.log.error("Falha ao preparar as cenas: " + e);
        }
    }

    private static void write(File target, byte[] data) throws IOException {
        OutputStream out = new FileOutputStream(target);
        try {
            out.write(data);
        } finally {
            out.close();
        }
    }

    private static String checksum(byte[] data) {
        CRC32 crc = new CRC32();
        crc.update(data);
        return data.length + "-" + Long.toHexString(crc.getValue());
    }

    public static List<String> names() {
        List<String> result = new ArrayList<String>();
        File[] files = folder == null ? null : folder.listFiles();
        if (files != null) {
            for (File file : files) {
                String name = file.getName();
                if (name.endsWith(".json") && isName(name.substring(0, name.length() - 5))) {
                    result.add(name.substring(0, name.length() - 5));
                }
            }
        }
        Collections.sort(result);
        return result;
    }

    /** Lê o texto da cena. O nome é restrito para não permitir caminhos arbitrários. */
    public static String read(String name) throws IOException {
        if (!isName(name)) throw new IOException("nome inválido (use a-z, 0-9, _ e -)");
        File file = new File(folder, name + ".json");
        if (!file.isFile()) throw new IOException("cena não encontrada: " + name);
        if (file.length() > Scene.MAX_BYTES) throw new IOException("cena maior que " + Scene.MAX_BYTES + " bytes");
        String text = new String(readAll(new FileInputStream(file)), Scene.UTF8);
        // Editores do Windows costumam gravar UTF-8 com BOM.
        return text.startsWith("\uFEFF") ? text.substring(1) : text;
    }

    private static boolean isName(String name) {
        return name.matches("[a-z0-9_-]{1,32}");
    }

    private static byte[] readAll(InputStream in) throws IOException {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                if (out.size() > Scene.MAX_BYTES) throw new IOException("cena grande demais");
            }
            return out.toByteArray();
        } finally {
            in.close();
        }
    }
}
