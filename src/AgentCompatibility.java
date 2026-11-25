import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.jar.JarFile;

/** Read-only class-fingerprint check, used before any startup settings are written. */
public final class AgentCompatibility {
    public static void main(String[] args) throws Exception {
        Map<String,String> hashes = switch(args[0]) {
            case "PY-263.7307.351" -> PyCharmStateAgent.HASHES;
            case "GO-263.7307.211" -> LocalStateAgent.EXPECTED_SHA256;
            default -> throw new IllegalArgumentException("Unsupported profile: "+args[0]);
        };
        for (var entry : hashes.entrySet()) {
            String jarName = entry.getKey().equals(LocalStateAgent.ABOUT_TARGET)
                    ? "intellij.platform.ide.impl.jar" : "intellij.platform.cs.jar";
            try (var jar=new JarFile(Path.of(args[1],"lib",jarName).toFile())) {
                var file=jar.getJarEntry(entry.getKey()+".class");
                if (file==null) throw new IllegalArgumentException("Required class missing: "+entry.getKey());
                String actual;
                try(var stream=jar.getInputStream(file)) {
                    actual=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(stream.readAllBytes()));
                }
                if (!actual.equals(entry.getValue())) throw new IllegalArgumentException("Class fingerprint differs: "+entry.getKey());
            }
        }
        System.out.println("PASS: "+args[0]+"; all "+hashes.size()+" original class fingerprints match.");
    }
}
