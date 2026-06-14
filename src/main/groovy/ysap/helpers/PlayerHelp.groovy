package ysap.helpers
import ysap.TerminalFormatter

class PlayerHelp {
    static String showHelp(String category = null, int maxWidth = 65) {
        if (category) {
            return showHelpCategory(category, maxWidth)
        }

        // Use compact help for narrow displays (HUD mode)
        if (maxWidth <= 50) {
            return showCompactHelp(maxWidth)
        }

        // Main help menu
        def helpMenu = new BoxBuilder(maxWidth)
                .addCenteredLine("🔮 LAMBDA COMMAND REFERENCE 🔮")
                .addSeparator()
                .addCenteredLine("Type 'help <category>' for detailed information")
                .addEmptyLine()
                .addLine("  📋 CATEGORIES:")
                .addLine("  ═════════════")
                .addEmptyLine()
                .addLine("  basic      - Essential commands & navigation")
                .addLine("  combat     - Defrag bot encounters & defense")
                .addLine("  fragments  - Logic fragment collection & fusion")
                .addLine("  abilities  - Special abilities & recursion powers")
                .addLine("  economy    - Bits, trading & merchant system")
                .addLine("  puzzle     - Puzzle solving & elemental symbols")
                .addLine("  social     - Heap space & player interaction")
                .addLine("  repair     - Sector repair & maintenance")
                .addLine("  files      - File system & data management")
                .addLine("  errors     - System error codes (errno)")
                .addLine("  all        - Show all commands (long list)")
                .addEmptyLine()
                .addLine("  💡 Example: help combat")
                .addEmptyLine()
                .addLine("  Quick keys: status (s), inventory (i), map (m)")
                .build()

        return TerminalFormatter.formatText(helpMenu, 'bold', 'cyan')
    }

    private static String showHelpCategory(String category, int maxWidth = 70) {
        switch(category.toLowerCase()) {
            case 'basic':     return renderHelp("BASIC COMMANDS",   BASIC,     maxWidth, 'green')
            case 'combat':    return renderHelp("DEFRAG BOT COMBAT", COMBAT,    maxWidth, 'red')
            case 'fragments': return renderHelp("LOGIC FRAGMENTS",  FRAGMENTS, maxWidth, 'magenta')
            case 'abilities': return renderHelp("SPECIAL ABILITIES", ABILITIES, maxWidth, 'yellow')
            case 'economy':   return renderHelp("ECONOMY & TRADING", ECONOMY,   maxWidth, 'green')
            case 'puzzle':    return renderHelp("PUZZLE SYSTEM",     PUZZLE,    maxWidth, 'cyan')
            case 'social':    return renderHelp("HEAP SPACE",        SOCIAL,    maxWidth, 'magenta')
            case 'repair':    return renderHelp("SECTOR REPAIR",     REPAIR,    maxWidth, 'yellow')
            case 'files':     return renderHelp("FILE SYSTEM",       FILES,     maxWidth, 'blue')
            case 'errors':    return renderHelp("SYSTEM ERRORS",     ERRORS,    maxWidth, 'red')
            case 'all':       return showAllHelp(maxWidth)
            default:          return showHelpError(category, maxWidth)
        }
    }

    /**
     * One renderer, two looks. Wide terminals (classic) get the framed BoxBuilder; narrow terminals
     * (HUD command area, maxWidth<=50) get PLAIN indented text — no box border to overflow/mangle, and
     * the HUD's own word-wrap shows the full line. Content lives ONCE per category in the *_HELP lists.
     */
    private static String renderHelp(String title, List<String> lines, int maxWidth, String color) {
        if (maxWidth <= 50) {
            def sb = new StringBuilder("=== ${title} ===\r\n")
            lines.each { sb.append(it ?: '').append("\r\n") }
            return TerminalFormatter.formatText(sb.toString(), 'bold', color)
        }
        def box = new BoxBuilder(maxWidth).addCenteredLine(title).addSeparator()
        lines.each { it ? box.addLine(it) : box.addEmptyLine() }
        return TerminalFormatter.formatText(box.build(), 'bold', color)
    }

    // --- category content: one list per page, rendered boxed (classic) or plain (HUD). "" = blank line.
    private static final List<String> BASIC = [
        "  ESSENTIAL", "  =========",
        "  status (s)      - Entity status, bits, location",
        "  inventory (i)   - Fragments, items, abilities",
        "  help [category] - Command reference",
        "  errno <n>       - Look up a system error code",
        "  history         - Recent command history",
        "  clear           - Clear the terminal",
        "  quit            - Disconnect (state saved)",
        "",
        "  NAVIGATION", "  ==========",
        "  cc <x,y>        - Change coordinates (e.g. cc 3,5)",
        "  scan            - Scan for items & threats",
        "  map (m)         - Show the matrix level map",
        "  session         - Game session info",
        "",
        "  REPAIR", "  ======",
        "  repair          - List repairable (damaged) sectors",
        "  repair <x> <y>  - Repair an adjacent DAMAGED sector",
        "                    (see 'errno 5')",
        "",
        "  TIP: a sector wiped by defrag must be repaired",
        "       before you can enter it."
    ].asImmutable()

    private static final List<String> COMBAT = [
        "  ENCOUNTER SEQUENCE", "  ==================",
        "  When you encounter a defrag bot:",
        "  1. defrag -h              - Learn the system",
        "  2. cat /proc/defrag/<id>  - View process file",
        "  3. grep -o <pid> /proc/defrag/<id>",
        "                            - Isolate the PID",
        "  4. kill -9 <pid>          - Terminate the bot",
        "",
        "  WARNING: bots drain bits while engaged.",
        "  Act quickly or flee to minimize losses.",
        "",
        "  AUTO-DEFRAG SYSTEM", "  ==================",
        "  defrag_status   - System-wide threat status",
        "                    (auto-defrag wipes sectors!)",
        "",
        "  TIP: higher ethnicity = stronger grep patterns"
    ].asImmutable()

    private static final List<String> FRAGMENTS = [
        "  COLLECTION", "  ==========",
        "  pickup          - Collect fragment here",
        "  scan            - Find nearby fragments",
        "",
        "  MANAGEMENT", "  ==========",
        "  cat fragment_file - List all your fragments",
        "  cat <fragment>    - View fragment details",
        "  ls                - List files here",
        "",
        "  FUSION", "  ======",
        "  fusion <fragment> - Fuse 3+ identical fragments",
        "                      into an enhanced version",
        "",
        "  TIP: fused fragments gain bonus power levels"
    ].asImmutable()

    private static final List<String> ABILITIES = [
        "  RECURSION POWERS", "  ================",
        "  recurse <ability> - Activate ethnicity power",
        "  Abilities: movement, fusion, defend,",
        "             mine, stealth, process",
        "",
        "  SPECIAL ITEMS", "  =============",
        "  use             - List available items",
        "  use <item>      - Activate an item effect",
        "  symbols         - Show elemental symbols",
        "",
        "  TIP: higher ethnicity = more recursion uses"
    ].asImmutable()

    private static final List<String> ECONOMY = [
        "  CURRENCY", "  ========",
        "  entropy         - Coherence & daily refresh",
        "  mining          - Collect passive bit rewards",
        "",
        "  MERCHANT", "  ========",
        "  shop            - Browse items (at a merchant)",
        "  buy <item#>     - Purchase from a merchant",
        "  sell <fragment> - Sell a fragment for bits",
        "",
        "  PLAYER TRADING", "  ==============",
        "  pay <entity> <bits> - Transfer bits",
        "  trade <entity>      - Open the trade interface",
        "",
        "  TIP: rare fragments sell for more bits"
    ].asImmutable()

    private static final List<String> PUZZLE = [
        "  PUZZLE MECHANICS", "  ================",
        "  collect_var <name> - Find a hidden variable",
        "  chmod +x <file>    - Make a puzzle file run",
        "  execute <file> [var] - Run puzzle logic",
        "",
        "  ELEMENTAL SYMBOLS", "  =================",
        "  execute --<flag> <nonce> <file>",
        "                  - Unlock an elemental symbol",
        "  unlock_symbol <element> <flag>",
        "                  - Claim a symbol you stand on",
        "",
        "  TRACKING", "  ========",
        "  pinv / pprog / pmarket - Puzzle inventory,",
        "                    progress, knowledge market",
        "",
        "  TIP: gather all 4 elements to invoke the Daemon"
    ].asImmutable()

    private static final List<String> SOCIAL = [
        "  HEAP SPACE", "  ==========",
        "  heap            - Enter the heap (chat/trade)",
        "  exit            - Leave the heap",
        "",
        "  COMMUNICATION", "  =============",
        "  echo <message>  - Message everyone",
        "  pm <entity> <msg> - Private message",
        "  list / who      - Show entities in the heap",
        "",
        "  COMMERCE", "  ========",
        "  pay <entity> <bits> - Send bits",
        "  trade <entity>      - Trade items/fragments",
        "",
        "  TIP: team up to supply the Lambda"
    ].asImmutable()

    private static final List<String> REPAIR = [
        "  COMMANDS", "  ========",
        "  repair          - List repairable sectors",
        "  repair <x> <y>  - Start the repair mini-game",
        "                    (must be ADJACENT)",
        "  repair scan     - Detailed area analysis",
        "  repair status   - 5x5 sector-health grid",
        "",
        "  MINI-GAME", "  =========",
        "  - Match the spinning digits to the code",
        "  - Press ENTER to lock each digit",
        "  - Type 'exit' to cancel",
        "",
        "  A damaged sector reports errno 5 and cannot",
        "  be entered until repaired. Auto-defrag keeps",
        "  wiping sectors, so repair to hold ground.",
        "",
        "  TIP: high-value sectors = longer repair codes"
    ].asImmutable()

    private static final List<String> FILES = [
        "  FILE COMMANDS", "  =============",
        "  ls              - List directory contents",
        "  cat <file>      - View file contents",
        "  grep <pat> <file> - Search within a file",
        "",
        "  SPECIAL FILES", "  =============",
        "  fragment_file   - Your fragment collection",
        "  /proc/defrag/<id> - A defrag bot's process",
        "",
        "  TIP: use grep -o to isolate a bot's PID"
    ].asImmutable()

    private static final List<String> ERRORS = [
        "  SYSTEM ERRORS", "  =============",
        "  The system reports failures errno-style.",
        "",
        "  errno           - List all known error codes",
        "  errno <n>       - Explain a code + the remedy",
        "",
        "  Known codes:",
        "  errno 5 = SECTOR_DAMAGED (repair_required)",
        "",
        "  TIP: when a command is refused, the errno in",
        "       the message tells you how to recover."
    ].asImmutable()

    private static String showAllHelp(int maxWidth = 78) {
        // This would be the original single-page format
        def box = new BoxBuilder(maxWidth)
                .addCenteredLine("COMPLETE COMMAND REFERENCE")
                .addSeparator()
                .addLine("  ⚠️  This is a long list! Consider using 'help <category>' instead")
                .addEmptyLine()
        // ... add all commands in a condensed format
                .build()

        return TerminalFormatter.formatText(box, 'bold', 'cyan')
    }

    private static String showHelpError(String category, int maxWidth = 50) {
        def box = new BoxBuilder(maxWidth)
                .addCenteredLine("❌ HELP ERROR")
                .addSeparator()
                .addLine("  Unknown category: '${category}'")
                .addEmptyLine()
                .addLine("  Type 'help' to see categories")
                .build()

        return TerminalFormatter.formatText(box, 'bold', 'red')
    }

    // Mingle/Chat Section
    static String chat(String category){
        switch(category.toLowerCase()) {
            case 'help':
                return chatHelp()
        }

    }

    private static String chatHelp(){
        def box = new BoxBuilder(70)
                .addCenteredLine("=== HEAP COMMANDS ===")
                .addSeparator()
                .addLine("  HEAP SPACE:::::::::::::::")
                .addEmptyLine()
                .addLine("  ══════════════")
                .addLine("  echo <msg>          - Message to all")
                .addLine("  pm <entity> <msg>   - Private message")
                .addLine("  list/who            - Show heap list")
                .addEmptyLine()
                .addLine("  COMMERCE::")
                .addLine("  ═══════════════")
                .addLine("  trade <entity>      - Open swap space")
                .addLine("  └─ Trade / Sell / Buy")
                .addEmptyLine()
                .addLine("  💡 :TIPS: 💡 ")
                .addLine("  ═══════════════")
                .addLine("  • Logic fragments are valuable - trade them for bits!")
                .addLine("  • Higher power level fragments are worth more")
                .addLine("  • Duplicate fragments (x2, x3) can be sold individually")
                .addEmptyLine()
                .addLine("  HEAP SPACE:::::::::::::::")
                .addEmptyLine()
                .build()

        return TerminalFormatter.formatText(box, 'bold', 'yellow')

    }

    // Note: Maybe deprecated maybe useful later ??
    private String queryTerminalSize(PrintWriter writer, BufferedReader reader) {
        // ANSI Sequence to query terminal size
        writer.println("Press Enter...")
        writer.print("\033[18t")
        writer.flush()

        try {
            // Wait and read response
            // Response format is expected as ESC[8;<height>;<width>t
            // Adjust this reading mechanism based on how your terminal actually responds
            String response = reader.readLine();

            // Log the raw response for debugging
            println "Raw terminal size response: $response"

            // Parsing the response to extract height and width
            // This is a simplified example; actual parsing may vary based on response format
            if (response && response.matches(/.*\033\[8;(\d+);(\d+)t.*/)) {
                return response.replaceAll(/.*\033\[8;(\d+);(\d+)t.*/, '$1x$2')
            }
        } catch (Exception e) {
            println "Error reading terminal size: $e"
        }
        return "unknown"
    }

    private static String showCompactHelp(int maxWidth) {
        def box = new BoxBuilder(maxWidth)
                .addCenteredLine("🔮 LAMBDA COMMANDS 🔮")
                .addSeparator()
                .addLine("  status (s)     - Entity info")
                .addLine("  inventory (i)  - Items & fragments")
                .addLine("  cc <x,y>       - Change coords")
                .addLine("  scan           - Find items")
                .addLine("  map (m)        - Level overview")
                .addLine("  pickup         - Collect fragment")
                .addLine("  repair <x> <y> - Fix damaged sector")
                .addLine("  mingle         - Enter heap space")
                .addLine("  errno <n>      - Error code lookup")
                .addLine("  help <cat>     - Detailed help")
                .addEmptyLine()
                .addLine("  Categories: basic, combat,")
                .addLine("  fragments, abilities, economy,")
                .addLine("  puzzle, social, repair, files,")
                .addLine("  errors")
                .build()

        return TerminalFormatter.formatText(box, 'bold', 'cyan')
    }
}
