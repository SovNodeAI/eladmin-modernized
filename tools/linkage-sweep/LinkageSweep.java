/*
 * Runtime linkage sweep for the eladmin migration.
 *
 * Scans the constant pools of every jar on a resolved classpath and reports
 * which artifacts still reference a removed namespace (for example
 * javax/mail during the jakarta migration). Compile success proves nothing
 * about linkage: the reference that broke the email path lived inside a
 * third-party jar's bytecode, invisible to any source-level import grep.
 *
 * Usage:
 *   java tools/linkage-sweep/LinkageSweep.java <namespace> <jar> [jar...]
 *   java tools/linkage-sweep/LinkageSweep.java javax/mail $(cat classpath.txt | tr ':' ' ')
 *
 * The namespace is matched as a slash-separated internal prefix, so
 * "javax/mail" matches every Utf8 constant that contains it. Only class
 * files are inspected; the tool never executes any scanned code.
 *
 * Exit codes: 0 = sweep completed (offenders may exist and are listed),
 * 1 = usage error, 2 = a jar could not be read.
 */

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public final class LinkageSweep {

    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.err.println("usage: LinkageSweep <namespace> <jar> [jar...]");
            System.exit(1);
        }
        String needle = args[0].replace('.', '/');
        int exit = 0;
        long totalMatches = 0;
        List<String> offenders = new ArrayList<>();
        for (int i = 1; i < args.length; i++) {
            Path jarPath = Path.of(args[i]);
            if (!Files.isRegularFile(jarPath)) {
                System.err.println("not a jar: " + jarPath);
                exit = Math.max(exit, 2);
                continue;
            }
            int matches = scanJar(jarPath, needle);
            if (matches > 0) {
                offenders.add(jarPath.getFileName() + " (" + matches + " references)");
                totalMatches += matches;
            }
        }
        System.out.println("LINKAGE SWEEP for " + args[0] + ": " + (args.length - 1)
                + " jars scanned, " + offenders.size() + " artifacts reference the namespace, "
                + totalMatches + " constant pool references total");
        for (String offender : offenders) {
            System.out.println("  references: " + offender);
        }
        System.exit(exit);
    }

    private static int scanJar(Path jarPath, String needle) throws IOException {
        int matches = 0;
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory() || !entry.getName().endsWith(".class")) {
                    continue;
                }
                try (InputStream in = jar.getInputStream(entry)) {
                    if (references(in, needle)) {
                        matches++;
                    }
                }
            }
        }
        return matches;
    }

    /**
     * Returns true when any Utf8 constant in the class file constant pool
     * contains the needle. Class file layout per JVMS section 4.1: magic,
     * versions, then constant_pool_count (u2) followed by the pool entries.
     */
    private static boolean references(InputStream in, String needle) throws IOException {
        DataInputStream data = new DataInputStream(in);
        int magic = data.readInt();
        if (magic != 0xCAFEBABE) {
            return false;
        }
        data.readUnsignedShort(); // minor
        data.readUnsignedShort(); // major
        int count = data.readUnsignedShort();
        for (int i = 1; i < count; i++) {
            int tag = data.readUnsignedByte();
            switch (tag) {
                case 1: // Utf8
                    int length = data.readUnsignedShort();
                    byte[] bytes = new byte[length];
                    data.readFully(bytes);
                    if (contains(bytes, needle)) {
                        return true;
                    }
                    break;
                case 7, 8, 16, 19, 20: // Class, String, MethodType, Module, Package: one u2
                    data.readUnsignedShort();
                    break;
                case 15: // MethodHandle: one u1 + one u2
                    data.readUnsignedByte();
                    data.readUnsignedShort();
                    break;
                case 3, 4, 9, 10, 11, 12, 17, 18: // integers, floats, refs: two u2
                    data.readUnsignedShort();
                    data.readUnsignedShort();
                    break;
                case 5, 6: // long, double: eight bytes, take two pool slots
                    data.readLong();
                    i++;
                    break;
                default:
                    return false; // unknown tag: give up on this class, stay honest
            }
        }
        return false;
    }

    private static boolean contains(byte[] haystack, String needle) {
        byte[] pattern = needle.getBytes(StandardCharsets.UTF_8);
        outer:
        for (int i = 0; i <= haystack.length - pattern.length; i++) {
            for (int j = 0; j < pattern.length; j++) {
                if (haystack[i + j] != pattern[j]) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }

    private LinkageSweep() {
    }
}
