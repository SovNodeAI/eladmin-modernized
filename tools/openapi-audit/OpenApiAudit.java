import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class OpenApiAudit {

    private static final int EXPECTED_TAGS = 26;
    private static final int EXPECTED_HIDDEN = 3;
    private static final int EXPECTED_OPERATIONS = 141;
    private static final int EXPECTED_SCHEMAS = 414;
    private static final int EXPECTED_REQUEST_BODIES = 1;

    private record Count(String label, Pattern pattern, int expected) {}

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("Usage: java tools/openapi-audit/OpenApiAudit.java <repoRoot>");
            System.exit(2);
        }

        Path root = Path.of(args[0]).toAbsolutePath().normalize();
        if (!Files.isRegularFile(root.resolve("pom.xml"))) {
            fail("repository root does not contain pom.xml: " + root);
        }

        List<Path> sources = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(Files::isRegularFile)
                    .filter(OpenApiAudit::isSource)
                    .filter(path -> slash(path).contains("/src/main/"))
                    .filter(path -> !slash(path).contains("/target/"))
                    .filter(path -> !slash(path).contains("/.git/"))
                    .forEach(sources::add);
        }
        sources.sort(Comparator.comparing(path -> slash(root.relativize(path))));

        StringBuilder all = new StringBuilder();
        List<String> legacy = new ArrayList<>();
        Pattern legacyPattern = Pattern.compile("io\\.swagger\\.annotations|@Api(?:Operation|ModelProperty|Param)?\\b");
        for (Path source : sources) {
            String text = new String(Files.readAllBytes(source), StandardCharsets.UTF_8);
            all.append(text).append('\n');
            Matcher matcher = legacyPattern.matcher(text);
            if (matcher.find()) {
                legacy.add(slash(root.relativize(source)));
            }
        }
        if (!legacy.isEmpty()) {
            fail("legacy Swagger 1 references remain in " + legacy.size() + " files: "
                    + String.join(", ", legacy.subList(0, Math.min(legacy.size(), 8))));
        }

        List<Count> counts = List.of(
                new Count("@Tag", Pattern.compile("(?m)^\\s*@Tag\\("), EXPECTED_TAGS),
                new Count("@Hidden", Pattern.compile("(?m)^\\s*@Hidden\\b"), EXPECTED_HIDDEN),
                new Count("@Operation", Pattern.compile("(?m)^\\s*@Operation\\("), EXPECTED_OPERATIONS),
                new Count("@Schema", Pattern.compile("(?m)^\\s*@Schema\\("), EXPECTED_SCHEMAS),
                new Count("OpenAPI request body", Pattern.compile("@io\\.swagger\\.v3\\.oas\\.annotations\\.parameters\\.RequestBody\\("), EXPECTED_REQUEST_BODIES));
        for (Count count : counts) {
            int actual = occurrences(count.pattern(), all);
            if (actual != count.expected()) {
                fail(count.label() + " count is " + actual + ", expected exactly " + count.expected());
            }
        }

        String pom = Files.readString(root.resolve("pom.xml"), StandardCharsets.UTF_8);
        require(pom, "<groupId>org.springdoc</groupId>", "direct Springdoc dependency");
        require(pom, "<artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>", "Springdoc WebMVC UI starter");
        require(pom, "<version>3.1.0</version>", "Springdoc 3.1.0 Boot 4 compatible version");
        if (pom.contains("<groupId>io.swagger</groupId>") || pom.contains("<artifactId>swagger-annotations</artifactId>")) {
            fail("legacy Swagger annotations dependency remains in pom.xml");
        }

        Path security = root.resolve("eladmin-system/src/main/java/me/zhengjie/modules/security/config/SpringSecurityConfig.java");
        String config = Files.readString(security, StandardCharsets.UTF_8);
        for (String route : List.of("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/doc.html", "/webjars/**")) {
            require(config, "\"" + route + "\"", "security permit rule for " + route);
        }

        String application = Files.readString(
                root.resolve("eladmin-system/src/main/resources/config/application.yml"), StandardCharsets.UTF_8);
        require(application, "path: /doc.html", "preserved documentation entry point");

        System.out.println("OPENAPI AUDIT OK: " + sources.size() + " source files checked; "
                + EXPECTED_TAGS + " tags, " + EXPECTED_HIDDEN + " hidden (1 class level AppRun, 2 method level Alipay callbacks), "
                + EXPECTED_OPERATIONS + " operations, " + EXPECTED_SCHEMAS + " schemas, "
                + EXPECTED_REQUEST_BODIES + " request body description");
    }

    private static boolean isSource(Path path) {
        String name = path.getFileName().toString();
        return name.endsWith(".java") || name.endsWith(".ftl");
    }

    private static int occurrences(Pattern pattern, CharSequence text) {
        int count = 0;
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    private static void require(String text, String expected, String label) {
        if (!text.contains(expected)) {
            fail("missing " + label + ": " + expected);
        }
    }

    private static void fail(String message) {
        System.err.println("OPENAPI AUDIT FAILED: " + message);
        System.exit(1);
    }

    private static String slash(Path path) {
        return path.toString().replace('\\', '/');
    }
}
