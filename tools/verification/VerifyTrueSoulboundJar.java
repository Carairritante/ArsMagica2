import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import javax.xml.parsers.DocumentBuilderFactory;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

public final class VerifyTrueSoulboundJar {

    public static void main(String[] args) throws Exception {
        try (JarFile jar = new JarFile(args[0])) {
            String[] requiredClasses = {
                "am2/soulbound/TrueSoulboundHelper.class",
                "am2/soulbound/TrueSoulboundVault.class",
                "am2/soulbound/TrueSoulboundRitual.class",
                "am2/soulbound/TrueSoulboundHandler.class",
                "am2/soulbound/TrueSoulboundClientHandler.class",
                "am2/entities/EntityTrueSoulboundItem.class",
                "am2/commands/CommandTrueSoulboundUnbind.class",
                "am2/commands/CommandTrueSoulboundDestroy.class"
            };
            for (String path : requiredClasses) {
                read(jar, path);
            }

            assertFieldAndCall(jar, "am2/RitualShapeHelper.class", "trueSoulbound", "am2/RitualShapeHelper", "initTrueSoulbound");
            assertMethodCalls(jar, "am2/spell/components/Recall.class", "am2/soulbound/TrueSoulboundRitual", "tryExecute");
            assertMethodCalls(jar, "am2/spell/components/Recall.class", "am2/soulbound/TrueSoulboundRitual", "resolveRitualCenter");
            assertStringConstant(jar, "am2/ritual/RitualArchitect.class", "true_soulbound");
            assertTypeRef(jar, "am2/entities/EntityManager.class", "am2/entities/EntityTrueSoulboundItem");
            assertTypeRef(jar, "am2/proxy/CommonProxy.class", "am2/soulbound/TrueSoulboundHandler");
            assertTypeRef(jar, "am2/proxy/ClientProxy.class", "am2/soulbound/TrueSoulboundClientHandler");
            assertTypeRef(jar, "am2/AMCore.class", "am2/commands/CommandTrueSoulboundUnbind");
            assertTypeRef(jar, "am2/AMCore.class", "am2/commands/CommandTrueSoulboundDestroy");
            assertMethodCalls(jar, "am2/items/ItemSpellBook.class", "am2/soulbound/TrueSoulboundHelper", "isOwnerOrUnbound");
            assertMethodCalls(jar, "am2/network/AMPacketProcessorServer.class", "am2/soulbound/TrueSoulboundHelper", "isOwnerOrUnbound");
            assertMethodCalls(jar, "am2/spell/components/Disarm.class", "am2/soulbound/TrueSoulboundHelper", "isTrueSoulbound");
            assertMethodCalls(jar, "am2/entities/ai/EntityAIPickup.class", "am2/soulbound/TrueSoulboundHelper", "isTrueSoulbound");
            assertMethodCalls(jar, "am2/entities/ai/EntityAITargetNearbyInanimateSmeltable.class", "am2/soulbound/TrueSoulboundHelper", "isTrueSoulbound");
            assertMethodCalls(jar, "am2/soulbound/TrueSoulboundHandler.class", "am2/soulbound/TrueSoulboundHandler", "reconcileLiveInventorySnapshot");
            assertMethodCalls(jar, "am2/soulbound/TrueSoulboundHandler.class", "am2/soulbound/TrueSoulboundHelper", "clampDurability");
            assertNoDirectAE2Ref(jar, "am2/soulbound/TrueSoulboundHandler.class");

            assertLangKeys(jar, "assets/arsmagica2/lang/en_US.lang",
                    "am2.architect.ritual.true_soulbound",
                    "am2.architect.activate.true_soulbound",
                    "am2.truesoulbound.tooltip.header",
                    "am2.truesoulbound.thief");
            assertLangKeys(jar, "assets/arsmagica2/lang/pt_BR.lang",
                    "am2.architect.ritual.true_soulbound",
                    "am2.architect.activate.true_soulbound",
                    "am2.truesoulbound.tooltip.header",
                    "am2.truesoulbound.thief");

            assertMethodCalls(jar, "am2/guis/GuiArcaneCompendium.class", "am2/soulbound/TrueSoulboundRitual", "getReagents");
            assertStringConstant(jar, "am2/lore/CompendiumEntry.class", "true_soulbound");

            String[] compendiumXmls = {
                "assets/arsmagica2/docs/ArcaneCompendium_pt_BR.xml",
                "assets/arsmagica2/docs/ArcaneCompendium_en_US.xml",
                "assets/arsmagica2/docs/ArcaneCompendium_de_DE.xml",
                "assets/arsmagica2/docs/ArcaneCompendium_fr_FR.xml",
                "assets/arsmagica2/docs/ArcaneCompendium_ru_RU.xml",
                "assets/arsmagica2/docs/ArcaneCompendium_zh_CN.xml"
            };
            for (String xmlPath : compendiumXmls) {
                assertCompendiumXml(jar, xmlPath,
                        "id=\"true_soulbound\"",
                        "id=\"true_soulbound_ritual\"",
                        "<ritualName>trueSoulbound</ritualName>",
                        "<ritualController>Recall</ritualController>");
            }
            assertCompendiumXml(jar, "assets/arsmagica2/docs/ArcaneCompendium_pt_BR.xml",
                    "id=\"true_soulbound_seal\"",
                    "Ritual do Vínculo Eterno");
            assertCompendiumXml(jar, "assets/arsmagica2/docs/ArcaneCompendium_en_US.xml",
                    "id=\"true_soulbound_seal\"",
                    "Ritual of the Eternal Bond");

            System.out.println("Verified: True Soulbound ritual, protections, entity, commands, Compendium lore, and localization are wired in JAR.");
        }
    }

    private static ClassNode read(JarFile jar, String path) throws Exception {
        JarEntry entry = jar.getJarEntry(path);
        if (entry == null) {
            throw new IllegalStateException("Missing expected class in JAR: " + path);
        }
        ClassNode node = new ClassNode();
        try (InputStream in = jar.getInputStream(entry)) {
            new ClassReader(in).accept(node, 0);
        }
        return node;
    }

    private static void assertFieldAndCall(JarFile jar, String path, String fieldName, String owner, String method) throws Exception {
        ClassNode cn = read(jar, path);
        boolean foundField = false;
        for (FieldNode f : cn.fields) {
            if (fieldName.equals(f.name)) {
                foundField = true;
                break;
            }
        }
        if (!foundField) {
            throw new IllegalStateException("Missing field " + fieldName + " in " + path);
        }
        assertMethodCalls(jar, path, owner, method);
    }

    private static void assertMethodCalls(JarFile jar, String path, String targetOwner, String targetMethod) throws Exception {
        ClassNode cn = read(jar, path);
        for (MethodNode mn : cn.methods) {
            for (AbstractInsnNode insn : mn.instructions.toArray()) {
                if (insn instanceof MethodInsnNode) {
                    MethodInsnNode m = (MethodInsnNode) insn;
                    if (targetOwner.equals(m.owner) && targetMethod.equals(m.name)) {
                        return;
                    }
                }
            }
        }
        throw new IllegalStateException("Expected call to " + targetOwner + "." + targetMethod + " in " + path);
    }

    private static void assertTypeRef(JarFile jar, String path, String internalName) throws Exception {
        ClassNode cn = read(jar, path);
        String targetDesc = "L" + internalName + ";";
        for (MethodNode mn : cn.methods) {
            for (AbstractInsnNode insn : mn.instructions.toArray()) {
                if (insn instanceof TypeInsnNode && internalName.equals(((TypeInsnNode) insn).desc)) {
                    return;
                }
                if (insn instanceof MethodInsnNode && internalName.equals(((MethodInsnNode) insn).owner)) {
                    return;
                }
                if (insn instanceof LdcInsnNode) {
                    Object cst = ((LdcInsnNode) insn).cst;
                    if (cst instanceof Type && targetDesc.equals(((Type) cst).getDescriptor())) {
                        return;
                    }
                }
            }
        }
        throw new IllegalStateException("Expected reference to " + internalName + " in " + path);
    }

    private static void assertStringConstant(JarFile jar, String path, String literal) throws Exception {
        ClassNode cn = read(jar, path);
        for (MethodNode mn : cn.methods) {
            for (AbstractInsnNode insn : mn.instructions.toArray()) {
                if (insn instanceof LdcInsnNode && literal.equals(((LdcInsnNode) insn).cst)) {
                    return;
                }
            }
        }
        throw new IllegalStateException("Expected string constant \"" + literal + "\" in " + path);
    }

    private static void assertLangKeys(JarFile jar, String entryName, String... keys) throws Exception {
        JarEntry entry = jar.getJarEntry(entryName);
        if (entry == null) {
            throw new IllegalStateException("Missing lang file in JAR: " + entryName);
        }
        Set<String> present = new HashSet<String>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(jar.getInputStream(entry), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                int eq = line.indexOf('=');
                if (eq > 0) {
                    present.add(line.substring(0, eq).trim());
                }
            }
        }
        for (String key : keys) {
            if (!present.contains(key)) {
                throw new IllegalStateException("Missing localization key '" + key + "' in " + entryName);
            }
        }
    }

    private static void assertNoDirectAE2Ref(JarFile jar, String path) throws Exception {
        ClassNode cn = read(jar, path);
        for (MethodNode mn : cn.methods) {
            checkNotAE2(mn.desc, path + "#" + mn.name);
            for (AbstractInsnNode insn : mn.instructions.toArray()) {
                if (insn instanceof MethodInsnNode) {
                    MethodInsnNode m = (MethodInsnNode) insn;
                    checkNotAE2(m.owner, path + "#" + mn.name);
                    checkNotAE2(m.desc, path + "#" + mn.name);
                } else if (insn instanceof FieldInsnNode) {
                    FieldInsnNode f = (FieldInsnNode) insn;
                    checkNotAE2(f.owner, path + "#" + mn.name);
                    checkNotAE2(f.desc, path + "#" + mn.name);
                } else if (insn instanceof TypeInsnNode) {
                    TypeInsnNode t = (TypeInsnNode) insn;
                    checkNotAE2(t.desc, path + "#" + mn.name);
                }
            }
        }
    }

    private static void checkNotAE2(String name, String context) {
        if (name != null && name.contains("appeng/")) {
            throw new IllegalStateException("Direct AE2 bytecode dependency found (" + name + ") in " + context);
        }
    }

    private static void assertCompendiumXml(JarFile jar, String entryName, String... expectedSubstrings) throws Exception {
        JarEntry entry = jar.getJarEntry(entryName);
        if (entry == null) {
            throw new IllegalStateException("Missing Compendium XML in JAR: " + entryName);
        }
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(jar.getInputStream(entry), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append('\n');
            }
        }
        String xml = sb.toString();
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.newDocumentBuilder().parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        for (String expected : expectedSubstrings) {
            if (!xml.contains(expected)) {
                throw new IllegalStateException("Missing expected Compendium XML content '" + expected + "' in " + entryName);
            }
        }
    }
}
