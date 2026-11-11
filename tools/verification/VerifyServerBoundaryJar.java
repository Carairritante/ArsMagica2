import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

/** Packaging contracts that previously let client-only models reach a server. */
public final class VerifyServerBoundaryJar {
    public static void main(String[] args) throws Exception {
        try (JarFile jar=new JarFile(args[0])) {
            ClassNode common=read(jar,"am2/armor/ArmorEventHandler.class");
            for (FieldNode field:common.fields) safe(field.desc,"field "+field.name);
            for (MethodNode method:common.methods) {
                safe(method.desc,"method "+method.name);
                for (AbstractInsnNode insn:method.instructions) {
                    if (insn instanceof MethodInsnNode) {
                        MethodInsnNode call=(MethodInsnNode)insn;
                        safe(call.owner,"call in "+method.name); safe(call.desc,"call descriptor in "+method.name);
                    } else if (insn instanceof FieldInsnNode) {
                        FieldInsnNode field=(FieldInsnNode)insn;
                        safe(field.owner,"field access in "+method.name); safe(field.desc,"field descriptor in "+method.name);
                    } else if (insn instanceof TypeInsnNode) {
                        safe(((TypeInsnNode)insn).desc,"type in "+method.name);
                    } else if (insn instanceof LdcInsnNode && ((LdcInsnNode)insn).cst instanceof Type) {
                        safe(((Type)((LdcInsnNode)insn).cst).getDescriptor(),"class literal in "+method.name);
                    }
                }
            }
            ClassNode core=read(jar,"am2/AMCore.class");
            String version=annotationVersion(core.visibleAnnotations);
            if (version==null || version.isEmpty()) throw new IllegalStateException("Missing @Mod version");
            String metadata;
            try (InputStream in=jar.getInputStream(jar.getJarEntry("mcmod.info"))) {
                java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();
                byte[] buffer=new byte[4096]; int n;
                while ((n=in.read(buffer))!=-1) out.write(buffer,0,n);
                metadata=new String(out.toByteArray(),StandardCharsets.UTF_8);
            }
            Matcher match=Pattern.compile("\"version\"\\s*:\\s*\"([^\"]+)\"").matcher(metadata);
            if (!match.find() || !"1.6.7".equals(match.group(1))) throw new IllegalStateException("Legacy metadata compatibility version changed");
            Matcher maintenance=Pattern.compile("\"maintenanceVersion\"\\s*:\\s*\"([^\"]+)\"").matcher(metadata);
            if (!maintenance.find() || !version.equals(maintenance.group(1))) throw new IllegalStateException("@Mod and metadata maintenance versions disagree");
            if (!jar.getName().endsWith("AM2.5-LTS-"+version+".jar")) throw new IllegalStateException("Filename and internal mod version disagree: "+version);
            System.out.println("Verified: common armor handler has no client graphics references; filename, @Mod and maintenanceVersion agree ("+version+"); legacy preloader metadata remains 1.6.7.");
        }
    }
    private static ClassNode read(JarFile jar,String name) throws Exception {
        if (jar.getJarEntry(name)==null) throw new IllegalStateException("Missing "+name);
        ClassNode node=new ClassNode();
        try (InputStream in=jar.getInputStream(jar.getJarEntry(name))) { new ClassReader(in).accept(node,0); }
        return node;
    }
    private static String annotationVersion(List<AnnotationNode> annotations) {
        if (annotations==null) return null;
        for (AnnotationNode annotation:annotations) {
            if (!annotation.desc.equals("Lcpw/mods/fml/common/Mod;")) continue;
            for (int i=0;i<annotation.values.size();i+=2) {
                if (annotation.values.get(i).equals("version")) return (String)annotation.values.get(i+1);
            }
        }
        return null;
    }
    private static void safe(String name,String location) {
        for (String forbidden:new String[]{"net/minecraft/client/","net/minecraftforge/client/","org/lwjgl/","am2/armor/ArmorClientEventHandler"}) {
            if (name.contains(forbidden)) throw new IllegalStateException("Client-only dependency in common armor handler: "+location+": "+name);
        }
    }
}
