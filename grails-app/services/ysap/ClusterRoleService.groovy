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
    private final Map<String, Long> lastScanAt = new ConcurrentHashMap<>()

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
        def box = new BoxBuilder(64)
            .addCenteredLine(TerminalFormatter.formatText("⊹ TRACKER SWEEP — enemy positions", 'bold', 'cyan'))
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
            .addLine("  Ghosts run dark (invisible to the array).")
            .addLine("  Λ identity is NOT revealed — deduce it from the geometry.")
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

    String lockTarget(LambdaPlayer caster, String targetName) { lockTargetFor(caster.username, targetName) }
    String spamTarget(LambdaPlayer caster, String targetName) { spamTargetFor(caster.username, targetName) }

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
