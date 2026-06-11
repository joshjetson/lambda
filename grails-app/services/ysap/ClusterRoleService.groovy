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
    private final Map<String, Long> lastScanAt = new ConcurrentHashMap<>()

    def clusterMatchService

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
        box.addEmptyLine().addLine("  Ghosts are invisible to the array. Λ identity is NOT revealed — deduce it.")
        return box.build() + "\r\n"
    }

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
