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
                .addLine("  repair     - Coordinate repair & maintenance")
                .addLine("  files      - File system & data management")
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
            case 'basic':
                return showBasicHelp(maxWidth)
            case 'combat':
                return showCombatHelp(maxWidth)
            case 'fragments':
                return showFragmentsHelp(maxWidth)
            case 'abilities':
                return showAbilitiesHelp(maxWidth)
            case 'economy':
                return showEconomyHelp(maxWidth)
            case 'puzzle':
                return showPuzzleHelp(maxWidth)
            case 'social':
                return showSocialHelp(maxWidth)
            case 'repair':
                return showRepairHelp(maxWidth)
            case 'files':
                return showFilesHelp(maxWidth)
            case 'all':
                return showAllHelp(maxWidth)
            default:
                return showHelpError(category, maxWidth)
        }
    }

    private static String showBasicHelp(int maxWidth = 70) {
        def box = new BoxBuilder(maxWidth)
                .addCenteredLine("📋 BASIC COMMANDS")
                .addSeparator()
                .addLine("  ESSENTIAL")
                .addLine("  ═════════")
                .addLine("  status (s)          - Show entity status, bits, and location")
                .addLine("  inventory (i)       - View fragments, items, abilities")
                .addLine("  help [category]     - Show command reference")
                .addLine("  quit                - Safely disconnect from realm")
                .addLine("  clear               - Clear terminal screen")
                .addLine("  history             - Show recent command history")
                .addEmptyLine()
                .addLine("  NAVIGATION")
                .addLine("  ══════════")
                .addLine("  cc (x,y)            - Change coordinates")
                .addLine("                        Example: cc (3,5)")
                .addLine("  scan                - Scan area for items & threats")
                .addLine("  map (m)             - Show full matrix level map")
                .addLine("  session             - Show game session info")
                .addEmptyLine()
                .addLine("  💡 TIP: Coordinates wrap around edges (0-9)")
                .build()

        return TerminalFormatter.formatText(box, 'bold', 'green')
    }

    private static String showCombatHelp(int maxWidth = 70) {
        def box = new BoxBuilder(maxWidth)
                .addCenteredLine("⚔️ DEFRAG BOT COMBAT")
                .addSeparator()
                .addLine("  ENCOUNTER SEQUENCE")
                .addLine("  ═════════════════")
                .addLine("  When you encounter a defrag bot:")
                .addEmptyLine()
                .addLine("  1. defrag -h              - Learn combat system")
                .addLine("  2. cat /proc/defrag/<id>  - View bot process file")
                .addLine("  3. grep '<pattern>' /proc/defrag/<id>")
                .addLine("                            - Search for PID pattern")
                .addLine("  4. kill -9 <pid>          - Terminate bot process")
                .addEmptyLine()
                .addLine("  ⚠️  WARNING: Bots drain 5 bits every 5 seconds!")
                .addLine("  ⚠️  Act quickly or flee to minimize losses")
                .addEmptyLine()
                .addLine("  AUTO-DEFRAG SYSTEM")
                .addLine("  ═════════════════")
                .addLine("  defrag_status       - Check system-wide threats")
                .addLine("                        Auto-defrag destroys coordinates!")
                .addEmptyLine()
                .addLine("  💡 TIP: Higher ethnicity = stronger grep patterns")
                .build()

        return TerminalFormatter.formatText(box, 'bold', 'red')
    }

    private static String showFragmentsHelp(int maxWidth = 70) {
        def box = new BoxBuilder(maxWidth)
                .addCenteredLine("🧩 LOGIC FRAGMENTS")
                .addSeparator()
                .addLine("  COLLECTION")
                .addLine("  ══════════")
                .addLine("  pickup              - Collect fragment at location")
                .addLine("  scan                - Find nearby fragments")
                .addEmptyLine()
                .addLine("  MANAGEMENT")
                .addLine("  ══════════")
                .addLine("  cat fragment_file   - List all your fragments")
                .addLine("  cat <fragment>      - View fragment details")
                .addLine("                        Example: cat hello_world.rb")
                .addLine("  ls                  - List files in directory")
                .addEmptyLine()
                .addLine("  FUSION SYSTEM")
                .addLine("  ════════════")
                .addLine("  fusion <fragment>   - Fuse 3+ identical fragments")
                .addLine("                        Creates enhanced versions!")
                .addLine("                        Example: fusion hello_world.rb")
                .addEmptyLine()
                .addLine("  💡 TIP: Fused fragments have bonus power levels")
                .build()

        return TerminalFormatter.formatText(box, 'bold', 'magenta')
    }

    private static String showAbilitiesHelp(int maxWidth = 70) {
        def box = new BoxBuilder(maxWidth)
                .addCenteredLine("✨ SPECIAL ABILITIES")
                .addSeparator()
                .addLine("  RECURSION POWERS")
                .addLine("  ═══════════════")
                .addLine("  recurse <ability>   - Activate ethnicity power")
                .addEmptyLine()
                .addLine("  Available abilities:")
                .addLine("  • movement   - Teleport to random safe coordinate")
                .addLine("  • fusion     - Enhanced fragment fusion chance")
                .addLine("  • defend     - Shield against defrag attacks")
                .addLine("  • mine       - Boost bit mining rate")
                .addLine("  • stealth    - Hide from defrag detection")
                .addLine("  • process    - Speed up puzzle solving")
                .addEmptyLine()
                .addLine("  SPECIAL ITEMS")
                .addLine("  ════════════")
                .addLine("  use                 - List available items")
                .addLine("  use <item>          - Activate item effect")
                .addLine("  symbols             - Show elemental symbols")
                .addEmptyLine()
                .addLine("  💡 TIP: Higher ethnicity = more recursion uses")
                .build()

        return TerminalFormatter.formatText(box, 'bold', 'yellow')
    }

    private static String showEconomyHelp(int maxWidth = 70) {
        def box = new BoxBuilder(maxWidth)
                .addCenteredLine("💰 ECONOMY & TRADING")
                .addSeparator()
                .addLine("  CURRENCY")
                .addLine("  ════════")
                .addLine("  entropy             - Check coherence & daily refresh")
                .addLine("  mining              - Collect passive bit rewards")
                .addLine("                        Rate: ethnicity × 10 bits/hour")
                .addEmptyLine()
                .addLine("  MERCHANT SYSTEM")
                .addLine("  ══════════════")
                .addLine("  shop                - Browse items (at merchant)")
                .addLine("  buy <item#>         - Purchase from merchant")
                .addLine("  sell <fragment>     - Sell fragments for bits")
                .addEmptyLine()
                .addLine("  PLAYER TRADING")
                .addLine("  ═════════════")
                .addLine("  pay <player> <bits> - Transfer bits to player")
                .addLine("  trade <player>      - Open trade interface")
                .addEmptyLine()
                .addLine("  💡 TIP: Rare fragments sell for more bits!")
                .build()

        return TerminalFormatter.formatText(box, 'bold', 'green')
    }

    private static String showPuzzleHelp(int maxWidth = 70) {
        def box = new BoxBuilder(maxWidth)
                .addCenteredLine("🎯 PUZZLE SYSTEM")
                .addSeparator()
                .addLine("  PUZZLE MECHANICS")
                .addLine("  ═══════════════")
                .addLine("  collect_var <name>  - Find hidden variables")
                .addLine("                        Example: collect_var x")
                .addEmptyLine()
                .addLine("  chmod +x <filename>  - make puzzle file executable")
                .addLine("                        Example: chmod +x air_unlock_3.py")
                .addEmptyLine()
                .addLine("  execute <fragment> [var] - Run puzzle logic")
                .addLine("                        Example: execute solver.py x")
                .addEmptyLine()
                .addLine("  ELEMENTAL SYMBOLS")
                .addLine("  ════════════════")
                .addLine("  execute --<flag> <nonce> <file>")
                .addLine("                      - Unlock elemental symbols")
                .addLine("                        Example: execute --fire abc123 key.rb")
                .addEmptyLine()
                .addLine("  PUZZLE TRACKING")
                .addLine("  ══════════════")
                .addLine("  pinv                - Show puzzle inventory")
                .addLine("  pprog               - Competitive progress")
                .addLine("  pmarket             - Trade puzzle knowledge")
                .addEmptyLine()
                .addLine("  💡 TIP: Collect all 7 symbols to escape!")
                .build()

        return TerminalFormatter.formatText(box, 'bold', 'cyan')
    }

    private static String showSocialHelp(int maxWidth = 70) {
        def box = new BoxBuilder(maxWidth)
                .addCenteredLine("💬 HEAP SPACE")
                .addSeparator()
                .addLine("  HEAP SPACE")
                .addLine("  ═════════════")
                .addLine("  heap              - Enter heap space")
                .addLine("  exit                - Leave heap")
                .addEmptyLine()
                .addLine("  COMMUNICATION")
                .addLine("  ════════════")
                .addLine("  echo <message>      - Send public message")
                .addLine("  pm <player> <msg>   - Send private message")
                .addLine("  list / who          - Show online players")
                .addEmptyLine()
                .addLine("  INTERACTIONS")
                .addLine("  ═══════════")
                .addLine("  pay <player> <bits> - Send bits to player")
                .addLine("  trade <player>      - Trade items/fragments")
                .addEmptyLine()
                .addLine("  💡 TIP: Team up for harder puzzles!")
                .build()

        return TerminalFormatter.formatText(box, 'bold', 'magenta')
    }

    private static String showRepairHelp(int maxWidth = 70) {
        def box = new BoxBuilder(maxWidth)
                .addCenteredLine("🔧 COORDINATE REPAIR")
                .addSeparator()
                .addLine("  REPAIR COMMANDS")
                .addLine("  ══════════════")
                .addLine("  repair              - Show repairable coordinates")
                .addLine("  repair <x> <y>      - Start repair mini-game")
                .addLine("                        Must be adjacent!")
                .addLine("  repair scan         - Detailed area analysis")
                .addLine("  repair status       - Show 5x5 grid status")
                .addEmptyLine()
                .addLine("  REPAIR MINI-GAME")
                .addLine("  ═══════════════")
                .addLine("  • Match the code sequence")
                .addLine("  • Press ENTER to lock each digit")
                .addLine("  • Type 'exit' to cancel repair")
                .addEmptyLine()
                .addLine("  ⚠️  WARNING: Auto-defrag destroys coordinates!")
                .addLine("  ⚠️  Repair quickly to maintain safe zones")
                .addEmptyLine()
                .addLine("  💡 TIP: High-value coordinates = harder repairs")
                .build()

        return TerminalFormatter.formatText(box, 'bold', 'yellow')
    }

    private static String showFilesHelp(int maxWidth = 70) {
        def box = new BoxBuilder(maxWidth)
                .addCenteredLine("📁 FILE SYSTEM")
                .addSeparator()
                .addLine("  FILE COMMANDS")
                .addLine("  ════════════")
                .addLine("  ls                  - List directory contents")
                .addLine("  cat <file>          - View file contents")
                .addLine("  grep <pattern> <file> - Search in files")
                .addEmptyLine()
                .addLine("  SPECIAL FILES")
                .addLine("  ════════════")
                .addLine("  fragment_file       - Your fragment collection")
                .addLine("  /proc/defrag/<id>   - Defrag bot processes")
                .addLine("  puzzle_vars         - Collected variables")
                .addEmptyLine()
                .addLine("  💡 TIP: Use grep to find patterns in bot files")
                .build()

        return TerminalFormatter.formatText(box, 'bold', 'blue')
    }

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
                .addLine("  cc (x,y)       - Change coords")
                .addLine("  scan           - Find items")
                .addLine("  map (m)        - Level overview")
                .addLine("  pickup         - Collect fragment")
                .addLine("  mingle         - Enter heap space")
                .addLine("  help <cat>     - Detailed help")
                .addEmptyLine()
                .addLine("  Categories: basic, combat,")
                .addLine("  fragments, abilities, economy,")
                .addLine("  puzzle, social, repair, files")
                .build()

        return TerminalFormatter.formatText(box, 'bold', 'cyan')
    }
}
