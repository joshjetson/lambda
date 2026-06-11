package ysap

import ysap.helpers.BoxBuilder
import java.util.concurrent.ConcurrentHashMap

/**
 * Cluster Mode information + abilities: the enemy-view identity firewall (here) and, in later pieces,
 * the role-keyed team verbs (scan all / spam / lock / deploy bot / transfer). Bounded to "what an
 * observer is allowed to know / do" — match truth lives in ClusterMatchService.
 *
 * THE FIREWALL (load-bearing invariant): to an enemy, the two Lambdas are indistinguishable — same
 * role-class, no true/decoy bit. Only a teammate sees which of their own Lambdas is the true one.
 * Every enemy-facing readout of a member MUST go through firewallViewOf so the bit can never leak.
 */
class ClusterRoleService {

    static final long SCAN_COOLDOWN_MS = 8000
    static final long LOCK_MS = 60000
    static final long DEPLOY_COOLDOWN_MS = 5000
    static final long SIPHON_COOLDOWN_MS = 30000   // siphon denies progress → rarer than spam/deploy
    private final Map<String, Long> lastScanAt = new ConcurrentHashMap<>()
    private final Map<String, Long> lastDeployAt = new ConcurrentHashMap<>()
    private final Map<String, Long> lastSiphonAt = new ConcurrentHashMap<>()

    // Each role's signature active team verb (Geo's ability is passive: trap-immunity + squeeze).
    static final Map<String, String> ROLE_VERBS = [
        'CIRCUIT_PATTERN': 'scan all',
        'FLOWING_CURRENT': 'lock',
        'DIGITAL_GHOST'  : 'spam',
        'BINARY_FORM'    : 'deploy',
        'CLASSIC_LAMBDA' : 'transfer',
    ].asImmutable()

    def clusterMatchService
    def telnetServerService
    def defragBotService

    /** Test seam: clear scan cooldowns so a Circuit can sweep immediately. */
    void clearScanCooldowns() { lastScanAt.clear() }

    /**
     * `scan all` — the Circuit (Tracker) team ability: enemy positions + role-class, Ghosts excluded,
     * Lambdas shown generically (the true/decoy bit never reaches the facts), on a cooldown. Returns
     * null when the player is NOT in an active cluster match, so the caller falls back to normal scan.
     * The single deduction SENSOR: it reveals LOCATION, never identity.
     */
    String scanAll(LambdaPlayer player) { scanAllFor(player.username) }

    /** Username-keyed core. Test seam: bots have no LambdaPlayer, so tests drive a bot Circuit by name. */
    String scanAllFor(String username) {
        def me = clusterMatchService.clusterStateFor(username)
        if (!me || me.state != 'ACTIVE') return null   // not an active cluster → normal scan handles it

        if (me.role != 'CIRCUIT_PATTERN') {
            return TerminalFormatter.formatText("'scan all' is the Circuit (Tracker) team ability.", 'bold', 'yellow') + "\r\n"
        }
        long now = System.currentTimeMillis()
        Long last = lastScanAt[username]
        if (last != null && now - last < SCAN_COOLDOWN_MS) {
            int wait = (((SCAN_COOLDOWN_MS - (now - last)) / 1000) + 1) as int
            return TerminalFormatter.formatText("Tracker array recharging — ${wait}s.", 'italic', 'cyan') + "\r\n"
        }
        lastScanAt[username] = now

        // Firewall applied here (the firewall owner): Ghosts are invisible to tracking; role-class is
        // shown; the true/decoy bit is never present in the facts, so a Lambda reads generically.
        def visible = clusterMatchService.enemyRosterFacts(username).findAll { it.role != 'DIGITAL_GHOST' }
        def box = new BoxBuilder(ClusterMatchService.PANEL_WIDTH)
            .addCenteredLine(TerminalFormatter.formatText("⊹ TRACKER SWEEP", 'bold', 'cyan'))
            .addSeparator()
        if (visible.isEmpty()) {
            box.addLine("  No trackable enemies (Ghosts run dark).")
        } else {
            visible.each { e ->
                def pos = (e.x != null && e.y != null) ? "(${e.x},${e.y})" : "(?,?)"
                box.addLine("  ${pos.padRight(8)} ${ClusterMatchService.roleLabel(e.role)}")
            }
        }
        appendProtectionRead(box, visible)
        box.addEmptyLine()
            .addLine("  Ghosts run dark. Λ identity is hidden —")
            .addLine("  deduce it from the geometry.")
        return box.build() + "\r\n"
    }

    // PIECE 5 — leak signal #1 (protection geometry): for each enemy Λ, how many enemy allies are
    // clustered near it (Chebyshev radius 2). More escorts ≈ likelier the real Lambda. This is the
    // RELATIVE correlation the design calls for — a read, never an answer.
    private void appendProtectionRead(BoxBuilder box, List<Map> visible) {
        def lambdas = visible.findAll { it.role == 'CLASSIC_LAMBDA' && it.x != null && it.y != null }
        def allies  = visible.findAll { it.role != 'CLASSIC_LAMBDA' && it.x != null && it.y != null }
        if (!lambdas) return
        box.addEmptyLine().addLine("  Protection read (allies within 2 of each Λ):")
        lambdas.eachWithIndex { L, i ->
            int near = allies.count { Math.max(Math.abs((it.x - L.x) as int), Math.abs((it.y - L.y) as int)) <= 2 }
            box.addLine("   Λ#${i + 1} @ (${L.x},${L.y}): ${near} nearby ally(ies)")
        }
    }

    // --- team verbs (PIECE 9: lock / spam) -------------------------------------------------------

    String invokeForCluster(LambdaPlayer player) { invokeForClusterBy(player.username) }

    /**
     * Cluster `invoke` — the climactic win gate. ONLY the true Lambda holding all 4 symbols wins;
     * the decoy is refused even with all 4 (it knows — this never reveals identity to an enemy).
     * Returns null outside an active match so the Node daemon path handles it.
     */
    String invokeForClusterBy(String username) {
        def me = clusterMatchService.clusterStateFor(username)
        if (!me || me.state != 'ACTIVE') return null
        if (me.role != 'CLASSIC_LAMBDA') return warn("Only a Lambda can invoke the Logic Daemon.")
        if (!me.isTrueLambda) {
            return TerminalFormatter.formatText("🌀 The symbols do not answer — you are the DECOY. Only the true Lambda can harness them.", 'bold', 'yellow') + "\r\n"
        }
        def held = clusterMatchService.heldSymbolsOf(username)
        def missing = ClusterMatchService.SYMBOLS.findAll { !held.contains(it) }
        if (missing) return warn("You need all 4 symbols to invoke — missing: ${missing.join(', ')} (${held.size()}/4).")
        def team = clusterMatchService.declareWin(username)
        return TerminalFormatter.formatText("🏆 LOGIC DAEMON DEFEATED — TEAM ${team} WINS! The true Lambda harnessed all 4 symbols and escaped the system.", 'bold', 'green') + "\r\n"
    }

    String lockTarget(LambdaPlayer caster, String targetName) { lockTargetFor(caster.username, targetName) }
    String spamTarget(LambdaPlayer caster, String targetName) { spamTargetFor(caster.username, targetName) }
    String deployBot(LambdaPlayer caster, Integer x, Integer y) { deployBotFor(caster.username, x, y) }
    String transferSymbol(LambdaPlayer caster, String symbol, String targetName) { transferFor(caster.username, symbol, targetName) }
    String siphonFrom(LambdaPlayer caster, String targetName) { siphonFromFor(caster.username, targetName) }

    /** Test seam: clear siphon cooldowns so a Ghost can siphon immediately. */
    void clearSiphonCooldowns() { lastSiphonAt.clear() }

    /** Shared cooldown gate so the human verb AND bot Saboteurs draw from one source (no chain-drain). */
    boolean siphonReady(String casterUsername) {
        Long last = lastSiphonAt[casterUsername]
        return last == null || (System.currentTimeMillis() - last) >= SIPHON_COOLDOWN_MS
    }
    void markSiphon(String casterUsername) { lastSiphonAt[casterUsername] = System.currentTimeMillis() }

    /** Binary's `deploy bot <x> <y>`: drop a defrag bot to block/guard a coordinate. Cooldown-gated. */
    String deployBotFor(String casterUsername, Integer x, Integer y) {
        def me = clusterMatchService.clusterStateFor(casterUsername)
        if (!me || me.state != 'ACTIVE') return null
        if (me.role != 'BINARY_FORM') return warn("'deploy bot' is the Binary (Trapper) team ability.")
        if (x == null || y == null || x < 0 || x > 9 || y < 0 || y > 9) return warn("Usage: deploy bot <x> <y> (0-9).")
        long now = System.currentTimeMillis()
        Long last = lastDeployAt[casterUsername]
        if (last != null && now - last < DEPLOY_COOLDOWN_MS) {
            return TerminalFormatter.formatText("Bot factory recharging — ${(((DEPLOY_COOLDOWN_MS-(now-last))/1000)+1) as int}s.", 'italic', 'cyan') + "\r\n"
        }
        def bot = defragBotService.spawnDefragBot(1, 1, x, y)   // reuse the existing combat bot
        if (!bot) return warn("Cannot deploy at (${x},${y}) — too close to spawn.")
        lastDeployAt[casterUsername] = now
        return TerminalFormatter.formatText("🤖 Deployed defrag bot ${bot.botId} at (${x},${y}).", 'bold', 'green') + "\r\n"
    }

    /** Lambda's `transfer <symbol> <teammate>`: hand a held symbol to a teammate carrier (the football). */
    String transferFor(String casterUsername, String symbol, String targetName) {
        def me = clusterMatchService.clusterStateFor(casterUsername)
        if (!me || me.state != 'ACTIVE') return null
        if (me.role != 'CLASSIC_LAMBDA') return warn("'transfer' is the Lambda ability (pass a symbol to a carrier).")
        def target = clusterMatchService.resolveTargetUsername(casterUsername, targetName)
        if (!target) return warn("No entity named '${targetName}' in this match.")
        def them = clusterMatchService.clusterStateFor(target)
        if (them.team != me.team) return warn("You can only pass to a teammate.")
        def r = clusterMatchService.transferSymbol(casterUsername, target, symbol)
        if (!r.ok) return warn(r.reason)
        pushTo(target, TerminalFormatter.formatText("\r\n🏈 You are now carrying the ${symbol.toUpperCase()} symbol — guard it.", 'bold', 'cyan'))
        return TerminalFormatter.formatText("🏈 Passed ${symbol.toUpperCase()} to ${target}.", 'bold', 'green') + "\r\n"
    }

    /** Current's `lock`: immobilize an adjacent target for 60s. Returns null outside an active match. */
    String lockTargetFor(String casterUsername, String targetName) {
        def gate = verbGate(casterUsername, 'FLOWING_CURRENT', "'lock' is the Current (Disruptor) team ability.", targetName)
        if (gate.fail) return gate.msg
        String target = gate.target
        clusterMatchService.applyLock(target, LOCK_MS)
        pushTo(target, TerminalFormatter.formatText("\r\n⚡ ELECTRIC-LOCKED — you cannot move for 60s.", 'bold', 'red'))
        return TerminalFormatter.formatText("⚡ Locked ${target} for 60 seconds.", 'bold', 'green') + "\r\n"
    }

    /** Ghost's `spam`: flood an adjacent target's terminal with junk. Returns null outside a match. */
    String spamTargetFor(String casterUsername, String targetName) {
        def gate = verbGate(casterUsername, 'DIGITAL_GHOST', "'spam' is the Ghost (Saboteur) team ability.", targetName)
        if (gate.fail) return gate.msg
        String target = gate.target
        def junk = new StringBuilder("\r\n")
        20.times { junk.append(TerminalFormatter.formatText("0xDEADBEEF ▓░▒ stack overflow ${System.nanoTime() % 99999}", 'bold', 'magenta')).append("\r\n") }
        pushTo(target, junk.toString())
        return TerminalFormatter.formatText("👻 Flooded ${target}'s terminal.", 'bold', 'green') + "\r\n"
    }

    /**
     * Ghost's `siphon <target>`: the Saboteur's real teeth — strip one symbol off an adjacent ENEMY
     * Lambda and scatter it back to the matrix (denial). The referee mutation is identity-blind, so
     * siphoning the decoy succeeds identically (the bluff working = you "wasted" it). Cooldown-gated.
     */
    String siphonFromFor(String casterUsername, String targetName) {
        def gate = verbGate(casterUsername, 'DIGITAL_GHOST', "'siphon' is the Ghost (Saboteur) team ability.", targetName)
        if (gate.fail) return gate.msg
        String target = gate.target
        if (!siphonReady(casterUsername)) {
            long wait = ((SIPHON_COOLDOWN_MS - (System.currentTimeMillis() - lastSiphonAt[casterUsername])) / 1000) + 1
            return TerminalFormatter.formatText("Siphon coil recharging — ${wait as int}s.", 'italic', 'cyan') + "\r\n"
        }
        // Identity-blind: null means "no siphonable symbol" — same text whether decoy-with-none or non-Lambda.
        def stolen = clusterMatchService.siphonSymbolFrom(target)
        if (!stolen) return warn("${target} is carrying no siphonable symbol.")
        markSiphon(casterUsername)
        pushTo(target, TerminalFormatter.formatText("\r\n🩸 The ${stolen} symbol was siphoned from you and scattered back to the matrix.", 'bold', 'red'))
        return TerminalFormatter.formatText("👻 Siphoned ${stolen} from ${target} — scattered back to the board.", 'bold', 'green') + "\r\n"
    }

    // Shared verb gate: in an ACTIVE match, caster has the right role, target resolves (case-insensitive),
    // isn't self, and is on an adjacent coordinate. Returns [fail, msg, target].
    private Map verbGate(String casterUsername, String requiredRole, String wrongRoleMsg, String targetName) {
        def me = clusterMatchService.clusterStateFor(casterUsername)
        if (!me || me.state != 'ACTIVE') return [fail: true, msg: null]   // not a cluster verb → caller handles
        if (me.role != requiredRole) return [fail: true, msg: warn(wrongRoleMsg)]
        if (!targetName) return [fail: true, msg: warn("Name a target.")]
        def target = clusterMatchService.resolveTargetUsername(casterUsername, targetName)
        if (!target) return [fail: true, msg: warn("No entity named '${targetName}' in this match.")]
        if (target.equalsIgnoreCase(casterUsername)) return [fail: true, msg: warn("You cannot target yourself.")]
        if (!adjacent(casterUsername, target)) return [fail: true, msg: warn("${target} is not on an adjacent coordinate.")]
        return [fail: false, msg: null, target: target]
    }

    private boolean adjacent(String a, String b) {
        def pa = clusterMatchService.memberPositionOf(a); def pb = clusterMatchService.memberPositionOf(b)
        if (pa?.x == null || pa?.y == null || pb?.x == null || pb?.y == null) return false
        return Math.max(Math.abs((pa.x - pb.x) as int), Math.abs((pa.y - pb.y) as int)) <= 1
    }

    private void pushTo(String username, String text) {
        try {
            def w = telnetServerService.writerForUsername(username)
            if (w) { w.print(text); w.flush() }
        } catch (Exception ignored) { /* target offline / bot → no-op */ }
    }

    private String warn(String msg) { TerminalFormatter.formatText(msg, 'bold', 'yellow') + "\r\n" }

    /**
     * What `viewerUsername` is allowed to see about `targetUsername`. Role-class is always visible
     * (Circuit's tracking reveals it); the Lambda true/decoy bit is visible ONLY to a teammate.
     * For an enemy, both Lambdas return the identical view (lambda: 'HIDDEN') — they cannot be told
     * apart by any direct channel. Returns null if either entity is not in an active match.
     */
    Map firewallViewOf(String viewerUsername, String targetUsername) {
        def viewer = clusterMatchService.clusterStateFor(viewerUsername)
        def target = clusterMatchService.clusterStateFor(targetUsername)
        if (!viewer || !target) return null

        boolean sameTeam = (viewer.matchId == target.matchId) && (viewer.team == target.team)
        def view = [role: ClusterMatchService.roleLabel(target.role)]   // role-class: always visible
        if (target.role == 'CLASSIC_LAMBDA') {
            // Identity leaks to teammates only; enemies see a generic, indistinguishable Λ.
            view.lambda = sameTeam ? (target.isTrueLambda ? 'TRUE' : 'DECOY') : 'HIDDEN'
        }
        return view
    }
}
