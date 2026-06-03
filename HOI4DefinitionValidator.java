import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * HOI4 definition.csv Validator
 * ================================
 * A fully OOP validator for Hearts of Iron IV definition.csv files.
 * Compatible with jGRASP and any standard Java IDE (JDK 11+).
 *
 * Usage (jGRASP):
 *   1. Open this file in jGRASP
 *   2. Compile (lightning bolt icon)
 *   3. Run (green play button) — enter your definition.csv path when prompted
 *
 * Usage (command line):
 *   javac HOI4DefinitionValidator.java
 *   java HOI4DefinitionValidator
 *
 * Classes:
 *   Province         — data model for one parsed definition row
 *   ValidationIssue  — a single error or warning with context
 *   IssueType        — enum: ERROR or WARNING
 *   DefinitionParser — reads CSV into Province objects
 *   DefinitionChecker— runs all validation logic
 *   ReportWriter     — outputs results to console and .txt report
 *   HOI4DefinitionValidator (Main) — entry point, wires everything together
 */
public class HOI4DefinitionValidator {

    // =========================================================================
    //  ENTRY POINT
    // =========================================================================

    public static void main(String[] args) throws IOException {
        System.out.println("============================================");
        System.out.println("  HOI4 definition.csv Validator");
        System.out.println("  OOP Edition — compatible with jGRASP");
        System.out.println("============================================");
        System.out.println();

        // Get file path — from args or prompt the user
        String filePath;
        if (args.length >= 1) {
            filePath = args[0];
        } else {
            Scanner sc = new Scanner(System.in);
            System.out.print("Enter path to definition.csv: ");
            filePath = sc.nextLine().trim();
        }

        Path csvPath = Paths.get(filePath);
        if (!Files.exists(csvPath)) {
            System.err.println("ERROR: File not found: " + csvPath.toAbsolutePath());
            return;
        }

        System.out.println("File: " + csvPath.toAbsolutePath());
        System.out.println();

        // ── Wire up the pipeline ──────────────────────────────────────────────
        DefinitionParser   parser   = new DefinitionParser(csvPath);
        ParseResult        result   = parser.parse();
        DefinitionChecker  checker  = new DefinitionChecker(result);
        List<ValidationIssue> issues = checker.runAllChecks();
        ReportWriter       writer   = new ReportWriter(csvPath, result, issues);
        writer.printToConsole();
        writer.writeReportFile();
    }


    // =========================================================================
    //  ENUM: IssueType
    //  Distinguishes hard errors from advisory warnings.
    // =========================================================================

    enum IssueType {
        ERROR, WARNING
    }


    // =========================================================================
    //  CLASS: ValidationIssue
    //  Represents a single problem found during validation.
    // =========================================================================

    static class ValidationIssue {
        private final IssueType type;
        private final int       lineNumber;   // 0 = global / not line-specific
        private final String    message;

        ValidationIssue(IssueType type, int lineNumber, String message) {
            this.type       = type;
            this.lineNumber = lineNumber;
            this.message    = message;
        }

        IssueType getType()       { return type; }
        int       getLineNumber() { return lineNumber; }
        String    getMessage()    { return message; }

        @Override
        public String toString() {
            String label = (type == IssueType.ERROR) ? "[ERROR]  " : "[WARNING]";
            String loc   = (lineNumber > 0)
                ? String.format("Line %5d", lineNumber)
                : "  (global)";
            return String.format("%s %s | %s", label, loc, message);
        }
    }


    // =========================================================================
    //  CLASS: Province
    //  Immutable data model for one parsed row in definition.csv.
    //  Raw string values are preserved alongside parsed values so the
    //  validator can report exactly what was in the file.
    // =========================================================================

    static class Province {
        // Raw strings straight from the CSV (for error reporting)
        private final String rawId;
        private final String rawR, rawG, rawB;
        private final String rawType;
        private final String rawCoastal;
        private final String rawTerrain;
        private final String rawContinent;
        private final int    lineNumber;

        // Parsed values (null if the field couldn't be parsed)
        private final Integer id;
        private final Integer r, g, b;

        Province(int lineNumber,
                 String rawId, String rawR, String rawG, String rawB,
                 String rawType, String rawCoastal, String rawTerrain, String rawContinent) {

            this.lineNumber    = lineNumber;
            this.rawId         = rawId.trim();
            this.rawR          = rawR.trim();
            this.rawG          = rawG.trim();
            this.rawB          = rawB.trim();
            this.rawType       = rawType.trim();
            this.rawCoastal    = rawCoastal.trim();
            this.rawTerrain    = rawTerrain.trim();
            this.rawContinent  = rawContinent.trim();

            this.id = tryParseInt(rawId);
            this.r  = tryParseInt(rawR);
            this.g  = tryParseInt(rawG);
            this.b  = tryParseInt(rawB);
        }

        // ── Accessors ─────────────────────────────────────────────────────────
        int     getLineNumber()   { return lineNumber; }
        String  getRawId()        { return rawId; }
        String  getRawR()         { return rawR; }
        String  getRawG()         { return rawG; }
        String  getRawB()         { return rawB; }
        String  getRawType()      { return rawType; }
        String  getRawCoastal()   { return rawCoastal; }
        String  getRawTerrain()   { return rawTerrain; }
        String  getRawContinent() { return rawContinent; }
        Integer getId()           { return id; }
        Integer getR()            { return r; }
        Integer getG()            { return g; }
        Integer getB()            { return b; }

        /** Returns "R,G,B" string key for uniqueness checks, or null if unparseable. */
        String getRgbKey() {
            if (r == null || g == null || b == null) return null;
            return r + "," + g + "," + b;
        }

        /** True if this province's type string matches the given type (case-insensitive). */
        boolean isType(String type) {
            return rawType.equalsIgnoreCase(type);
        }

        private static Integer tryParseInt(String s) {
            try { return Integer.parseInt(s.trim()); }
            catch (NumberFormatException e) { return null; }
        }

        @Override
        public String toString() {
            return String.format("Province[line=%d, id=%s, rgb=(%s,%s,%s), type=%s]",
                lineNumber, rawId, rawR, rawG, rawB, rawType);
        }
    }


    // =========================================================================
    //  CLASS: ParseResult
    //  Container returned by DefinitionParser.
    //  Holds the parsed Province list plus any raw parse-level issues.
    // =========================================================================

    static class ParseResult {
        private final List<Province>        provinces;
        private final List<ValidationIssue> parseIssues;  // encoding, blank lines, etc.
        private final boolean               hasBom;
        private final boolean               hasNonAscii;
        private final boolean               hasCrlfEndings;
        private final boolean               hasCrOnlyEndings;

        ParseResult(List<Province> provinces, List<ValidationIssue> parseIssues,
                    boolean hasBom, boolean hasNonAscii,
                    boolean hasCrlfEndings, boolean hasCrOnlyEndings) {
            this.provinces        = provinces;
            this.parseIssues      = parseIssues;
            this.hasBom           = hasBom;
            this.hasNonAscii      = hasNonAscii;
            this.hasCrlfEndings   = hasCrlfEndings;
            this.hasCrOnlyEndings = hasCrOnlyEndings;
        }

        List<Province>        getProvinces()        { return provinces; }
        List<ValidationIssue> getParseIssues()      { return parseIssues; }
        boolean               hasBom()              { return hasBom; }
        boolean               hasNonAscii()         { return hasNonAscii; }
        boolean               hasCrlfEndings()      { return hasCrlfEndings; }
        boolean               hasCrOnlyEndings()    { return hasCrOnlyEndings; }
    }


    // =========================================================================
    //  CLASS: DefinitionParser
    //  Reads the raw file bytes, checks encoding/line endings, and
    //  produces a ParseResult containing Province objects.
    // =========================================================================

    static class DefinitionParser {
        private final Path                  csvPath;
        private final List<ValidationIssue> issues = new ArrayList<>();

        DefinitionParser(Path csvPath) {
            this.csvPath = csvPath;
        }

        ParseResult parse() throws IOException {
            byte[] raw = Files.readAllBytes(csvPath);

            // ── Encoding checks ───────────────────────────────────────────────
            boolean hasBom = detectBom(raw);
            boolean hasNonAscii = detectNonAscii(raw);

            // ── Line-ending checks ────────────────────────────────────────────
            String content = new String(raw, StandardCharsets.UTF_8);
            boolean hasCrlf   = content.contains("\r\n");
            boolean hasCrOnly = !hasCrlf && content.replace("\r\n","").contains("\r");

            // ── Split into lines ──────────────────────────────────────────────
            String[] lines = content.split("\\r?\\n", -1);
            List<Province> provinces = parseLines(lines);

            return new ParseResult(provinces, issues, hasBom, hasNonAscii, hasCrlf, hasCrOnly);
        }

        // ── Internal helpers ──────────────────────────────────────────────────

        private List<Province> parseLines(String[] lines) {
            List<Province> provinces = new ArrayList<>();
            boolean headerSkipped    = false;

            for (int i = 0; i < lines.length; i++) {
                int lineNum = i + 1;
                String raw  = lines[i];

                // Blank line
                if (raw.trim().isEmpty()) {
                    if (i < lines.length - 1) {
                        issues.add(new ValidationIssue(IssueType.ERROR, lineNum,
                            "Blank line detected. HOI4 will likely stop reading the file here."));
                    }
                    continue;
                }

                // Delimiter check
                if (!raw.contains(";")) {
                    if (raw.contains(",")) {
                        issues.add(new ValidationIssue(IssueType.ERROR, lineNum,
                            "Row uses commas instead of semicolons: " + raw));
                    } else {
                        issues.add(new ValidationIssue(IssueType.ERROR, lineNum,
                            "No semicolons found — not a valid definition row: " + raw));
                    }
                    continue;
                }

                String[] cols = raw.split(";", -1);

                // Skip text header row
                if (!headerSkipped) {
                    headerSkipped = true;
                    if (isTextHeader(cols[0])) {
                        System.out.println("[INFO] Text header row detected at line " + lineNum + " — skipping.");
                        continue;
                    }
                }

                // Column count
                if (cols.length < 8) {
                    issues.add(new ValidationIssue(IssueType.ERROR, lineNum,
                        "Too few columns (" + cols.length + "). Expected at least 8 " +
                        "(id;r;g;b;type;is_coastal;terrain;continent). Row: " + raw));
                    continue;
                }

                // Trailing whitespace in any of the first 8 columns
                for (int c = 0; c < 8; c++) {
                    if (!cols[c].equals(cols[c].trim())) {
                        issues.add(new ValidationIssue(IssueType.WARNING, lineNum,
                            "Column " + (c + 1) + " has leading/trailing whitespace: '" + cols[c] + "'"));
                    }
                }

                provinces.add(new Province(
                    lineNum,
                    cols[0], cols[1], cols[2], cols[3],
                    cols[4], cols[5], cols[6], cols[7]
                ));
            }

            return provinces;
        }

        private boolean detectBom(byte[] raw) {
            return raw.length >= 3
                && (raw[0] & 0xFF) == 0xEF
                && (raw[1] & 0xFF) == 0xBB
                && (raw[2] & 0xFF) == 0xBF;
        }

        private boolean detectNonAscii(byte[] raw) {
            for (byte b : raw) {
                if ((b & 0xFF) > 127) return true;
            }
            return false;
        }

        private boolean isTextHeader(String firstCol) {
            try { Integer.parseInt(firstCol.trim()); return false; }
            catch (NumberFormatException e) { return true; }
        }
    }


    // =========================================================================
    //  CLASS: DefinitionChecker
    //  Runs all semantic validation passes against a ParseResult.
    //  Each check is its own method for clarity and easy extensibility.
    // =========================================================================

    static class DefinitionChecker {
        private final ParseResult            result;
        private final List<ValidationIssue>  issues = new ArrayList<>();

        // Valid values for type and is_coastal fields
        private static final Set<String> VALID_TYPES   =
            new HashSet<>(Arrays.asList("land", "sea", "lake"));
        private static final Set<String> VALID_COASTAL =
            new HashSet<>(Arrays.asList("true", "false", "1", "0"));

        // Known vanilla terrain categories — unknown values produce a WARNING
        // (not ERROR) so custom mod terrains aren't flagged as broken
        private static final Set<String> KNOWN_TERRAINS = new HashSet<>(Arrays.asList(
            "plains", "forest", "hills", "mountain", "desert",
            "marsh", "jungle", "urban", "ocean", "lakes", "glacier", "unknown"
        ));

        DefinitionChecker(ParseResult result) {
            this.result = result;
        }

        /** Runs every check and returns the combined issue list. */
        List<ValidationIssue> runAllChecks() {
            // Start with parse-level issues
            issues.addAll(result.getParseIssues());

            // File-level checks
            checkEncodingAndLineEndings();

            // Row-level checks (iterate once per province)
            for (Province p : result.getProvinces()) {
                checkProvinceId(p);
                checkRgbValidity(p);
                checkTypeField(p);
                checkCoastalField(p);
                checkTerrainField(p);
                checkContinentField(p);
            }

            // Cross-province checks (need the full list)
            checkRgbUniqueness();
            checkIdUniquenessAndSequence();

            return Collections.unmodifiableList(issues);
        }

        // ── File-level checks ─────────────────────────────────────────────────

        private void checkEncodingAndLineEndings() {
            if (result.hasBom()) {
                addError(0, "File has a UTF-8 BOM. HOI4 expects plain UTF-8 without BOM. "
                    + "Re-save without BOM (Notepad++ → Encoding → UTF-8 without BOM).");
            }
            if (result.hasNonAscii()) {
                addWarning(0, "File contains non-ASCII bytes. Definition files should be plain ASCII.");
            }
            if (result.hasCrlfEndings()) {
                addWarning(0, "File uses Windows (CRLF) line endings. Usually fine, but LF is safer.");
            }
            if (result.hasCrOnlyEndings()) {
                addError(0, "File uses CR-only line endings (old Mac format). This can cause the "
                    + "entire file to be read as one line. Convert to LF or CRLF.");
            }
        }

        // ── Per-province checks ───────────────────────────────────────────────

        private void checkProvinceId(Province p) {
            if (p.getId() == null) {
                addError(p.getLineNumber(), "Province ID is not a valid integer: '" + p.getRawId() + "'");
                return;
            }
            if (p.getId() == 0) {
                addError(p.getLineNumber(), "Province ID is 0. Province IDs must start at 1 "
                    + "(ID 0 is reserved by the engine).");
            }
            if (p.getId() < 0) {
                addError(p.getLineNumber(), "Province ID is negative: " + p.getId());
            }
        }

        private void checkRgbValidity(Province p) {
            checkColorChannel(p, "R", p.getRawR(), p.getR());
            checkColorChannel(p, "G", p.getRawG(), p.getG());
            checkColorChannel(p, "B", p.getRawB(), p.getB());

            // Pure black is reserved
            if (p.getR() != null && p.getG() != null && p.getB() != null
                    && p.getR() == 0 && p.getG() == 0 && p.getB() == 0) {
                addError(p.getLineNumber(), "RGB (0,0,0) is reserved and cannot be assigned to a province.");
            }
        }

        private void checkColorChannel(Province p, String channel, String raw, Integer value) {
            if (value == null) {
                addError(p.getLineNumber(), channel + " value is not a valid integer: '" + raw + "'");
            } else if (value < 0 || value > 255) {
                addError(p.getLineNumber(), channel + " value " + value + " is out of range (must be 0–255).");
            }
        }

        private void checkTypeField(Province p) {
            String type = p.getRawType().toLowerCase();
            if (type.isEmpty()) {
                addError(p.getLineNumber(), "Province type is empty. Must be one of: " + VALID_TYPES);
            } else if (!VALID_TYPES.contains(type)) {
                addError(p.getLineNumber(), "Invalid province type '" + p.getRawType()
                    + "'. Must be one of: " + VALID_TYPES);
            }
        }

        private void checkCoastalField(Province p) {
            String coastal = p.getRawCoastal().toLowerCase();
            if (coastal.isEmpty()) {
                addWarning(p.getLineNumber(), "is_coastal field is empty.");
            } else if (!VALID_COASTAL.contains(coastal)) {
                addError(p.getLineNumber(), "Invalid is_coastal value '" + p.getRawCoastal()
                    + "'. Expected: true / false / 1 / 0");
            }
            // Sea/lake provinces flagged as coastal is suspicious
            if ((p.isType("sea") || p.isType("lake"))
                    && (coastal.equals("true") || coastal.equals("1"))) {
                addWarning(p.getLineNumber(), "Province type is '" + p.getRawType()
                    + "' but is_coastal is true. Sea/lake provinces are not normally flagged as coastal.");
            }
        }

        private void checkTerrainField(Province p) {
            String terrain = p.getRawTerrain().toLowerCase();
            if (terrain.isEmpty()) {
                addError(p.getLineNumber(), "Terrain field is empty.");
            } else if (!KNOWN_TERRAINS.contains(terrain)) {
                addWarning(p.getLineNumber(), "Unknown terrain '" + p.getRawTerrain()
                    + "'. If this is a custom terrain defined in terrain.txt, this warning can be ignored.");
            }
        }

        private void checkContinentField(Province p) {
            try {
                int continent = Integer.parseInt(p.getRawContinent());
                if (continent < 0) {
                    addError(p.getLineNumber(), "Continent ID cannot be negative: " + continent);
                }
                if (continent == 0 && p.isType("land")) {
                    addWarning(p.getLineNumber(), "Land province has continent ID 0. "
                        + "Land provinces should be assigned to a continent (1 or higher).");
                }
            } catch (NumberFormatException e) {
                addError(p.getLineNumber(), "Continent is not a valid integer: '" + p.getRawContinent() + "'");
            }
        }

        // ── Cross-province checks ─────────────────────────────────────────────

        private void checkRgbUniqueness() {
            Map<String, Integer> rgbFirstSeen = new LinkedHashMap<>();
            for (Province p : result.getProvinces()) {
                String key = p.getRgbKey();
                if (key == null) continue; // already flagged as unparseable
                if (rgbFirstSeen.containsKey(key)) {
                    addError(p.getLineNumber(), "Duplicate RGB (" + key + ") — first seen on line "
                        + rgbFirstSeen.get(key) + ". Every province must have a unique color.");
                } else {
                    rgbFirstSeen.put(key, p.getLineNumber());
                }
            }
        }

        private void checkIdUniquenessAndSequence() {
            Map<Integer, Integer> idFirstSeen = new LinkedHashMap<>();
            List<Integer> validIds = new ArrayList<>();

            for (Province p : result.getProvinces()) {
                if (p.getId() == null || p.getId() <= 0) continue;
                if (idFirstSeen.containsKey(p.getId())) {
                    addError(p.getLineNumber(), "Duplicate Province ID " + p.getId()
                        + " — first seen on line " + idFirstSeen.get(p.getId()) + ".");
                } else {
                    idFirstSeen.put(p.getId(), p.getLineNumber());
                    validIds.add(p.getId());
                }
            }

            if (validIds.isEmpty()) return;
            Collections.sort(validIds);

            // Must start at 1
            if (validIds.get(0) != 1) {
                addError(0, "Province IDs do not start at 1. Lowest ID found is " + validIds.get(0) + ".");
            }

            // Find gaps
            List<Integer> gaps = new ArrayList<>();
            for (int i = 0; i < validIds.size() - 1; i++) {
                for (int missing = validIds.get(i) + 1; missing < validIds.get(i + 1); missing++) {
                    gaps.add(missing);
                    if (gaps.size() >= 50) break; // cap to avoid huge lists
                }
                if (gaps.size() >= 50) break;
            }

            if (!gaps.isEmpty()) {
                String gapStr = gaps.size() < 50
                    ? gaps.toString()
                    : gaps.subList(0, 50) + " ... (" + gaps.size() + "+ gaps total)";
                addWarning(0, "Non-sequential Province IDs — missing IDs: " + gapStr);
            }
        }

        // ── Logging helpers ───────────────────────────────────────────────────

        private void addError(int line, String msg) {
            issues.add(new ValidationIssue(IssueType.ERROR, line, msg));
        }

        private void addWarning(int line, String msg) {
            issues.add(new ValidationIssue(IssueType.WARNING, line, msg));
        }
    }


    // =========================================================================
    //  CLASS: ReportWriter
    //  Handles all output — console summary and .txt report file.
    // =========================================================================

    static class ReportWriter {
        private final Path                   csvPath;
        private final ParseResult            result;
        private final List<ValidationIssue>  issues;
        private final long                   errorCount;
        private final long                   warningCount;

        ReportWriter(Path csvPath, ParseResult result, List<ValidationIssue> issues) {
            this.csvPath      = csvPath;
            this.result       = result;
            this.issues       = issues;
            this.errorCount   = issues.stream().filter(i -> i.getType() == IssueType.ERROR).count();
            this.warningCount = issues.stream().filter(i -> i.getType() == IssueType.WARNING).count();
        }

        void printToConsole() {
            System.out.println("── Parse Summary ──────────────────────────────");
            System.out.println("  Provinces parsed : " + result.getProvinces().size());
            System.out.println();

            if (issues.isEmpty()) {
                System.out.println("  No issues found. File looks clean!");
            } else {
                System.out.println("── Issues ─────────────────────────────────────");
                for (ValidationIssue issue : issues) {
                    System.out.println(issue);
                }
            }

            System.out.println();
            System.out.println("── Result ─────────────────────────────────────");
            System.out.printf("  Errors  : %d%n", errorCount);
            System.out.printf("  Warnings: %d%n", warningCount);
            System.out.println("───────────────────────────────────────────────");
        }

        void writeReportFile() {
            Path reportPath = csvPath.resolveSibling("definition_validation_report.txt");
            try (PrintWriter pw = new PrintWriter(
                    new OutputStreamWriter(
                        new FileOutputStream(reportPath.toFile()), StandardCharsets.UTF_8))) {

                pw.println("HOI4 definition.csv Validation Report");
                pw.println("======================================");
                pw.println("File     : " + csvPath.toAbsolutePath());
                pw.println("Date     : " + new Date());
                pw.println("Errors   : " + errorCount);
                pw.println("Warnings : " + warningCount);
                pw.println("Provinces: " + result.getProvinces().size());
                pw.println();

                if (issues.isEmpty()) {
                    pw.println("No issues found. File looks clean!");
                } else {
                    pw.println("Issues");
                    pw.println("------");
                    for (ValidationIssue issue : issues) {
                        pw.println(issue);
                    }
                }

                System.out.println();
                System.out.println("Report saved to: " + reportPath.toAbsolutePath());

            } catch (IOException e) {
                System.err.println("Could not write report file: " + e.getMessage());
            }
        }
    }
}
