import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Akma merged-manifest security guard. Single-file Java program: no dependencies beyond the JDK.
 *
 * <pre>
 *   java scripts/security/ManifestGuard.java [--policy FILE] [--release] MANIFEST.xml [MORE.xml ...]
 * </pre>
 *
 * Exit codes: 0 = every manifest passes, 1 = at least one policy violation, 2 = usage / unreadable input.
 *
 * Inspect the MERGED manifest (app/build/intermediates/merged_manifests/&lt;variant&gt;/...), not
 * app/src/main/AndroidManifest.xml: libraries add permissions and components during manifest merge.
 * The guard never edits a manifest and never removes a permission; approving something new is an explicit,
 * reviewable edit of scripts/security/manifest-policy.txt.
 */
public final class ManifestGuard {
    private static final String ANDROID_NS = "http://schemas.android.com/apk/res/android";

    /** Extra explanation for permissions that deserve a specific message. */
    private static final Map<String, String> RISKY_PERMISSIONS = new HashMap<>();
    /** Service-binding permissions that only platform-privileged feature services (accessibility etc.) declare. */
    private static final Set<String> FORBIDDEN_BIND_PERMISSIONS = new HashSet<>();
    private static final Set<String> FORBIDDEN_SERVICE_ACTIONS = new HashSet<>();

    static {
        RISKY_PERMISSIONS.put("android.permission.INTERNET",
            "network access; Akma is offline-by-design (no cloud inference). A library AAR probably added it during manifest merge: "
                + "remove it with <uses-permission android:name=\"android.permission.INTERNET\" tools:node=\"remove\"/> in the app manifest.");
        RISKY_PERMISSIONS.put("android.permission.ACCESS_NETWORK_STATE", "network state probing; usually pulled in by networking libraries.");
        RISKY_PERMISSIONS.put("android.permission.ACCESS_WIFI_STATE", "network state probing.");
        RISKY_PERMISSIONS.put("android.permission.BIND_ACCESSIBILITY_SERVICE", "AccessibilityService is forbidden for Akma (screen reading).");
        RISKY_PERMISSIONS.put("android.permission.BIND_NOTIFICATION_LISTENER_SERVICE", "notification listener is forbidden for Akma (message scraping).");
        RISKY_PERMISSIONS.put("android.permission.BIND_INPUT_METHOD", "an input method (keyboard) is out of scope and sees all typed text.");
        RISKY_PERMISSIONS.put("android.permission.READ_SMS", "SMS access is forbidden.");
        RISKY_PERMISSIONS.put("android.permission.RECEIVE_SMS", "SMS access is forbidden.");
        RISKY_PERMISSIONS.put("android.permission.READ_CONTACTS", "contacts access is not part of the manual copy/paste design.");
        RISKY_PERMISSIONS.put("android.permission.RECORD_AUDIO", "microphone access is not part of the product.");
        RISKY_PERMISSIONS.put("android.permission.CAMERA", "camera access is not part of the product.");
        RISKY_PERMISSIONS.put("android.permission.READ_EXTERNAL_STORAGE", "shared-storage access; model files must live in app-private storage.");
        RISKY_PERMISSIONS.put("android.permission.WRITE_EXTERNAL_STORAGE", "shared-storage access; model files must live in app-private storage.");
        RISKY_PERMISSIONS.put("android.permission.MANAGE_EXTERNAL_STORAGE", "all-files access is not needed for app-private model files.");
        RISKY_PERMISSIONS.put("android.permission.READ_LOGS", "reading other apps' logs is forbidden.");
        RISKY_PERMISSIONS.put("android.permission.REQUEST_INSTALL_PACKAGES", "package installation is out of scope.");
        RISKY_PERMISSIONS.put("android.permission.QUERY_ALL_PACKAGES", "package enumeration is out of scope.");
        RISKY_PERMISSIONS.put("android.permission.RECEIVE_BOOT_COMPLETED", "boot auto-start contradicts the user-started overlay design.");

        FORBIDDEN_BIND_PERMISSIONS.addAll(Arrays.asList(
            "android.permission.BIND_ACCESSIBILITY_SERVICE",
            "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE",
            "android.permission.BIND_INPUT_METHOD",
            "android.permission.BIND_AUTOFILL_SERVICE",
            "android.permission.BIND_DEVICE_ADMIN",
            "android.permission.BIND_VPN_SERVICE",
            "android.permission.BIND_CONDITION_PROVIDER_SERVICE",
            "android.permission.BIND_SCREENING_SERVICE",
            "android.permission.BIND_CARRIER_SERVICES"));
        FORBIDDEN_SERVICE_ACTIONS.addAll(Arrays.asList(
            "android.accessibilityservice.AccessibilityService",
            "android.service.notification.NotificationListenerService",
            "android.view.InputMethod",
            "android.service.autofill.AutofillService",
            "android.net.VpnService",
            "android.telecom.CallScreeningService"));
    }

    private static final class Policy {
        final Set<String> permissions = new LinkedHashSet<>();
        final Set<String> permissionSuffixes = new LinkedHashSet<>();
        final Map<String, String> exportedComponents = new HashMap<>(); // fully-qualified name -> required permission or ""
    }

    private static final class Report {
        final List<String> failures = new ArrayList<>();
        final List<String> warnings = new ArrayList<>();
        final List<String> passes = new ArrayList<>();
    }

    public static void main(String[] args) {
        System.exit(run(args));
    }

    static int run(String[] args) {
        Path policyPath = null;
        boolean release = false;
        List<Path> manifests = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.equals("--policy") && i + 1 < args.length) {
                policyPath = Paths.get(args[++i]);
            } else if (arg.equals("--release")) {
                release = true;
            } else if (arg.equals("--help") || arg.equals("-h")) {
                usage();
                return 0;
            } else if (arg.startsWith("--")) {
                System.err.println("ERROR unknown option: " + arg);
                usage();
                return 2;
            } else {
                manifests.add(Paths.get(arg));
            }
        }
        if (manifests.isEmpty()) {
            System.err.println("ERROR no manifest given.");
            usage();
            return 2;
        }
        Policy policy;
        try {
            policy = loadPolicy(resolvePolicy(policyPath));
        } catch (IOException | IllegalArgumentException error) {
            System.err.println("ERROR cannot load policy: " + error.getMessage());
            return 2;
        }
        int exit = 0;
        for (Path manifest : manifests) {
            System.out.println("== manifest: " + manifest.getFileName());
            Report report = new Report();
            try {
                check(parse(manifest), policy, release, report);
            } catch (Exception error) {
                System.err.println("ERROR cannot parse " + manifest + ": " + error.getMessage());
                return 2;
            }
            for (String line : report.passes) System.out.println("OK   " + line);
            for (String line : report.warnings) System.out.println("WARN " + line);
            for (String line : report.failures) System.out.println("FAIL " + line);
            if (!report.failures.isEmpty()) {
                exit = Math.max(exit, 1);
                System.out.println("RESULT FAIL (" + report.failures.size() + " violation(s)). Fix the merged manifest, or - only with reviewer approval - "
                    + "edit scripts/security/manifest-policy.txt.");
            } else {
                System.out.println("RESULT PASS");
            }
        }
        return exit;
    }

    private static void usage() {
        System.err.println("usage: java scripts/security/ManifestGuard.java [--policy FILE] [--release] MANIFEST.xml [MORE.xml ...]");
        System.err.println("  default policy lookup: AKMA_MANIFEST_POLICY, ./scripts/security/manifest-policy.txt, ../scripts/security/manifest-policy.txt");
    }

    // ---- policy -------------------------------------------------------------------------------------------------

    private static Path resolvePolicy(Path explicit) throws IOException {
        List<Path> candidates = new ArrayList<>();
        if (explicit != null) candidates.add(explicit);
        String env = System.getenv("AKMA_MANIFEST_POLICY");
        if (env != null && !env.isEmpty()) candidates.add(Paths.get(env));
        candidates.add(Paths.get("scripts", "security", "manifest-policy.txt"));
        candidates.add(Paths.get("..", "scripts", "security", "manifest-policy.txt"));
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) return candidate;
        }
        throw new IOException("manifest-policy.txt not found; pass --policy FILE");
    }

    private static Policy loadPolicy(Path path) throws IOException {
        Policy policy = new Policy();
        for (String raw : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            String line = raw.contains("#") ? raw.substring(0, raw.indexOf('#')) : raw;
            line = line.trim();
            if (line.isEmpty()) continue;
            String[] parts = line.split("\\s+");
            switch (parts[0]) {
                case "permission":
                    requireParts(parts, 2, line);
                    policy.permissions.add(parts[1]);
                    break;
                case "permission-suffix":
                    requireParts(parts, 2, line);
                    policy.permissionSuffixes.add(parts[1]);
                    break;
                case "exported":
                    requireParts(parts, 2, line);
                    String required = "";
                    for (int i = 2; i < parts.length; i++) {
                        if (parts[i].startsWith("requires=")) required = parts[i].substring("requires=".length());
                    }
                    policy.exportedComponents.put(parts[1], required);
                    break;
                default:
                    throw new IllegalArgumentException("unknown policy directive: " + line);
            }
        }
        return policy;
    }

    private static void requireParts(String[] parts, int min, String line) {
        if (parts.length < min) throw new IllegalArgumentException("malformed policy line: " + line);
    }

    // ---- parsing -------------------------------------------------------------------------------------------------

    private static Document parse(Path path) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        DocumentBuilder builder = factory.newDocumentBuilder();
        try (InputStream in = Files.newInputStream(path)) {
            return builder.parse(in);
        }
    }

    private static String attr(Element element, String name) {
        return element.hasAttributeNS(ANDROID_NS, name) ? element.getAttributeNS(ANDROID_NS, name) : null;
    }

    private static List<Element> children(Element parent, String tag) {
        List<Element> result = new ArrayList<>();
        NodeList nodes = parent.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node instanceof Element && ((Element) node).getTagName().equals(tag)) result.add((Element) node);
        }
        return result;
    }

    // ---- rules ---------------------------------------------------------------------------------------------------

    private static void check(Document document, Policy policy, boolean release, Report report) {
        Element manifest = document.getDocumentElement();
        String pkg = manifest.getAttribute("package");
        if (!"manifest".equals(manifest.getTagName()) || pkg.isEmpty()) {
            report.failures.add("[structure] root element is not <manifest package=...>; is this really a merged manifest?");
            return;
        }
        if (attr(manifest, "sharedUserId") != null) {
            report.failures.add("[shared-uid] android:sharedUserId is set; Akma must not share a UID with other apps.");
        }

        checkPermissions(manifest, pkg, policy, report);
        checkCustomPermissionDefinitions(manifest, report);

        List<Element> applications = children(manifest, "application");
        if (applications.size() != 1) {
            report.failures.add("[structure] expected exactly one <application>, found " + applications.size() + ".");
            return;
        }
        Element application = applications.get(0);
        checkApplication(application, release, report);
        for (String tag : new String[] {"activity", "activity-alias", "service", "receiver", "provider"}) {
            for (Element component : children(application, tag)) {
                checkComponent(tag, component, pkg, policy, report);
            }
        }
    }

    private static void checkPermissions(Element manifest, String pkg, Policy policy, Report report) {
        int approved = 0;
        for (String tag : new String[] {"uses-permission", "uses-permission-sdk-23", "uses-permission-sdk-m"}) {
            for (Element element : children(manifest, tag)) {
                String name = attr(element, "name");
                if (name == null || name.isEmpty()) {
                    report.failures.add("[permission] <" + tag + "> without android:name.");
                    continue;
                }
                if (isApprovedPermission(name, pkg, policy)) {
                    approved++;
                    continue;
                }
                String hint = RISKY_PERMISSIONS.get(name);
                report.failures.add("[permission] unapproved permission " + name + (hint != null ? " - " + hint : "")
                    + " To allow it deliberately, add 'permission " + name + "' to scripts/security/manifest-policy.txt with reviewer approval.");
            }
        }
        report.passes.add("[permission] " + approved + " approved permission(s) checked.");
    }

    private static boolean isApprovedPermission(String name, String pkg, Policy policy) {
        if (policy.permissions.contains(name)) return true;
        for (String suffix : policy.permissionSuffixes) {
            if (name.equals(pkg + suffix)) return true;
        }
        return false;
    }

    private static void checkCustomPermissionDefinitions(Element manifest, Report report) {
        for (Element definition : children(manifest, "permission")) {
            String level = attr(definition, "protectionLevel");
            String name = attr(definition, "name");
            if (level == null || !level.contains("signature")) {
                report.failures.add("[custom-permission] <permission " + name + "> has protectionLevel '" + level
                    + "'; custom permissions must be signature-level so other apps cannot hold them.");
            }
        }
    }

    private static void checkApplication(Element application, boolean release, Report report) {
        String allowBackup = attr(application, "allowBackup");
        if (!"false".equals(allowBackup)) {
            report.failures.add("[backup] android:allowBackup is '" + allowBackup + "'; set android:allowBackup=\"false\" so messages/models are not backed up.");
        }
        if ("true".equals(attr(application, "usesCleartextTraffic"))) {
            report.failures.add("[cleartext] android:usesCleartextTraffic=\"true\"; Akma must not allow plaintext network traffic.");
        }
        if ("true".equals(attr(application, "debuggable"))) {
            String message = "[debuggable] android:debuggable=\"true\": acceptable for debug builds only; the submitted/demo APK must be non-debuggable (run-as can read app-private data).";
            if (release) report.failures.add(message); else report.warnings.add(message);
        }
        if ("true".equals(attr(application, "testOnly"))) {
            String message = "[testOnly] android:testOnly=\"true\" prevents normal installation of release artifacts.";
            if (release) report.failures.add(message); else report.warnings.add(message);
        }
    }

    private static void checkComponent(String tag, Element component, String pkg, Policy policy, Report report) {
        String rawName = attr(component, "name");
        String name = qualify(rawName, pkg);
        String label = tag + " " + name;
        String permission = attr(component, "permission");
        List<Element> filters = children(component, "intent-filter");
        Set<String> actions = new LinkedHashSet<>();
        for (Element filter : filters) {
            for (Element action : children(filter, "action")) {
                String actionName = attr(action, "name");
                if (actionName != null) actions.add(actionName);
            }
        }

        if (permission != null && FORBIDDEN_BIND_PERMISSIONS.contains(permission)) {
            report.failures.add("[platform-service] " + label + " is protected by " + permission
                + ", i.e. it is an accessibility/notification-listener/input-method style platform service. Akma must not ship one.");
        }
        for (String action : actions) {
            if (FORBIDDEN_SERVICE_ACTIONS.contains(action)) {
                report.failures.add("[platform-service] " + label + " registers action " + action + ". Akma must not ship an accessibility, notification-listener, input-method, autofill or VPN service.");
            }
        }
        for (Element meta : children(component, "meta-data")) {
            String metaName = attr(meta, "name");
            if ("android.accessibilityservice".equals(metaName) || "android.view.im".equals(metaName)
                || "android.service.notification.default_filter_types".equals(metaName)) {
                report.failures.add("[platform-service] " + label + " carries meta-data " + metaName + " used by platform service declarations.");
            }
        }

        String exportedAttr = attr(component, "exported");
        boolean exported;
        String why;
        if (exportedAttr != null) {
            exported = "true".equals(exportedAttr);
            why = "android:exported=\"" + exportedAttr + "\"";
        } else if (tag.equals("provider")) {
            exported = false;
            why = "provider default (not exported)";
        } else {
            exported = !filters.isEmpty();
            why = filters.isEmpty() ? "no intent-filter" : "implicitly exported because it has an intent-filter and no android:exported";
        }
        if (!exported) {
            report.passes.add("[component] " + label + " not exported (" + why + ").");
            return;
        }

        String required = policy.exportedComponents.get(name);
        if (required == null) {
            report.failures.add("[exported] " + label + " is exported (" + why + ") but not on the approved list. "
                + "Set android:exported=\"false\" (or remove the intent-filter), or add 'exported " + name
                + "' to scripts/security/manifest-policy.txt with reviewer approval.");
            return;
        }
        if (!required.isEmpty() && !required.equals(permission)) {
            report.failures.add("[exported] " + label + " is approved only when guarded by android:permission=\"" + required
                + "\" but the merged manifest has " + (permission == null ? "no permission" : "\"" + permission + "\"") + ".");
            return;
        }
        if (tag.equals("provider") && permission == null && attr(component, "readPermission") == null
            && attr(component, "writePermission") == null && required.isEmpty()) {
            report.failures.add("[exported] provider " + name + " is exported without any permission.");
            return;
        }
        report.passes.add("[component] " + label + " exported and on the approved list" + (required.isEmpty() ? "" : " (guarded by " + required + ")") + ".");
    }

    private static String qualify(String name, String pkg) {
        if (name == null) return "<unnamed>";
        if (name.startsWith(".")) return pkg + name;
        if (!name.contains(".")) return pkg + "." + name;
        return name;
    }
}
