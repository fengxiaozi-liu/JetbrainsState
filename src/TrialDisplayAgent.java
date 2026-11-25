import java.lang.instrument.*;
import java.security.ProtectionDomain;
import org.jetbrains.org.objectweb.asm.*;
import org.jetbrains.org.objectweb.asm.tree.*;

/** Only the trial countdown's presentation resource, never license state. */
public final class TrialDisplayAgent {
    private static boolean hide;
    public static void premain(String options, Instrumentation instrumentation) {
        hide = "hide".equals(options);
        instrumentation.addTransformer(new ClassFileTransformer() {
            public byte[] transform(ClassLoader loader, String name, Class<?> cls, ProtectionDomain domain, byte[] bytes) {
                if (!"com/intellij/AbstractBundle".equals(name)) return null;
                var node = new ClassNode();
                new ClassReader(bytes).accept(node, 0);
                int changes = 0;
                for (var method : node.methods) {
                    if (!method.name.equals("getMessage") || !method.desc.equals("(Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/String;")) continue;
                    var code = new InsnList();
                    code.add(new VarInsnNode(Opcodes.ALOAD, 1));
                    code.add(new VarInsnNode(Opcodes.ALOAD, 2));
                    code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "TrialDisplayAgent", "trace", "(Ljava/lang/String;[Ljava/lang/Object;)V", false));
                    method.instructions.insert(code);
                    method.maxStack = Math.max(method.maxStack, 2);
                    changes++;
                }
                if (changes != 1) throw new IllegalStateException("Unsupported message entry points: " + changes);
                var writer = new ClassWriter(0);
                node.accept(writer);
                System.err.println("[trial-display] Resource tracing armed; hide=" + hide);
                return writer.toByteArray();
            }
        });
    }
    public static void trace(String key, Object[] args) {
        if (!"auto.trial.dialog.trial.days.left".equals(key)) return;
        System.err.println("[trial-display] Countdown args=" + java.util.Arrays.toString(args));
        new Exception("[trial-display] Countdown caller").printStackTrace(System.err);
    }
}
