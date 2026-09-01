import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class EndpointCensus {

    private static final int EXPECTED_TOTAL = 139;
    private static final int EXPECTED_DIRECT_VERB = 132;
    private static final int EXPECTED_ANONYMOUS_COMPOSED = 6;
    private static final int EXPECTED_BARE_REQUEST_MAPPING = 1;
    private static final String UNKNOWN = "UNKNOWN";

    private static final Pattern MAPPING_PATTERN = Pattern.compile(
            "\\b(Anonymous)?(GetMapping|PostMapping|PutMapping|DeleteMapping|PatchMapping|RequestMapping)\\b");
    private static final Pattern STRING_LITERAL = Pattern.compile("\"((?:\\\\.|[^\"\\\\])*)\"");
    private static final Pattern REQUEST_METHOD = Pattern.compile("\\bRequestMethod\\.([A-Z]+)");
    private static final Pattern PACKAGE_PATTERN = Pattern.compile("\\bpackage\\s+([A-Za-z_$][A-Za-z0-9_$.]*)\\s*;");

    private record Row(String kind, String method, String route, String simple, String fqn,
                       String handler, String path, int line) {}

    private record Args(String text, int end) {}

    private static final class UsageError extends RuntimeException {
        UsageError(String message) { super(message); }
    }

    private static final class CensusFailure extends RuntimeException {
        CensusFailure(String message) { super(message); }
    }

    public static void main(String[] args) {
        try {
            System.exit(run(args));
        } catch (UsageError e) {
            System.err.println("USAGE ERROR: " + e.getMessage());
            System.err.println("Usage: java tools/endpoint-census/EndpointCensus.java <repoRoot> <censusFile> [generate|verify]");
            System.exit(2);
        } catch (CensusFailure e) {
            System.err.println("CENSUS FAILED: " + e.getMessage());
            System.exit(1);
        } catch (Exception e) {
            System.err.println("UNEXPECTED ERROR: " + e);
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static int run(String[] args) throws Exception {
        if (args.length < 2 || args.length > 3) {
            throw new UsageError("expected 2 or 3 arguments, got " + args.length);
        }
        Path repoRoot = Paths.get(args[0]).toAbsolutePath().normalize();
        Path censusFile = Paths.get(args[1]).toAbsolutePath().normalize();
        String mode = args.length == 3 ? args[2] : "generate";
        if (!mode.equals("generate") && !mode.equals("verify")) {
            throw new UsageError("mode must be 'generate' or 'verify': " + mode);
        }
        if (!Files.isDirectory(repoRoot)) {
            throw new CensusFailure("repoRoot is not a directory: " + repoRoot);
        }
        if (!Files.isRegularFile(repoRoot.resolve("pom.xml"))) {
            throw new CensusFailure("repoRoot does not look like this repository (no pom.xml): " + repoRoot);
        }

        List<Row> rows = scan(repoRoot);
        byte[] body = buildCensus(rows).getBytes(StandardCharsets.UTF_8);
        String sha256 = sha256Hex(body);
        byte[] checksumBytes = (sha256 + "\n").getBytes(StandardCharsets.UTF_8);

        if (mode.equals("generate")) {
            Path parent = censusFile.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.write(censusFile, body);
            Files.write(checksumPath(censusFile), checksumBytes);
            System.out.println("GENERATE OK: " + rows.size() + " endpoint records -> "
                    + slash(repoRoot.relativize(censusFile).toString()));
            printBreakdown(rows);
            System.out.println("SHA-256: " + sha256);
            return 0;
        }

        if (!Files.isRegularFile(censusFile)) {
            throw new CensusFailure("stored census artifact missing: " + censusFile);
        }
        Path checksum = checksumPath(censusFile);
        if (!Files.isRegularFile(checksum)) {
            throw new CensusFailure("stored checksum file missing: " + checksum);
        }
        byte[] stored = Files.readAllBytes(censusFile);
        byte[] storedChecksum = Files.readAllBytes(checksum);
        if (!Arrays.equals(stored, body)) {
            int diff = firstDiffIndex(stored, body);
            throw new CensusFailure("artifact drift at byte offset " + diff + " (line " + byteToLine(stored, diff)
                    + "); regenerate and re-review before replacing the artifact");
        }
        if (!Arrays.equals(storedChecksum, checksumBytes)) {
            throw new CensusFailure("checksum file drift: stored="
                    + new String(storedChecksum, StandardCharsets.UTF_8).trim()
                    + " expected=" + sha256);
        }
        System.out.println("VERIFY OK: " + rows.size() + " endpoint records match byte-for-byte; SHA-256 " + sha256);
        printBreakdown(rows);
        return 0;
    }

    private static void printBreakdown(List<Row> rows) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Row r : rows) {
            counts.merge(r.kind(), 1, Integer::sum);
        }
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            System.out.println("  " + e.getKey() + " = " + e.getValue());
        }
    }

    private static List<Row> scan(Path repoRoot) throws Exception {
        List<Path> files = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(repoRoot)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".java"))
                    .filter(p -> {
                        String s = slash(p.toString());
                        return s.contains("/src/main/java/") && !s.contains("/target/") && !s.contains("/.git/");
                    })
                    .forEach(files::add);
        }
        files.sort(Comparator.comparing(p -> slash(repoRoot.relativize(p).toString())));

        List<Row> rows = new ArrayList<>();
        for (Path file : files) {
            scanFile(repoRoot, file, rows);
        }
        checkInvariants(rows);
        rows.sort(Comparator.comparing(Row::route)
                .thenComparing(Row::method)
                .thenComparing(Row::simple)
                .thenComparing(Row::handler)
                .thenComparing(Row::path)
                .thenComparingInt(Row::line));
        return rows;
    }

    private static void scanFile(Path repoRoot, Path file, List<Row> rows) throws Exception {
        String relPath = slash(repoRoot.relativize(file).toString());
        String src = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        String masked = maskComments(src);

        int classKw = findClassKeyword(masked);
        if (classKw < 0) {
            return;
        }
        int nameStart = skipWs(masked, classKw + "class".length());
        int nameEnd = identEnd(masked, nameStart);
        String simple = masked.substring(nameStart, nameEnd);
        int classBodyOpen = indexOfLiteralAware(masked, nameEnd, masked.length(), '{');
        if (classBodyOpen < 0) {
            throw new CensusFailure(relPath + ": class body not found for " + simple);
        }
        int classBodyClose = matchingBrace(masked, classBodyOpen);
        if (classBodyClose < 0) {
            throw new CensusFailure(relPath + ": unbalanced class braces in " + simple);
        }
        if (!hasAnnotation(masked, 0, classBodyOpen, "RestController")) {
            return;
        }

        String pkg = "";
        Matcher packageMatcher = PACKAGE_PATTERN.matcher(masked);
        if (packageMatcher.find()) {
            pkg = packageMatcher.group(1);
        }
        String fqn = pkg.isEmpty() ? simple : pkg + "." + simple;

        String prefix = "";
        Matcher headerMatcher = MAPPING_PATTERN.matcher(masked);
        headerMatcher.region(0, classBodyOpen);
        while (headerMatcher.find()) {
            if (masked.charAt(headerMatcher.start() - 1) != '@') {
                continue;
            }
            if (!headerMatcher.group(2).equals("RequestMapping")) {
                continue;
            }
            Args args = parseArgs(masked, headerMatcher.end(), classBodyOpen);
            List<String> paths = extractPaths(args.text());
            if (paths.size() > 1) {
                throw new CensusFailure(relPath + ": class-level @RequestMapping declares multiple paths; not supported");
            }
            prefix = paths.get(0);
            break;
        }

        Matcher mappingMatcher = MAPPING_PATTERN.matcher(masked);
        mappingMatcher.region(classBodyOpen + 1, classBodyClose);
        while (mappingMatcher.find()) {
            if (masked.charAt(mappingMatcher.start() - 1) != '@') {
                continue;
            }
            boolean anonymous = mappingMatcher.group(1) != null;
            String name = mappingMatcher.group(2);
            int annoStart = mappingMatcher.start() - 1;
            int line = 1 + countNewlines(masked, annoStart);
            Args args = parseArgs(masked, mappingMatcher.end(), classBodyClose);
            if (name.equals("RequestMapping")) {
                List<String> verbs = requestMappingVerbs(args.text(), relPath, line);
                for (String methodPath : extractPaths(args.text())) {
                    for (String verb : verbs) {
                        String kind = verb.equals(UNKNOWN) ? "bare_request_mapping" : "direct_verb";
                        rows.add(buildRow(kind, verb, prefix, methodPath, simple, fqn, masked,
                                args.end(), classBodyClose, relPath, line));
                    }
                }
            } else {
                String verb = name.substring(0, name.length() - "Mapping".length()).toUpperCase(Locale.ROOT);
                String kind = anonymous ? "anonymous_composed" : "direct_verb";
                for (String methodPath : extractPaths(args.text())) {
                    rows.add(buildRow(kind, verb, prefix, methodPath, simple, fqn, masked,
                            args.end(), classBodyClose, relPath, line));
                }
            }
        }
    }

    private static Row buildRow(String kind, String verb, String prefix, String methodPath, String simple,
                                String fqn, String masked, int from, int stop, String relPath, int line) {
        String handler = handlerName(masked, from, stop);
        if (handler == null || handler.isEmpty() || !Character.isJavaIdentifierStart(handler.charAt(0))) {
            throw new CensusFailure(relPath + ":" + line + ": could not resolve handler method name after @"
                    + (kind.equals("anonymous_composed") ? "Anonymous" : "") + verb.toLowerCase(Locale.ROOT)
                    + (verb.equals(UNKNOWN) ? "RequestMapping" : "Mapping"));
        }
        return new Row(kind, verb, normalizeRoute(prefix, methodPath), simple, fqn, handler, relPath, line);
    }

    private static List<String> requestMappingVerbs(String argsText, String relPath, int line) {
        if (argsText == null) {
            return List.of(UNKNOWN);
        }
        int attr = findAttrStart(argsText, "method");
        if (attr < 0) {
            return List.of(UNKNOWN);
        }
        String span = attributeValueSpan(argsText, attr);
        List<String> verbs = new ArrayList<>();
        Matcher m = REQUEST_METHOD.matcher(span);
        while (m.find()) {
            if (!verbs.contains(m.group(1))) {
                verbs.add(m.group(1));
            }
        }
        if (verbs.isEmpty()) {
            throw new CensusFailure(relPath + ":" + line + ": @RequestMapping has a method attribute but no RequestMethod.* constant was parsed");
        }
        return verbs;
    }

    private static List<String> extractPaths(String argsText) {
        if (argsText == null) {
            return List.of("");
        }
        int attr = findAttrStart(argsText, "value");
        if (attr < 0) {
            attr = findAttrStart(argsText, "path");
        }
        String span;
        if (attr >= 0) {
            span = attributeValueSpan(argsText, attr);
        } else if (hasTopLevelEquals(argsText)) {
            return List.of("");
        } else {
            span = argsText;
        }
        List<String> paths = new ArrayList<>();
        Matcher m = STRING_LITERAL.matcher(span);
        while (m.find()) {
            paths.add(unescape(m.group(1)));
        }
        return paths.isEmpty() ? List.of("") : paths;
    }

    private static String normalizeRoute(String prefix, String methodPath) {
        // 与 Spring 的 PatternsRequestCondition.combine 语义对齐：任一为空取另一方；
        // 拼接处保证恰好一个斜杠；其余情况中间补斜杠。此前纯字符串拼接把
        // "/api/generator" + "sync" 记成 /api/generatorsync，而 Spring 实际服务
        // 的是 /api/generator/sync。
        String left = prefix == null ? "" : prefix;
        String right = methodPath == null ? "" : methodPath;
        String route;
        if (right.isEmpty()) {
            route = left;
        } else if (left.isEmpty()) {
            route = right;
        } else if (left.endsWith("/") || right.startsWith("/")) {
            route = left + right;
        } else {
            route = left + "/" + right;
        }
        while (route.contains("//")) {
            route = route.replace("//", "/");
        }
        if (route.isEmpty()) {
            return "/";
        }
        if (!route.startsWith("/")) {
            route = "/" + route;
        }
        return route;
    }

    private static String handlerName(String masked, int from, int stop) {
        int depth = 0;
        int i = from;
        int lastTopParen = -1;
        while (i < stop) {
            char ch = masked.charAt(i);
            if (ch == '"') {
                i = skipString(masked, i);
                continue;
            }
            if (ch == '\'') {
                i = skipCharLiteral(masked, i);
                continue;
            }
            if (ch == '(') {
                if (depth == 0) {
                    lastTopParen = i;
                }
                depth++;
            } else if (ch == ')') {
                depth--;
            } else if (depth == 0 && (ch == '{' || ch == ';')) {
                break;
            }
            i++;
        }
        if (lastTopParen < 0) {
            return null;
        }
        int end = lastTopParen;
        while (end > from && Character.isWhitespace(masked.charAt(end - 1))) {
            end--;
        }
        int start = end;
        while (start > from && Character.isJavaIdentifierPart(masked.charAt(start - 1))) {
            start--;
        }
        return masked.substring(start, end);
    }

    private static Args parseArgs(String masked, int from, int stop) {
        int i = skipWs(masked, from);
        if (i >= stop || masked.charAt(i) != '(') {
            return new Args(null, from);
        }
        int close = matchingParen(masked, i);
        if (close < 0) {
            throw new CensusFailure("unbalanced annotation parentheses");
        }
        return new Args(masked.substring(i + 1, close), close + 1);
    }

    private static int findAttrStart(String argsText, String attr) {
        int depth = 0;
        int i = 0;
        while (i < argsText.length()) {
            char ch = argsText.charAt(i);
            if (ch == '"') {
                i = skipString(argsText, i);
                continue;
            }
            if (ch == '\'') {
                i = skipCharLiteral(argsText, i);
                continue;
            }
            if (ch == '(' || ch == '{') {
                depth++;
            } else if (ch == ')' || ch == '}') {
                depth--;
            } else if (depth == 0 && argsText.startsWith(attr, i)
                    && (i == 0 || !Character.isJavaIdentifierPart(argsText.charAt(i - 1)))) {
                int j = skipWs(argsText, i + attr.length());
                if (j < argsText.length() && argsText.charAt(j) == '=') {
                    return j + 1;
                }
            }
            i++;
        }
        return -1;
    }

    private static String attributeValueSpan(String argsText, int from) {
        int depth = 0;
        int i = from;
        while (i < argsText.length()) {
            char ch = argsText.charAt(i);
            if (ch == '"') {
                i = skipString(argsText, i);
                continue;
            }
            if (ch == '\'') {
                i = skipCharLiteral(argsText, i);
                continue;
            }
            if (ch == '(' || ch == '{') {
                depth++;
            } else if (ch == ')' || ch == '}') {
                depth--;
            } else if (ch == ',' && depth <= 0) {
                break;
            }
            i++;
        }
        return argsText.substring(from, i);
    }

    private static boolean hasTopLevelEquals(String argsText) {
        int depth = 0;
        int i = 0;
        while (i < argsText.length()) {
            char ch = argsText.charAt(i);
            if (ch == '"') {
                i = skipString(argsText, i);
                continue;
            }
            if (ch == '\'') {
                i = skipCharLiteral(argsText, i);
                continue;
            }
            if (ch == '(' || ch == '{') {
                depth++;
            } else if (ch == ')' || ch == '}') {
                depth--;
            } else if (ch == '=' && depth == 0) {
                return true;
            }
            i++;
        }
        return false;
    }

    private static String unescape(String raw) {
        StringBuilder out = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char ch = raw.charAt(i);
            if (ch == '\\' && i + 1 < raw.length()) {
                i++;
                char esc = raw.charAt(i);
                out.append(switch (esc) {
                    case 'n' -> '\n';
                    case 't' -> '\t';
                    case 'r' -> '\r';
                    default -> esc;
                });
            } else {
                out.append(ch);
            }
        }
        return out.toString();
    }

    private static String maskComments(String src) {
        char[] c = src.toCharArray();
        int n = c.length;
        StringBuilder out = new StringBuilder(n);
        int i = 0;
        while (i < n) {
            char ch = c[i];
            if (ch == '/' && i + 1 < n && c[i + 1] == '/') {
                while (i < n && c[i] != '\n') {
                    out.append(' ');
                    i++;
                }
            } else if (ch == '/' && i + 1 < n && c[i + 1] == '*') {
                out.append("  ");
                i += 2;
                while (i < n) {
                    if (c[i] == '*' && i + 1 < n && c[i + 1] == '/') {
                        out.append("  ");
                        i += 2;
                        break;
                    }
                    out.append(c[i] == '\n' ? '\n' : ' ');
                    i++;
                }
            } else if (ch == '"') {
                int end = isTextBlock(c, i) ? skipTextBlock(c, i) : skipString(c, i);
                out.append(src, i, end);
                i = end;
            } else if (ch == '\'') {
                int end = skipCharLiteral(c, i);
                out.append(src, i, end);
                i = end;
            } else {
                out.append(ch);
                i++;
            }
        }
        return out.toString();
    }

    private static boolean isTextBlock(char[] c, int i) {
        return i + 2 < c.length && c[i + 1] == '"' && c[i + 2] == '"';
    }

    private static int skipTextBlock(char[] c, int i) {
        for (int j = i + 3; j + 2 < c.length; j++) {
            if (c[j] == '"' && c[j + 1] == '"' && c[j + 2] == '"') {
                return j + 3;
            }
        }
        return c.length;
    }

    private static int skipString(String s, int i) {
        int n = s.length();
        i++;
        while (i < n) {
            char ch = s.charAt(i);
            if (ch == '\\') {
                i += 2;
                continue;
            }
            if (ch == '"') {
                return i + 1;
            }
            i++;
        }
        return n;
    }

    private static int skipString(char[] c, int i) {
        int n = c.length;
        i++;
        while (i < n) {
            char ch = c[i];
            if (ch == '\\') {
                i += 2;
                continue;
            }
            if (ch == '"') {
                return i + 1;
            }
            i++;
        }
        return n;
    }

    private static int skipCharLiteral(String s, int i) {
        int n = s.length();
        i++;
        if (i < n && s.charAt(i) == '\\') {
            i += 2;
        } else {
            i++;
        }
        if (i < n && s.charAt(i) == '\'') {
            i++;
        }
        return Math.min(i, n);
    }

    private static int skipCharLiteral(char[] c, int i) {
        int n = c.length;
        i++;
        if (i < n && c[i] == '\\') {
            i += 2;
        } else {
            i++;
        }
        if (i < n && c[i] == '\'') {
            i++;
        }
        return Math.min(i, n);
    }

    private static int findClassKeyword(String masked) {
        int i = 0;
        while (i < masked.length()) {
            char ch = masked.charAt(i);
            if (ch == '"') {
                i = skipString(masked, i);
                continue;
            }
            if (ch == '\'') {
                i = skipCharLiteral(masked, i);
                continue;
            }
            if (ch == 'c' && matchWord(masked, i, "class")) {
                return i;
            }
            i++;
        }
        return -1;
    }

    private static boolean matchWord(String s, int at, String word) {
        if (!s.startsWith(word, at)) {
            return false;
        }
        int before = at - 1;
        int after = at + word.length();
        if (before >= 0 && Character.isJavaIdentifierPart(s.charAt(before))) {
            return false;
        }
        return after >= s.length() || !Character.isJavaIdentifierPart(s.charAt(after));
    }

    private static boolean hasAnnotation(String masked, int from, int to, String name) {
        Matcher m = Pattern.compile("\\b" + name + "\\b").matcher(masked);
        m.region(Math.max(1, from), to);
        while (m.find()) {
            if (masked.charAt(m.start() - 1) == '@') {
                return true;
            }
        }
        return false;
    }

    private static int matchingBrace(String s, int open) {
        int depth = 0;
        int i = open;
        while (i < s.length()) {
            char ch = s.charAt(i);
            if (ch == '"') {
                i = isTextBlock(s.toCharArray(), i) ? skipTextBlock(s.toCharArray(), i) : skipString(s, i);
                continue;
            }
            if (ch == '\'') {
                i = skipCharLiteral(s, i);
                continue;
            }
            if (ch == '{') {
                depth++;
            } else if (ch == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
            i++;
        }
        return -1;
    }

    private static int matchingParen(String s, int open) {
        int depth = 0;
        int i = open;
        while (i < s.length()) {
            char ch = s.charAt(i);
            if (ch == '"') {
                i = skipString(s, i);
                continue;
            }
            if (ch == '\'') {
                i = skipCharLiteral(s, i);
                continue;
            }
            if (ch == '(') {
                depth++;
            } else if (ch == ')') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
            i++;
        }
        return -1;
    }

    private static int indexOfLiteralAware(String s, int from, int stop, char target) {
        int i = from;
        while (i < stop) {
            char ch = s.charAt(i);
            if (ch == '"') {
                i = skipString(s, i);
                continue;
            }
            if (ch == '\'') {
                i = skipCharLiteral(s, i);
                continue;
            }
            if (ch == target) {
                return i;
            }
            i++;
        }
        return -1;
    }

    private static int skipWs(String s, int i) {
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
            i++;
        }
        return i;
    }

    private static int identEnd(String s, int i) {
        while (i < s.length() && Character.isJavaIdentifierPart(s.charAt(i))) {
            i++;
        }
        return i;
    }

    private static int countNewlines(String s, int limit) {
        int count = 0;
        for (int i = 0; i < limit && i < s.length(); i++) {
            if (s.charAt(i) == '\n') {
                count++;
            }
        }
        return count;
    }

    private static void checkInvariants(List<Row> rows) {
        Set<String> seen = new HashSet<>();
        Set<String> duplicates = new HashSet<>();
        for (Row r : rows) {
            String key = rowKey(r);
            if (!seen.add(key)) {
                duplicates.add(key);
            }
        }
        if (!duplicates.isEmpty()) {
            throw new CensusFailure("duplicate endpoint records detected (" + duplicates.size() + "): "
                    + String.join(" | ", duplicates));
        }
        long direct = rows.stream().filter(r -> r.kind().equals("direct_verb")).count();
        long anonymous = rows.stream().filter(r -> r.kind().equals("anonymous_composed")).count();
        long bare = rows.stream().filter(r -> r.kind().equals("bare_request_mapping")).count();
        if (rows.size() != EXPECTED_TOTAL) {
            throw new CensusFailure("total endpoint count is " + rows.size() + ", expected exactly " + EXPECTED_TOTAL
                    + " (direct_verb=" + direct + ", anonymous_composed=" + anonymous + ", bare_request_mapping=" + bare + ")");
        }
        if (direct != EXPECTED_DIRECT_VERB) {
            throw new CensusFailure("direct_verb count is " + direct + ", expected exactly " + EXPECTED_DIRECT_VERB);
        }
        if (anonymous != EXPECTED_ANONYMOUS_COMPOSED) {
            throw new CensusFailure("anonymous_composed count is " + anonymous + ", expected exactly "
                    + EXPECTED_ANONYMOUS_COMPOSED);
        }
        if (bare != EXPECTED_BARE_REQUEST_MAPPING) {
            throw new CensusFailure("bare_request_mapping count is " + bare + ", expected exactly "
                    + EXPECTED_BARE_REQUEST_MAPPING);
        }
    }

    private static String rowKey(Row r) {
        return r.kind() + "|" + r.method() + "|" + r.route() + "|" + r.simple() + "|" + r.fqn()
                + "|" + r.handler() + "|" + r.path() + ":" + r.line();
    }

    private static String buildCensus(List<Row> rows) {
        StringBuilder out = new StringBuilder();
        out.append("# eladmin endpoint census\n");
        out.append("# generated_by: tools/endpoint-census/EndpointCensus.java (static source scan; no application start, no network)\n");
        out.append("# normalization: route = class-level @RequestMapping prefix + method path; empty route becomes \"/\"; leading slash enforced; duplicate slashes collapsed\n");
        out.append("# sort order: route, http_method, controller_simple, handler_method, source path, source line\n");
        out.append("# columns: mapping_kind|http_method|route|controller_simple|controller_fqn|handler_method|source_location\n");
        out.append("# rows: ").append(rows.size()).append('\n');
        for (Row r : rows) {
            out.append(r.kind()).append('|')
                    .append(r.method()).append('|')
                    .append(r.route()).append('|')
                    .append(r.simple()).append('|')
                    .append(r.fqn()).append('|')
                    .append(r.handler()).append('|')
                    .append(r.path()).append(':').append(r.line()).append('\n');
        }
        return out.toString();
    }

    private static Path checksumPath(Path censusFile) {
        Path name = censusFile.getFileName();
        return censusFile.resolveSibling(name + ".sha256");
    }

    private static String sha256Hex(byte[] data) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        StringBuilder out = new StringBuilder(64);
        for (byte b : digest.digest(data)) {
            out.append(String.format("%02x", b));
        }
        return out.toString();
    }

    private static int firstDiffIndex(byte[] a, byte[] b) {
        int n = Math.min(a.length, b.length);
        for (int i = 0; i < n; i++) {
            if (a[i] != b[i]) {
                return i;
            }
        }
        return n;
    }

    private static int byteToLine(byte[] data, int offset) {
        int line = 1;
        for (int i = 0; i < offset && i < data.length; i++) {
            if (data[i] == '\n') {
                line++;
            }
        }
        return line;
    }

    private static String slash(String path) {
        return path.replace('\\', '/');
    }
}
