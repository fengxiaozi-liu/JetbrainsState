import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.security.ProtectionDomain;
import java.util.jar.JarFile;
import org.jetbrains.org.objectweb.asm.*;
import org.jetbrains.org.objectweb.asm.tree.*;

/** TEST ONLY: replay the real expired-trial exception inside an isolated IDE. */
public final class ExpiryReplayAgent {
    private static final String OWNER = "com/intellij/ide/z/L/p8";
    private static final String DESCRIPTOR = "(Lcom/intellij/ide/z/S;Lcom/intellij/ide/z/c;J)Lcom/intellij/ide/z/L/tH;";
    public static void premain(String jarPath, Instrumentation instrumentation) throws Exception {
        int branchIndex = -1;
        try (var jar = new JarFile(jarPath)) {
            var original = new ClassNode();
            new ClassReader(jar.getInputStream(jar.getJarEntry(OWNER + ".class")).readAllBytes()).accept(original, 0);
            for (var method : original.methods) {
                if (!method.name.equals("t") || !method.desc.equals(DESCRIPTOR)) continue;
                var instructions = method.instructions.toArray();
                for (int i = 0; i < instructions.length; i++) {
                    if (instructions[i] instanceof FieldInsnNode f && f.owner.equals("com/intellij/ide/z/L/tH") && f.name.equals("z")) branchIndex = i;
                }
            }
        }
        if (branchIndex < 0) throw new IllegalStateException("No expiry branch found");
        final int expiredResultIndex = branchIndex;
        instrumentation.addTransformer(new ClassFileTransformer() {
            @Override public byte[] transform(ClassLoader loader, String name, Class<?> type, ProtectionDomain domain, byte[] bytes) {
                if (!OWNER.equals(name)) return null;
                var node = new ClassNode();
                new ClassReader(bytes).accept(node, 0);
                int calls = 0;
                for (var method : node.methods) {
                    if (!method.name.equals("t") || !method.desc.equals(DESCRIPTOR)) continue;
                    var instructions = method.instructions.toArray();
                    var result = (FieldInsnNode) instructions[expiredResultIndex];
                    var trace = new InsnList();
                    trace.add(new LdcInsnNode(result.name));
                    trace.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "ExpiryReplayAgent", "result", "(Ljava/lang/String;)V", false));
                    method.instructions.insertBefore(result, trace);
                    for (var instruction : instructions) {
                        if (instruction instanceof MethodInsnNode call && call.owner.equals("com/intellij/ide/z/c")
                                && call.name.equals("z") && call.desc.equals("(J)V")) {
                            method.instructions.set(call, new MethodInsnNode(Opcodes.INVOKESTATIC, "ExpiryReplayAgent", "expire", "(Ljava/lang/Object;J)V", false));
                            calls++;
                        }
                    }
                    method.maxStack += 1;
                }
                if (calls != 1) throw new IllegalStateException("Expected one validator call, got " + calls);
                var writer = new ClassWriter(0);
                node.accept(writer);
                System.err.println("[expiry-replay] Installed isolated expiry-exception replay.");
                return writer.toByteArray();
            }
        });
    }
    public static void result(String field) {
        System.err.println("[expiry-replay] Real trial-expiry handler reached; result field=" + field);
    }
    public static void expire(Object validator, long ignored) throws Exception {
        var exceptionType = Class.forName("com.intellij.ide.z.L.zN", true, validator.getClass().getClassLoader());
        var exception = (Exception) exceptionType.getConstructor().newInstance();
        System.err.println("[expiry-replay] Injecting " + exception.getClass().getName());
        throw exception;
    }
}
