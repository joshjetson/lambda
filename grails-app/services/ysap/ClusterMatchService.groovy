package ysap

import ysap.helpers.BoxBuilder
import java.util.concurrent.ConcurrentHashMap

/**
 * Cluster Mode referee — match/lobby lifecycle, team membership, and role + true/decoy assignment.
 * One bounded concern (match truth); information/abilities live in ClusterRoleService (later pieces),
 * occupancy enforcement in CoordinateStateService. Role IS the player's ethnicity (avatarSilhouette);
 * identity is reused, not reinvented. All DB access is wrapped in withTransaction (telnet-thread rule).
 *
 * Additive: nothing here runs unless a player types `cluster ...`; Node/FFA play is untouched.
 */
class ClusterMatchService {

    static final int TEAM_CAP = 7
    // Panels are kept narrow enough to render inside the HUD's command panel (58-wide) as well as the
    // normal terminal — one width used everywhere (DRY). Cluster role panels reuse the same constant.
    static final int PANEL_WIDTH = 50

    // A full team = 2 Lambdas + one of each of the other 5 roles (race once per team, Lambda twice).
    private static final List<String> CANON_TEAM = [
        'CLASSIC_LAMBDA', 'CLASSIC_LAMBDA', 'CIRCUIT_PATTERN', 'GEOMETRIC_ENTITY',
        'FLOWING_CURRENT', 'DIGITAL_GHOST', 'BINARY_FORM'
    ].asImmutable()

    // The 6 ethnicities ARE the 6 Cluster roles. One source of the role-name mapping (DRY).
    private static final Map<String, String> ROLE_LABEL = [
        'CLASSIC_LAMBDA' : 'Lambda (Collector)',
        'CIRCUIT_PATTERN': 'Circuit (Tracker)',
        'GEOMETRIC_ENTITY': 'Geo (Scout)',
        'FLOWING_CURRENT': 'Current (Disruptor)',
        'DIGITAL_GHOST'  : 'Ghost (Saboteur)',
        'BINARY_FORM'    : 'Binary (Trapper)'
    ].asImmutable()

    /** Single source of the role-name mapping (used by the panel renderer AND the firewall). */
    static String roleLabel(String role) { ROLE_LABEL[role] ?: role }

    // In-memory immobilize state (Current's `lock`). Keyed by username → lock expiry millis.
    private final Map<String, Long> lockUntil = new ConcurrentHashMap<>()

    // In-memory UNCOLLECTED symbol coordinates per match (matchId → symbol → [x,y]). Same ephemeral
    // pattern as lockUntil; collected symbols live on ClusterMembership.heldSymbols. (Kept separate
    // from Node's level-global ElementalSymbol to avoid coupling — cluster symbols are per-match.)
    private final Map<String, Map<String, List<Integer>>> matchSymbols = new ConcurrentHashMap<>()
    private int relocSeq = 0

    void applyLock(String username, long durationMs) { lockUntil[username] = System.currentTimeMillis() + durationMs }
    void clearLock(String username) { lockUntil.remove(username) }
    boolean isLocked(String username) {
        Long until = lockUntil[username]
        if (until == null) return false
        if (System.currentTimeMillis() >= until) { lockUntil.remove(username); return false }
        return true
    }
    int lockRemainingSeconds(String username) {
        Long until = lockUntil[username]
        return until == null ? 0 : Math.max(0, ((until - System.currentTimeMillis()) / 1000) as int)
    }

    static final List<String> SYMBOLS = ['AIR', 'FIRE', 'EARTH', 'WATER'].asImmutable()

    /** End the match with the given player's team as the winner. Returns the winning team name. */
    String declareWin(String username) {
        String team = null
        ClusterMatch.withTransaction {
            def m = findActiveMembership(username)
            if (m) { team = m.team.name; m.team.match.state = 'ENDED'; m.team.match.winner = team; m.team.match.save(failOnError: true); matchSymbols.remove(m.team.match.matchId) }
        }
        return team
    }

    /** The winner of the player's match (works after ENDED, unlike the active-only seams). */
    String winnerForUser(String username) {
        String w = null
        ClusterMatch.withTransaction {
            def mem = ClusterMembership.findAllByUsername(username)?.find { true }
            w = mem?.team?.match?.winner
        }
        return w
    }

    /** Elemental symbols an entity is currently carrying (held on the membership). */
    Set<String> heldSymbolsOf(String username) {
        Set<String> out = [] as Set
        ClusterMatch.withTransaction {
            def m = findActiveMembership(username)
            out = parseSymbols(m?.heldSymbols)
        }
        return out
    }

    /** Grant a symbol to an entity (collection seam; cluster pickup wires here later). */
    void grantSymbol(String username, String symbol) {
        ClusterMatch.withTransaction {
            def m = findActiveMembership(username)
            if (m) addHeldSymbol(m, symbol)
        }
    }

    // Single place that appends a symbol to a membership's CSV (DRY: grantSymbol + collect-on-arrival).
    private void addHeldSymbol(ClusterMembership m, String symbol) {
        def s = parseSymbols(m.heldSymbols); s << symbol.toUpperCase()
        m.heldSymbols = s.join(','); m.save(failOnError: true)
    }

    // --- on-board symbols (cluster completability) -----------------------------------------------

    /** Place/seed a symbol at a coordinate in a match (seam for tests + auto-seed at start). */
    void placeSymbol(String matchId, String symbol, int x, int y) {
        matchSymbols.computeIfAbsent(matchId, { new ConcurrentHashMap<>() }).put(symbol.toUpperCase(), [x, y])
    }

    /** Seed all 4 elemental symbols at spread, findable coordinates (called when a match starts). */
    void placeSymbols(String matchId) {
        def rnd = new Random(matchId.hashCode() + 7)
        SYMBOLS.each { placeSymbol(matchId, it, 1 + rnd.nextInt(8), 1 + rnd.nextInt(8)) }
    }

    /** Uncollected symbols still on the board for a match: [[symbol, x, y], ...]. */
    List<Map> uncollectedSymbolsFor(String matchId) {
        def syms = matchSymbols[matchId]
        return syms ? syms.collect { k, v -> [symbol: k, x: v[0], y: v[1]] } : []
    }

    /** The symbol sitting on (x,y) in a match, or null. */
    String symbolAt(String matchId, int x, int y) {
        return matchSymbols[matchId]?.find { k, v -> v[0] == x && v[1] == y }?.key
    }

    /**
     * Scan readout for a cluster Lambda: the elemental field reveals where the uncollected symbols
     * are (the objective). Movement is dice-paced, so this is a fair race — walk onto one to collect.
     */
    String symbolHintFor(String username) {
        String hint = ''
        ClusterMatch.withTransaction {
            def m = findActiveMembership(username)
            if (m && m.role == 'CLASSIC_LAMBDA' && m.team.match.state == 'ACTIVE') {
                def held = parseSymbols(m.heldSymbols)
                def need = uncollectedSymbolsFor(m.team.match.matchId).findAll { !held.contains(it.symbol) }.sort { it.symbol }
                if (need) {
                    hint = "\r\n" + TerminalFormatter.formatText("ELEMENTAL FIELD — symbols you still need (walk onto one to collect):", 'bold', 'magenta') + "\r\n" +
                           need.collect { "  ⚡ ${it.symbol} at (${it.x},${it.y})" }.join("\r\n") + "\r\n"
                } else {
                    hint = "\r\n" + TerminalFormatter.formatText("⚡ All 4 symbols gathered — type 'invoke' to defeat the Logic Daemon and WIN!", 'bold', 'green') + "\r\n"
                }
            }
        }
        return hint
    }

    /** Relocate a symbol to a fresh in-bounds coord (the design's "dynamic relocation" — keeps the race on). */
    void relocateSymbol(String matchId, String symbol) {
        def syms = matchSymbols[matchId]
        def cur = syms?.get(symbol)
        if (!cur) return
        def rnd = new Random((matchId + symbol).hashCode() + (relocSeq++))
        int nx = cur[0], ny = cur[1], guard = 0
        while (nx == cur[0] && ny == cur[1] && guard++ < 30) { nx = rnd.nextInt(10); ny = rnd.nextInt(10) }
        syms.put(symbol, [nx, ny])
    }

    /** Move one symbol from caster to target (both in the same match). Returns [ok, reason]. */
    Map transferSymbol(String casterUsername, String targetUsername, String symbol) {
        Map res = [ok: false, reason: '']
        String sym = symbol?.toUpperCase()
        if (!(sym in SYMBOLS)) { res.reason = "Unknown symbol '${symbol}'."; return res }
        ClusterMatch.withTransaction {
            def from = findActiveMembership(casterUsername)
            def to = findActiveMembership(targetUsername)
            if (!from || !to) { res.reason = 'Entity not found.'; return }
            def fs = parseSymbols(from.heldSymbols)
            if (!fs.contains(sym)) { res.reason = "You are not carrying the ${sym} symbol."; return }
            fs.remove(sym); from.heldSymbols = fs.join(','); from.save(failOnError: true)
            def ts = parseSymbols(to.heldSymbols); ts << sym; to.heldSymbols = ts.join(','); to.save(failOnError: true)
            res.ok = true
        }
        return res
    }

    private static Set<String> parseSymbols(String csv) {
        return (csv ? csv.split(',').findAll { it } : []) as Set
    }

    /** Resolve a typed target name to an actual member username in the caster's match (case-insensitive). */
    String resolveTargetUsername(String casterUsername, String targetName) {
        String out = null
        ClusterMatch.withTransaction {
            def m = findActiveMembership(casterUsername)
            def hit = m?.team?.match?.teams?.collectMany { it.members }?.find { it.username.equalsIgnoreCase(targetName) }
            out = hit?.username
        }
        return out
    }

    /** True when the player is in an ACTIVE cluster match (drives real-time movement + position sync). */
    boolean isInActiveMatch(String username) {
        boolean active = false
        ClusterMatch.withTransaction {
            def m = findActiveMembership(username)
            active = (m != null && m.team.match.state == 'ACTIVE')
        }
        return active
    }

    // O(1) sub-command dispatch — no switch/if-ladder (Doctrine #2).
    private final Map<String, Closure> clusterSubcommands = [
        'create': { LambdaPlayer p -> createMatch(p) },
        'join'  : { LambdaPlayer p -> joinMatch(p) },
        'start' : { LambdaPlayer p -> startMatch(p) },
        'status': { LambdaPlayer p -> getClusterStatus(p) },
        'leave' : { LambdaPlayer p -> leaveMatch(p) },
    ]

    /** One-line delegate target for commandHandlers['cluster']. */
    String handleClusterCommand(String command, LambdaPlayer player, PrintWriter writer) {
        def parts = command?.trim()?.toLowerCase()?.split(/\s+/) ?: []
        def sub = parts.length > 1 ? parts[1] : ''
        def handler = clusterSubcommands[sub]
        return handler ? (handler.call(player) as String) : clusterUsage()
    }

    String createMatch(LambdaPlayer player) {
        String result
        ClusterMatch.withTransaction {
            if (findActiveMembership(player.username)) {
                result = warn("You are already in a cluster match — 'cluster status' or 'cluster leave'.")
                return
            }
            def match = new ClusterMatch(matchId: genMatchId())
            def alpha = new ClusterTeam(name: 'ALPHA')
            def beta = new ClusterTeam(name: 'BETA')
            match.addToTeams(alpha); match.addToTeams(beta)
            match.save(failOnError: true)
            def m = new ClusterMembership(username: player.username, role: player.avatarSilhouette)
            alpha.addToMembers(m); alpha.save(failOnError: true)
            result = renderMembershipPanel(m, 'Cluster Match Created')
        }
        return result
    }

    String joinMatch(LambdaPlayer player) {
        String result
        ClusterMatch.withTransaction {
            if (findActiveMembership(player.username)) {
                result = warn("You are already in a cluster match — 'cluster status' or 'cluster leave'.")
                return
            }
            def match = ClusterMatch.findByState('LOBBY', [sort: 'createdDate', order: 'desc'])
            if (!match) { result = warn("No open cluster lobby. Use 'cluster create' to start one."); return }
            def alpha = match.teams.find { it.name == 'ALPHA' }
            def beta = match.teams.find { it.name == 'BETA' }
            // PIECE 1 seating: fill ALPHA, then BETA. Balanced, role-unique seating arrives with
            // bot-fill in PIECE 2; for now this just needs to form a team and seat the player.
            def team = (alpha.members?.size() ?: 0) < TEAM_CAP ? alpha : beta
            def m = new ClusterMembership(username: player.username, role: player.avatarSilhouette)
            team.addToMembers(m); team.save(failOnError: true)
            assignTrueDecoy(team)
            result = renderMembershipPanel(m, 'Joined Cluster Match')
        }
        return result
    }

    String getClusterStatus(LambdaPlayer player) {
        String result
        ClusterMatch.withTransaction {
            def m = findActiveMembership(player.username)
            result = m ? renderMembershipPanel(m, 'Cluster Status')
                       : warn("Not in a cluster match. Use 'cluster create' or 'cluster join'.")
        }
        return result
    }

    String leaveMatch(LambdaPlayer player) {
        String result
        ClusterMatch.withTransaction {
            def m = findActiveMembership(player.username)
            if (!m) { result = info("You are not in a cluster match."); return }
            def team = m.team
            team.removeFromMembers(m); m.delete(failOnError: true)
            result = info("Left the cluster match.")
        }
        return result
    }

    // Fill empty seats with bots so a solo human still gets a real 7v7, then lock in true/decoy per
    // team and flip the match ACTIVE. This is the match-start gate (LOBBY → ACTIVE).
    String startMatch(LambdaPlayer player) {
        String result
        ClusterMatch.withTransaction {
            def m = findActiveMembership(player.username)
            if (!m) { result = warn("Not in a cluster match. Use 'cluster create' first."); return }
            def match = m.team.match
            if (match.state != 'LOBBY') { result = warn("Match ${match.matchId} is already ${match.state}."); return }

            match.teams.each { team -> fillTeamWithBots(team) }   // humans keep their seats; bots fill the rest
            match.teams.each { team -> assignTrueDecoy(team) }    // exactly one true Lambda per full team
            match.state = 'ACTIVE'
            match.save(failOnError: true)
            placeSymbols(match.matchId)   // seed the 4 elemental symbols on the board to race for

            result = renderMembershipPanel(m, 'Match Started — ACTIVE')
        }
        return result
    }

    /**
     * Occupancy rule for entering (x,y) in a cluster match: max 4 entities per coordinate, max 2 per
     * team — except a Geo (Scout) may take a 5th slot. Returns [allowed, reason]. allowed=true when
     * the player is not in an active match (Node movement is unaffected). The board owns the board:
     * CoordinateStateService calls this hook on move; this method only computes the rule.
     */
    Map occupancyCheck(String username, Integer x, Integer y) {
        Map res = [allowed: true, reason: '']
        ClusterMatch.withTransaction {
            def m = findActiveMembership(username)
            if (!m || m.team.match.state != 'ACTIVE') return    // not an active cluster → no rule
            if (m.role == 'GEOMETRIC_ENTITY') return             // Geo (Scout) ignores occupancy — squeeze-in
            def others = m.team.match.teams.collectMany { it.members }
                .findAll { it.username != username && it.positionX == x && it.positionY == y }
            int sameTeam = others.count { it.team.id == m.team.id }
            if (sameTeam >= 2) {
                res.allowed = false; res.reason = "Coordinate (${x},${y}) already holds 2 of your team."
            } else if (others.size() >= 4) {
                res.allowed = false; res.reason = "Coordinate (${x},${y}) is full (4) — only a Geo may squeeze in."
            }
        }
        return res
    }

    // --- internals -------------------------------------------------------------------------------

    // Add bot members until the team matches the canonical composition (multiset difference vs the
    // human/bot seats already present). isBot=true; role drives the bot's behavior in later pieces.
    private void fillTeamWithBots(ClusterTeam team) {
        def need = new ArrayList<String>(CANON_TEAM)
        team.members?.each { mem -> need.remove(mem.role) }   // remove ONE canon slot per existing seat
        int n = 0
        def tag = team.name[0] + (team.match.matchId.takeRight(3))   // e.g. A640 — short + match-scoped
        need.each { role ->
            team.addToMembers(new ClusterMembership(
                username: "bot_${tag}${++n}",   // bot_A6401 … short, readable, fits the HUD panel
                role: role, isBot: true))
        }
        team.save(failOnError: true)
    }

    private ClusterMembership findActiveMembership(String username) {
        ClusterMembership.findAllByUsername(username).find { it.team?.match?.state != 'ENDED' }
    }

    // Once a team has its 2 Lambdas, flag exactly one as the true Lambda (the other is the decoy).
    // Fixed at this point; data-driven pick, no conditional ladder.
    private void assignTrueDecoy(ClusterTeam team) {
        def lambdas = team.members?.findAll { it.role == 'CLASSIC_LAMBDA' } ?: []
        if (lambdas.size() >= 2 && !lambdas.any { it.isTrueLambda }) {
            // A HUMAN Lambda is always the true one (so a solo human has real agency — they can win,
            // and a bot plays the decoy). Among two humans (full multiplayer) the true one is random.
            def pool = lambdas.findAll { !it.isBot } ?: lambdas
            def chosen = pool[new Random().nextInt(pool.size())]
            chosen.isTrueLambda = true
            chosen.save(failOnError: true)
        }
    }

    private String genMatchId() {
        "CM_${System.currentTimeMillis().toString().takeRight(8)}_${new Random().nextInt(900) + 100}"
    }

    // Single renderer shared by create/join/status (DRY). `m` is the VIEWER's membership, so the
    // true/decoy line is self-only; the roster lists names+roles but never another player's bit.
    private String renderMembershipPanel(ClusterMembership m, String title) {
        def team = m.team
        def match = team.match
        def box = new BoxBuilder(PANEL_WIDTH)
            .addCenteredLine(TerminalFormatter.formatText("⬡ ${title}", 'bold', 'cyan'))
            .addSeparator()
            .addLine("  Match: ${match.matchId}")
            .addLine("  State: ${match.state}   Team: ${team.name}")
            .addLine("  Role:  ${roleLabel(m.role)}")

        if (m.role == 'CLASSIC_LAMBDA') {
            def lambdas = team.members.findAll { it.role == 'CLASSIC_LAMBDA' }
            if (lambdas.any { it.isTrueLambda }) {
                box.addLine(m.isTrueLambda
                    ? "  ${TerminalFormatter.formatText('You are the TRUE Lambda (only you can win).', 'bold', 'green')}"
                    : "  ${TerminalFormatter.formatText("You are the DECOY (you can't cash symbols in).", 'bold', 'yellow')}")
            } else {
                box.addLine("  Lambda — awaiting the 2nd Lambda (true/decoy).")
            }
            // Carried symbols (the race progress) — invoke at 4 to win.
            def held = parseSymbols(m.heldSymbols)
            box.addLine("  Symbols: ${held ? held.join(', ') : 'none'} (${held.size()}/4)")
        }

        box.addEmptyLine().addLine("  Team roster:")
        team.members.sort { it.username }.each { mem ->
            box.addLine("   - ${mem.username}  -  ${roleLabel(mem.role)}")
        }
        return box.build() + "\r\n"
    }

    private String clusterUsage() {
        def box = new BoxBuilder(PANEL_WIDTH)
            .addCenteredLine(TerminalFormatter.formatText("⬡ CLUSTER MODE", 'bold', 'cyan'))
            .addSeparator()
            .addLine("  cluster create - new 7v7 match (TEAM ALPHA)")
            .addLine("  cluster join   - join an open lobby")
            .addLine("  cluster start  - fill with bots and begin")
            .addLine("  cluster status - match, team, role, identity")
            .addLine("  cluster leave  - leave your match")
        return box.build() + "\r\n"
    }

    private String warn(String msg) { TerminalFormatter.formatText(msg, 'bold', 'yellow') + "\r\n" }
    private String info(String msg) { TerminalFormatter.formatText(msg, 'italic', 'cyan') + "\r\n" }

    // --- package-visible test seams (mirrors telnetServerService.playerSessions assertions) -------

    /** The player's active cluster membership as a plain map, or null. */
    Map clusterStateFor(String username) {
        Map out = null
        ClusterMatch.withTransaction {
            def m = findActiveMembership(username)
            if (m) out = [matchId: m.team.match.matchId, team: m.team.name, role: m.role,
                          isTrueLambda: m.isTrueLambda, state: m.team.match.state]
        }
        return out
    }

    /** Match-level summary for assertions: state, per-team size, per-team true-Lambda count, bot count. */
    Map matchSummaryFor(String username) {
        Map out = null
        ClusterMatch.withTransaction {
            def m = findActiveMembership(username)
            if (m) {
                def match = m.team.match
                out = [state: match.state,
                       teamSizes: match.teams.collectEntries { [(it.name): it.members.size()] },
                       trueLambdasPerTeam: match.teams.collectEntries { [(it.name): it.members.count { it.isTrueLambda }] },
                       bots: match.teams.sum { it.members.count { it.isBot } } as int]
            }
        }
        return out
    }

    /** Count of TRUE Lambdas on the player's team — must be exactly 1 once two Lambdas exist. */
    int trueLambdaCountOnTeamOf(String username) {
        int n = 0
        ClusterMatch.withTransaction {
            def m = findActiveMembership(username)
            if (m) n = (m.team.members.count { it.isTrueLambda }) as int
        }
        return n
    }

    /**
     * Raw enemy roster facts (role + position) for the viewer's opponents. Deliberately omits the
     * true/decoy bit so it cannot leak through tracking; the firewall rules (ghost-exclusion, no bit)
     * are applied by ClusterRoleService, which owns the firewall.
     */
    List<Map> enemyRosterFacts(String username) {
        List<Map> out = []
        ClusterMatch.withTransaction {
            def m = findActiveMembership(username)
            def enemy = m?.team?.match?.teams?.find { it.id != m.team.id }
            enemy?.members?.sort { it.username }?.each { out << [role: it.role, x: it.positionX, y: it.positionY] }
        }
        return out
    }

    /** A member username with `role` on the player's team (sameTeam=true) or the enemy team. Seam. */
    String memberWithRole(String username, String role, boolean sameTeam) {
        String out = null
        ClusterMatch.withTransaction {
            def m = findActiveMembership(username)
            def team = !m ? null : (sameTeam ? m.team : m.team.match.teams.find { it.id != m.team.id })
            out = team?.members?.sort { it.username }?.find { it.role == role }?.username
        }
        return out
    }

    /** All member facts for a match (pure query for the bot AI tick — no behavior). */
    List<Map> botFactsForMatch(String matchId) {
        List<Map> out = []
        ClusterMatch.withTransaction {
            def match = ClusterMatch.findByMatchId(matchId)
            match?.teams?.each { t ->
                t.members.each { m ->
                    out << [team: t.name, username: m.username, role: m.role,
                            x: m.positionX, y: m.positionY, isBot: m.isBot,
                            isTrueLambda: m.isTrueLambda, heldCount: parseSymbols(m.heldSymbols).size()]
                }
            }
        }
        return out
    }

    /** Match ids currently ACTIVE (the bot scheduler iterates these). */
    List<String> activeMatchIds() {
        List<String> out = []
        ClusterMatch.withTransaction { out = ClusterMatch.findAllByState('ACTIVE').collect { it.matchId } }
        return out
    }

    /** A member's current board position as [x, y] (null entries if unset). */
    Map memberPositionOf(String username) {
        Map out = null
        ClusterMatch.withTransaction {
            def m = findActiveMembership(username)
            if (m) out = [x: m.positionX, y: m.positionY]
        }
        return out
    }

    /** Set a member's board position (sync seam — used by tests + the movement sync in PIECE 8). */
    void setMemberPosition(String username, Integer x, Integer y) {
        ClusterMatch.withTransaction {
            def m = findActiveMembership(username)
            if (!m) return
            m.positionX = x; m.positionY = y; m.save(failOnError: true)
            // Collect-on-arrival: a Lambda (true OR decoy) landing on a symbol grabs it; the symbol
            // then relocates so the other Lambda/team keeps racing. Shared by humans AND bots (both
            // move through this seam) — one path, no duplication. No-op for non-Lambdas / Node.
            if (m.role == 'CLASSIC_LAMBDA' && x != null && y != null && m.team.match.state == 'ACTIVE') {
                def matchId = m.team.match.matchId
                def sym = symbolAt(matchId, x, y)
                // Only collect a symbol you don't already hold; it then relocates so others can race
                // for a fresh one (and your already-held targets stay put for you to navigate to).
                if (sym && !parseSymbols(m.heldSymbols).contains(sym)) { addHeldSymbol(m, sym); relocateSymbol(matchId, sym) }
            }
        }
    }

    /** A member username on the OPPOSING team (firewall enemy-view tests), or null. */
    String anEnemyMemberUsername(String username) {
        String out = null
        ClusterMatch.withTransaction {
            def m = findActiveMembership(username)
            def enemyTeam = m?.team?.match?.teams?.find { it.id != m.team.id }
            out = enemyTeam?.members?.collect { it.username }?.sort()?.find { true }
        }
        return out
    }

    /** The Lambda usernames on the player's team (the true + decoy), for firewall tests. */
    List<String> lambdaUsernamesOnTeamOf(String username) {
        List<String> out = []
        ClusterMatch.withTransaction {
            def m = findActiveMembership(username)
            if (m) out = m.team.members.findAll { it.role == 'CLASSIC_LAMBDA' }.collect { it.username }.sort()
        }
        return out
    }
}
