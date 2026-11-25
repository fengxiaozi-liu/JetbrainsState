import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarFile;
import org.jetbrains.org.objectweb.asm.ClassReader;
import org.jetbrains.org.objectweb.asm.Opcodes;
import org.jetbrains.org.objectweb.asm.tree.ClassNode;
import org.jetbrains.org.objectweb.asm.ClassWriter;
import org.jetbrains.org.objectweb.asm.tree.FieldInsnNode;
import org.jetbrains.org.objectweb.asm.tree.MethodNode;
import java.util.Arrays;

public final class VerifyTransform {
    public static void main(String[] args) throws Exception {
        verifyDisplayDates();
        for (String target : LocalStateAgent.EXPECTED_SHA256.keySet()) verify(args, target);
    }
    private static void verify(String[] args, String target) throws Exception {
        byte[] original;
        boolean about = target.equals(LocalStateAgent.ABOUT_TARGET);
        boolean countdown = target.equals(LocalStateAgent.COUNTDOWN_TARGET);
        boolean expiryModel = target.equals(LocalStateAgent.EXPIRY_MODEL_TARGET);
        try (var jar = new JarFile(about ? args[2] : args[0])) {
            original = jar.getInputStream(jar.getJarEntry(target + ".class")).readAllBytes();
        }
        byte[] patched = LocalStateAgent.rewrite(original);
        var before = new ClassNode();
        var after = new ClassNode();
        new ClassReader(original).accept(before, 0);
        new ClassReader(patched).accept(after, 0);
        if (!before.name.equals(after.name) || before.methods.size() != after.methods.size()
                || before.fields.size() != after.fields.size()) throw new AssertionError("Class schema changed");
        boolean dialogGate = target.endsWith("/p8");
        String targetMethod = about ? "getText" : dialogGate ? "F" : "t";
        String targetDescriptor = expiryModel ? LocalStateAgent.EXPIRY_MODEL_DESCRIPTOR : countdown ? LocalStateAgent.COUNTDOWN_DESCRIPTOR : about ? "()Ljavax/swing/Box;" : dialogGate ? "(JZ)V" : "(J)Z";
        var method = after.methods.stream().filter(m -> m.name.equals(targetMethod) && m.desc.equals(targetDescriptor))
                .findFirst().orElseThrow();
        if (expiryModel) {
            int replacements = 0;
            int unchangedMissingDate = 0;
            for (var instruction : method.instructions.toArray()) {
                if (!(instruction instanceof org.jetbrains.org.objectweb.asm.tree.TypeInsnNode allocation)
                        || allocation.getOpcode() != Opcodes.NEW) continue;
                if (allocation.desc.equals("com/intellij/ide/z/Z/z/t2")) unchangedMissingDate++;
                if (!allocation.desc.equals("com/intellij/ide/z/Z/z/tL")) continue;
                var value = allocation.getNext().getNext();
                if (!(value instanceof org.jetbrains.org.objectweb.asm.tree.LdcInsnNode constant)) continue;
                if (!Integer.valueOf(Integer.MAX_VALUE).equals(constant.cst)
                        || !(value.getNext() instanceof org.jetbrains.org.objectweb.asm.tree.MethodInsnNode constructor)
                        || !constructor.owner.equals(allocation.desc) || !constructor.desc.equals("(I)V")) {
                    throw new AssertionError("Incorrect expiry model replacement");
                }
                allocation.desc = "com/intellij/ide/z/Z/z/t2";
                constructor.owner = allocation.desc;
                constructor.desc = "()V";
                method.instructions.remove(value);
                replacements++;
            }
            if (replacements != 3 || unchangedMissingDate != 1) throw new AssertionError("Expiry/error scope changed");
            for (int i = 0; i < before.methods.size(); i++) {
                if (!Arrays.equals(methodBytes(before.methods.get(i)), methodBytes(after.methods.get(i)))) {
                    throw new AssertionError("Unexpected expiry model instructions changed");
                }
            }
            System.out.println("PASS: three explicit expiry results replaced; missing-date result and all other instructions preserved.");
        } else if (countdown) {
            int sites = 0;
            int messageCalls = 0;
            for (var instruction : method.instructions.toArray()) {
                if (instruction instanceof org.jetbrains.org.objectweb.asm.tree.MethodInsnNode call
                        && call.owner.equals("com/intellij/ide/z/J") && call.name.equals("t")) {
                    messageCalls++;
                    var pop = call.getNext();
                    var blank = pop.getNext();
                    if (messageCalls == 2) {
                        if (pop.getOpcode() != Opcodes.POP
                                || !(blank instanceof org.jetbrains.org.objectweb.asm.tree.LdcInsnNode ldc)
                                || !"".equals(ldc.cst)) throw new AssertionError("Countdown not blanked");
                        method.instructions.remove(pop);
                        method.instructions.remove(blank);
                        sites++;
                    }
                }
            }
            if (sites != 1 || messageCalls != 2) throw new AssertionError("Unexpected title sites");
            for (int i = 0; i < before.methods.size(); i++) {
                if (!Arrays.equals(methodBytes(before.methods.get(i)), methodBytes(after.methods.get(i)))) {
                    throw new AssertionError("Unrelated countdown method change");
                }
            }
            System.out.println("PASS: only countdown title result blanked; original date calculation, expired title and other methods preserved.");
        } else if (about) {
            int helpers = 0;
            for (var instruction : method.instructions.toArray()) {
                if (instruction instanceof org.jetbrains.org.objectweb.asm.tree.MethodInsnNode call
                        && call.owner.equals("LocalStateAgent") && call.name.equals("shiftAboutMessages")) {
                    if (call.getOpcode() != Opcodes.INVOKESTATIC
                            || !call.desc.equals("(Ljava/util/List;)Ljava/util/List;")
                            || !(call.getPrevious() instanceof org.jetbrains.org.objectweb.asm.tree.MethodInsnNode previous)
                            || !previous.owner.equals("com/intellij/ui/LicensingFacade")
                            || !previous.name.equals("getLicenseRestrictionsMessages")) {
                        throw new AssertionError("Incorrect About hook");
                    }
                    method.instructions.remove(call);
                    helpers++;
                }
            }
            if (helpers != 1) throw new AssertionError("Expected one display helper");
            for (int i = 0; i < before.methods.size(); i++) {
                if (!Arrays.equals(methodBytes(before.methods.get(i)), methodBytes(after.methods.get(i)))) {
                    throw new AssertionError("Unexpected About change: " + before.methods.get(i).name);
                }
            }
            System.out.println("PASS: About changes only one presentation call site.");
        } else if (dialogGate) {
            for (var instruction : method.instructions.toArray()) {
                if (instruction instanceof org.jetbrains.org.objectweb.asm.tree.MethodInsnNode call
                        && call.owner.equals("com/intellij/ide/z/I/a")) throw new AssertionError("Startup dialog still present");
            }
            verifyExpiryScope(before, after);
        } else if (method.instructions.size() != 2
                || method.instructions.getFirst().getOpcode() != Opcodes.ICONST_1
                || method.instructions.getLast().getOpcode() != Opcodes.IRETURN
                || !method.tryCatchBlocks.isEmpty()) throw new AssertionError("Incorrect replacement");
        byte[] invalid = original.clone();
        invalid[invalid.length - 1] ^= 1;
        try {
            LocalStateAgent.rewrite(invalid);
            throw new AssertionError("Fingerprint guard did not reject altered input");
        } catch (IllegalArgumentException expected) {
            System.out.println("PASS: " + target + " transformed; altered input rejected.");
        }
        Path output = Path.of(args[1]).resolve(target + ".class");
        Files.createDirectories(output.getParent());
        Files.write(output, patched);
    }

    private static void verifyDisplayDates() {
        var original = java.util.List.of("Subscription is active until December 10, 2026.", "Built on November 5, 2026");
        var expected = java.util.List.of("Subscription is active until December 10, 2125.", "Built on November 5, 2026");
        if (!LocalStateAgent.shiftAboutMessages(original).equals(expected)
                || !LocalStateAgent.shiftAboutMessages(original).equals(expected)
                || !original.get(0).equals("Subscription is active until December 10, 2026.")) {
            throw new AssertionError("Date shift or original-list preservation failed");
        }
        var leap = java.util.List.of("Subscription is active until February 29, 2028.");
        if (!LocalStateAgent.shiftAboutMessages(leap).get(0).equals("Subscription is active until February 28, 2127.")) {
            throw new AssertionError("Leap day handling failed");
        }
        var untouched = Arrays.asList("Subscription is active until February 30, 2026.", "Localized text", null);
        var empty = java.util.List.<String>of();
        if (LocalStateAgent.shiftAboutMessages(untouched) != untouched
                || LocalStateAgent.shiftAboutMessages(empty) != empty
                || LocalStateAgent.shiftAboutMessages(null) != null) throw new AssertionError("Fallback changed input");
        System.out.println("PASS: +99 calendar years, leap day, repeated display, immutable input and unknown-message fallback.");
    }

    private static byte[] methodBytes(MethodNode method) {
        var writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, "MethodSnapshot", null, "java/lang/Object", null);
        method.accept(writer);
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static void verifyExpiryScope(ClassNode before, ClassNode after) {
        int changedResults = 0;
        for (int i = 0; i < before.methods.size(); i++) {
            var original = before.methods.get(i);
            var patched = after.methods.get(i);
            if (original.name.equals("F") && original.desc.equals("(JZ)V")) continue;
            if (original.name.equals("t") && original.desc.equals(LocalStateAgent.LICENSE_STATE_DESCRIPTOR)) {
                var oldInstructions = original.instructions.toArray();
                var newInstructions = patched.instructions.toArray();
                if (oldInstructions.length != newInstructions.length) throw new AssertionError("State calculation shape changed");
                for (int j = 0; j < oldInstructions.length; j++) {
                    if (oldInstructions[j] instanceof FieldInsnNode oldField
                            && oldField.owner.equals(LocalStateAgent.TARGET) && oldField.name.equals("z")) {
                        if (!(newInstructions[j] instanceof FieldInsnNode newField)
                                || newField.getOpcode() != Opcodes.GETSTATIC
                                || !newField.owner.equals(oldField.owner) || !newField.desc.equals(oldField.desc)
                                || !newField.name.equals("E")) throw new AssertionError("Expired-state result not replaced");
                        newField.name = "z";
                        try {
                            if (!Arrays.equals(methodBytes(original), methodBytes(patched))) {
                                throw new AssertionError("Unrelated license validation or error handling changed");
                            }
                        } finally { newField.name = "E"; }
                        changedResults++;
                    }
                }
            } else if (!Arrays.equals(methodBytes(original), methodBytes(patched))) {
                throw new AssertionError("Unexpected method change: " + original.name + original.desc);
            }
        }
        if (changedResults != 1) throw new AssertionError("Expected exactly one expired-state result");
        System.out.println("PASS: trial-expiry result replaced; other state-calculation instructions and failure branches unchanged.");
    }
}
