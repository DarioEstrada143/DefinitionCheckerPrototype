# DefinitionCheckerPrototype
We will be asking AI, specifically the AI Model Claude, to attempt to muster up a code that will be able to go through a map definition file for the video game Hearts of Iron IV, and attempt to isolate any potential issues that could cause the game to crash.

## Interaction Log

**How familar are you Hearts of Iron IV modding? Specifically map modding?**

Pretty familiar! HOI4 map modding is a deep rabbit hole, and I've worked through a fair amount of it with you already.
On the technical side, I'm comfortable with:
Core map files

provinces.bmp — the RGB province map and its strict formatting requirements
definition.csv — province ID, RGB, type, coastal, terrain, continent columns, and the common mismatches that cause errors
heightmap.bmp, rivers.bmp, trees.bmp — their individual format/color requirements
terrain.bmp — and how it differs from the terrain category in definition.csv

Map definition files

default.map — sea zones, lakes, impassables, definitions path, etc.
continent.txt — province-to-continent assignments
adjacencies.csv — custom straits and cross-map connections
strategic_regions.txt and supply_areas.txt

State/VP layer

State history files, victory point assignments, building slot caps
Getting provinces properly owned vs. controlled at game start

Common pain points

Province ID 0 issues
RGB uniqueness violations in provinces.bmp
Mismatches between definition.csv entries and what's actually painted on the map
The validator's error output and how to read it

We've also worked through some of your specific mod's diagnostic issues — the definition.csv/provinces.bmp sync problem and generating the default.map and continent.txt files.
What are you running into now?You said: Let's go into the definition file a bit.Let's go into the definition file a bit. What are some common errors that could happen with this specific bit?11:15 AMClaude responded: The definition.The definition.csv is one of the most error-prone files in HOI4 map modding. Here's a breakdown of the common failure modes:
RGB Issues

Duplicate RGB values — every province must have a completely unique color; even one duplicate will cause crashes or silent merging of provinces
RGB (0,0,0) used — pure black is reserved and cannot be assigned to any province
RGB in definition.csv doesn't match what's actually painted in provinces.bmp — the most common desync issue; the game simply won't recognize the province
Values outside 0–255 range (usually a spreadsheet/export artifact)

Formatting Issues

Missing semicolons or wrong delimiter (it must be ; not ,)
Extra whitespace or trailing spaces in fields
Windows vs Unix line endings causing parse failures
A missing or malformed header row
Blank lines in the middle of the file
The file not being saved as plain ASCII/UTF-8 — Excel especially likes to add BOMs or encoding artifacts

Province ID Issues

IDs not starting at 1 (ID 0 is reserved by the engine)
Non-sequential IDs — gaps in the sequence can cause problems depending on engine version
Duplicate IDs
Province IDs referenced in state files, default.map, or continent.txt that don't exist in definition.csv

Type/Flag Field Errors

Invalid terrain category (must match entries in terrain.txt)
is_coastal flag set incorrectly — the engine does its own coastal detection from the map but mismatches can cause odd behavior
Continent ID of 0 or one not defined in continent.txt
Province type not being one of the valid values (land, sea, lake)

Sea/Lake Province Mistakes

Sea provinces accidentally flagged as land or vice versa
Lakes not being properly closed bodies (rivers.bmp interaction)
Sea provinces assigned to a continent (they typically shouldn't be)

The single most common source of crashes in my experience is RGB mismatch between definition.csv and provinces.bmp — usually caused by editing one without updating the other, or color rounding during image export.
Is there a specific error you're chasing down, or are you doing more of a general audit?

**How about this. Is it possible we could create a Java code that could say, take a definition file and go through it and search for these common errors and mismatches and such? But we make it so this code can be universally applied to any sort of definition file for any mod?**

Absolutely, that's a great idea — a universal HOI4 definition.csv validator. Java is a solid choice for this since it's portable and good for file parsing.

Before I start writing, let me nail down the scope so it's actually useful for you:

*Claude prompts a series of questions that I can give answers to*

Q: What checks should be included? (Select all that apply)
A: RGB uniqueness & validity (0,0,0 / duplicates / out of range), Province ID sequencing (gaps, duplicates, starts at 1), CSV formatting (delimiters, blank lines, encoding, header), Field value validation (terrain, type, continent, coastal)

Q: How should errors be reported?
A: Both console + log file

*End of questionnaire*

Good choices — skipping the BMP cross-reference keeps it self-contained and fast (that one needs image processing libraries and is a whole separate beast). Let me build this out.

Now let me create a build script and a sample test file so you can actually run it:

No JDK available, only JRE. Let me verify the code is correct by doing a logic dry-run, then package everything up for you to compile locally:

Here's what you've got and how to run it:

**To compile and run** (requires JDK 11+ installed):
```bash
chmod +x build_and_run.sh
./build_and_run.sh path/to/your/definition.csv
```

Or manually:
```bash
javac -d out src/main/java/hoi4/validator/DefinitionValidator.java
jar cfe hoi4-validator.jar hoi4.validator.DefinitionValidator -C out .
java -jar hoi4-validator.jar path/to/definition.csv
```

---

**What it catches:**

| Check | Behavior |
|---|---|
| UTF-8 BOM / non-ASCII bytes | ERROR / WARNING |
| CRLF vs CR-only line endings | WARNING |
| Blank lines mid-file | ERROR |
| Comma instead of semicolon delimiter | ERROR |
| Too few columns | ERROR |
| Province ID 0 or duplicates | ERROR |
| ID gaps (non-sequential) | WARNING |
| RGB out of range (0–255) | ERROR |
| RGB (0,0,0) black reserved | ERROR |
| Duplicate RGB values | ERROR |
| Invalid province type | ERROR |
| Invalid is_coastal value | ERROR |
| Sea/lake marked coastal | WARNING |
| Empty or unknown terrain | ERROR / WARNING |
| Land province with continent 0 | WARNING |

The test CSV I included has intentional errors baked in (ID 0, duplicate ID 5, duplicate RGB, bad R value of 300, lake marked coastal) so you can verify it's working correctly before pointing it at your real file.

Want me to add the BMP cross-reference pass next, or any other checks?

**Actually, is it possible you could make this in program that can be put into something like JGrasp? Not to mention, could we include Object Oriented Programming in this program as well?**

Great call — structuring it with OOP makes it much cleaner, more maintainable, and easier to extend later. I'll break it into proper classes with clear responsibilities, all in a single file that jGRASP can open and run directly.

Here's the class design I'm thinking:

- **`Province`** — data model representing one parsed row
- **`ValidationIssue`** — represents a single error or warning (type, line, message)
- **`DefinitionParser`** — reads the CSV and produces a list of `Province` objects
- **`DefinitionValidator`** — runs all checks against the parsed provinces
- **`ReportWriter`** — handles console and file output
- **`Main`** — entry point, wires everything together

jGRASP works best with a single `.java` file where `main` is in the top-level public class. I'll use inner static classes to keep it one file while still being fully OOP.Here's how to run it in jGRASP:

1. Open `HOI4DefinitionValidator.java` in jGRASP
2. Hit the **lightning bolt** to compile
3. Hit the **green play button** to run — it will prompt you to type the path to your `definition.csv`
4. Results print to the console, and a `definition_validation_report.txt` is saved next to your CSV

---

Here's the class breakdown so you can see the OOP structure clearly:

| Class | Responsibility |
|---|---|
| `Province` | Data model — one row from the CSV, raw + parsed fields |
| `ValidationIssue` | A single problem — type, line number, message |
| `IssueType` | Enum — `ERROR` or `WARNING` |
| `ParseResult` | Container returned by the parser — provinces + file-level metadata |
| `DefinitionParser` | Reads the file, handles encoding/blank lines, builds `Province` objects |
| `DefinitionChecker` | Runs all validation logic — each check is its own method |
| `ReportWriter` | Handles all output — console print and `.txt` file |
| `HOI4DefinitionValidator` | `main()` entry point — wires the pipeline together |

It's all in one `.java` file using inner static classes, which is exactly what jGRASP expects. Want me to add anything — custom terrain support from a user-supplied `terrain.txt`, or maybe a BMP cross-reference pass as a separate optional module?
