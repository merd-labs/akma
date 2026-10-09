import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;

/** Standalone host-tool tests. All model/ELF bytes are explicit non-inference fixtures. */
public final class VerifyReleaseArtifactTest {
    private static int cases;
    private static final byte[] MODEL = "LITERTLMfixture only".getBytes(StandardCharsets.US_ASCII);
    private static final String NAME = "bundle.litertlm";
    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("akma-release-fixtures-");
        try {
            String size = String.valueOf(MODEL.length);
            String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(MODEL));
            Path model = directory.resolve("private fixture.litertlm");
            Files.write(model, MODEL);
            check(0, "--help");
            check(0, "--model", model.toString(), size, sha);
            byte[] bad = MODEL.clone(); bad[9] ^= 1;
            Files.write(model, bad);
            check(1, "--model", model.toString(), size, sha);
            Files.write(model, Arrays.copyOf(MODEL, MODEL.length - 1));
            check(1, "--model", model.toString(), size, sha);
            Files.write(model, MODEL);
            check(1, "--model", model.toString(), size, "unknown");
            check(1, "--model", directory.resolve("missing").toString(), size, sha);
            Path apk = directory.resolve("private fixture.apk");
            zip(apk, MODEL, true, true, false, false);
            check(0, "--apk", apk.toString(), NAME, size, sha);
            zip(apk, bad, true, true, false, false);
            check(1, "--apk", apk.toString(), NAME, size, sha);
            zip(apk, MODEL, false, true, false, false);
            check(1, "--apk", apk.toString(), NAME, size, sha);
            zip(apk, MODEL, true, false, false, false);
            check(1, "--apk", apk.toString(), NAME, size, sha);
            zip(apk, MODEL, true, true, true, false);
            check(1, "--apk", apk.toString(), NAME, size, sha);
            zip(apk, MODEL, true, true, false, true);
            byte[] archive = Files.readAllBytes(apk);
            byte[] from = "duplxx.litertlm".getBytes(StandardCharsets.US_ASCII);
            byte[] to = NAME.getBytes(StandardCharsets.US_ASCII);
            for (int i = 0; i <= archive.length - from.length; i++) {
                boolean match = true;
                for (int j = 0; j < from.length; j++) if (archive[i+j] != from[j]) { match = false; break; }
                if (match) System.arraycopy(to, 0, archive, i, to.length);
            }
            Files.write(apk, archive);
            check(1, "--apk", apk.toString(), NAME, size, sha);
            Files.write(apk, new byte[]{1, 2, 3});
            check(1, "--apk", apk.toString(), NAME, size, sha);
            System.out.println("PASS: " + cases + " host verifier fixture cases; real inference NOT TESTED");
        } finally {
            try (var paths = Files.walk(directory)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
    }
    private static void check(int expected, String... args) {
        ByteArrayOutputStream capture = new ByteArrayOutputStream();
        int actual = VerifyReleaseArtifact.run(args, new PrintStream(capture));
        if (actual != expected || capture.toString().contains("private fixture"))
            throw new AssertionError("Verifier fixture failed: " + cases);
        cases++;
    }
    private static void zip(Path path, byte[] model, boolean stored, boolean nativeEntry, boolean badElf, boolean duplicate) throws Exception {
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(path))) {
            entry(zip, "assets/" + NAME, model, stored);
            if (duplicate) entry(zip, "assets/duplxx.litertlm", model, stored);
            if (nativeEntry) {
                byte[] elf = new byte[64];
                elf[0] = 0x7f; elf[1] = 'E'; elf[2] = 'L'; elf[3] = 'F'; elf[4] = 2; elf[5] = 1;
                elf[18] = (byte)(badElf ? 62 : 183);
                entry(zip, "lib/arm64-v8a/liblitertlm_jni.so", elf, true);
            }
        }
    }
    private static void entry(ZipOutputStream zip, String name, byte[] bytes, boolean stored) throws Exception {
        ZipEntry entry = new ZipEntry(name);
        if (stored) {
            CRC32 crc = new CRC32(); crc.update(bytes);
            entry.setMethod(ZipEntry.STORED); entry.setSize(bytes.length); entry.setCompressedSize(bytes.length); entry.setCrc(crc.getValue());
        }
        zip.putNextEntry(entry); zip.write(bytes); zip.closeEntry();
    }
}
