import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Main.java
 * ================================
 * Entry point for the HOI4 definition.csv Validator.
 * Wires together the classes defined in HOI4DefinitionValidator.java.
 *
 * HOW TO USE IN jGRASP:
 *   1. Open BOTH files in jGRASP (HOI4DefinitionValidator.java and Main.java)
 *   2. Compile HOI4DefinitionValidator.java first (lightning bolt)
 *   3. Then compile and run Main.java
 *   4. When prompted, enter the full path to your definition.csv file
 *
 * HOW TO USE FROM COMMAND LINE:
 *   javac HOI4DefinitionValidator.java Main.java
 *   java Main
 *   -- or pass the path directly --
 *   java Main path/to/definition.csv
 *
 * OUTPUT:
 *   - Full issue list printed to console (in file order)
 *   - TreeMap summary printed to console (sorted by province ID)
 *   - definition_validation_report.txt saved next to your CSV
 */
public class Main {

    public static void main(String[] args) throws IOException {

        printBanner();

        // ── Resolve file path ─────────────────────────────────────────────────
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
            System.err.println("ERROR: File not found — " + csvPath.toAbsolutePath());
            return;
        }

        System.out.println("File : " + csvPath.toAbsolutePath());
        System.out.println();

        // ── Parse ─────────────────────────────────────────────────────────────
        DefinitionParser parser = new DefinitionParser(csvPath);
        ParseResult      result = parser.parse();

        // ── Check ─────────────────────────────────────────────────────────────
        DefinitionChecker         checker = new DefinitionChecker(result);
        List<ValidationIssue>     issues  = checker.runAllChecks();
        TreeMap<Integer, List<ValidationIssue>> errorMap = checker.getErrorMap();

        // ── Report ────────────────────────────────────────────────────────────
        ReportWriter writer = new ReportWriter(csvPath, result, issues, errorMap);
        writer.printToConsole();
        writer.writeReportFile();
    }

    // ── Banner ────────────────────────────────────────────────────────────────

    private static void printBanner() {
        System.out.println("╔══════════════════════════════════════════════╗");
        System.out.println("║    HOI4 definition.csv Validator             ║");
        System.out.println("║    Compatible with jGRASP  |  JDK 11+        ║");
        System.out.println("╚══════════════════════════════════════════════╝");
        System.out.println();
    }
}
