import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;

/** JDK 17 only. Streams artifacts; prints no paths, identifiers, messages or model content. */
public final class VerifyReleaseArtifact {
    public static void main(String[] args) { System.exit(run(args, System.out)); }

    static int run(String[] args, PrintStream output) {
        if (args.length == 1 && args[0].equals("--help")) {
            output.println("Usage: --model FILE EXPECTED_BYTES EXPECTED_SHA256");
            output.println("       --apk FILE MODEL_FILENAME EXPECTED_BYTES EXPECTED_SHA256");
            output.println("Use trusted metadata. APK mode requires an uncompressed model and ARM64 LiteRT JNI library.");
            return 0;
        }
        try {
            if (Runtime.version().feature() != 17) throw new Failure("JDK 17 required");
            boolean apk = args.length == 5 && args[0].equals("--apk");
            if (!apk && !(args.length == 4 && args[0].equals("--model"))) throw new Failure("invalid arguments");
            String bytesArg = args[apk ? 3 : 2];
            String expectedHash = args[apk ? 4 : 3];
            if (!bytesArg.matches("[1-9][0-9]*") || !expectedHash.matches("[a-f0-9]{64}"))
                throw new Failure("invalid trusted metadata");
            long expectedBytes = Long.parseLong(bytesArg);
            if (expectedBytes < 8) throw new Failure("invalid trusted metadata");
            Path file = Path.of(args[1]);
            if (!Files.isRegularFile(file) || !Files.isReadable(file)) throw new Failure("missing or unreadable artifact");
            if (apk) {
                String name = args[2];
                if (!name.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,127}\\.litertlm") || name.contains(".."))
                    throw new Failure("invalid model filename");
                try (ZipFile zip = new ZipFile(file.toFile())) {
                    ZipEntry model = unique(zip, "assets/" + name);
                    if (model.getMethod() != ZipEntry.STORED) throw new Failure("model must be uncompressed");
                    if (model.getSize() != expectedBytes) throw new Failure("model size mismatch");
                    try (InputStream input = zip.getInputStream(model)) { verifyModel(input, expectedBytes, expectedHash); }
                    ZipEntry nativeLibrary = unique(zip, "lib/arm64-v8a/liblitertlm_jni.so");
                    try (InputStream input = zip.getInputStream(nativeLibrary)) {
                        byte[] elf = input.readNBytes(20);
                        if (elf.length != 20 || elf[0] != 0x7f || elf[1] != 'E' || elf[2] != 'L' || elf[3] != 'F'
                            || elf[4] != 2 || elf[5] != 1 || (elf[18] & 255) != 183 || elf[19] != 0)
                            throw new Failure("JNI library is not little-endian ARM64 ELF");
                    }
                }
                output.println("PASS: APK model size, header, SHA-256 and ARM64 ELF packaging");
                output.println("apk_bytes=" + Files.size(file));
                try (InputStream input = Files.newInputStream(file)) { output.println("apk_sha256=" + hash(input)); }
                output.println("Application ID, minSdk, signing, native loading and inference NOT TESTED by this verifier");
            } else {
                try (InputStream input = Files.newInputStream(file)) { verifyModel(input, expectedBytes, expectedHash); }
                output.println("PASS: model size, header and SHA-256; native inference NOT TESTED");
            }
            return 0;
        } catch (Failure failure) {
            output.println("FAIL: " + failure.getMessage());
        } catch (Exception failure) {
            output.println("FAIL: artifact or metadata read failed (details redacted)");
        }
        return 1;
    }

    private static ZipEntry unique(ZipFile zip, String name) throws Failure {
        ZipEntry found = null;
        for (Enumeration<? extends ZipEntry> entries = zip.entries(); entries.hasMoreElements();) {
            ZipEntry entry = entries.nextElement();
            if (!entry.getName().equals(name)) continue;
            if (found != null || entry.isDirectory()) throw new Failure("duplicate or invalid required APK entry");
            found = entry;
        }
        if (found == null) throw new Failure("required APK entry missing");
        return found;
    }

    private static void verifyModel(InputStream input, long expectedBytes, String expectedHash) throws Exception {
        byte[] header = input.readNBytes(8);
        if (!Arrays.equals(header, "LITERTLM".getBytes(java.nio.charset.StandardCharsets.US_ASCII)))
            throw new Failure("LiteRT-LM header mismatch");
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(header);
        long total = header.length;
        byte[] buffer = new byte[64 * 1024];
        for (int count; (count = input.read(buffer)) != -1;) {
            if (count > expectedBytes - total) throw new Failure("model size mismatch");
            total += count;
            digest.update(buffer, 0, count);
        }
        if (total != expectedBytes) throw new Failure("model size mismatch");
        if (!HexFormat.of().formatHex(digest.digest()).equals(expectedHash)) throw new Failure("model SHA-256 mismatch");
    }

    private static String hash(InputStream input) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] buffer = new byte[64 * 1024];
        for (int count; (count = input.read(buffer)) != -1;) digest.update(buffer, 0, count);
        return HexFormat.of().formatHex(digest.digest());
    }

    private static final class Failure extends Exception {
        Failure(String safeMessage) { super(safeMessage); }
    }
}
