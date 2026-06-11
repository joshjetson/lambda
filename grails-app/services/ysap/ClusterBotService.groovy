package ysap

import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * Cluster bot autonomy — the only concern here is making bot seats ALIVE: spawn positions and a
 * per-role intent tick that moves them one step at a time. Match TRUTH stays in ClusterMatchService
 * (this reads/writes via its position seams, never the domains directly); abilities/firewall stay in
 * ClusterRoleService. Mirrors AutoDefragService's scheduler shape; the timer is THIN — it just calls
 * the synchronously-testable advanceBots(matchId), so the behavior is driven deterministically in tests.
 *
 * Additive: bots only move in ACTIVE matches; Node/FFA is untouched.
 */
class ClusterBotService {

    def clusterMatchService

    private final ScheduledExecutorService botScheduler = Executors.newScheduledThreadPool(1)

    // O(1) role → "where this bot wants to be" (a target [x,y] or null = hold). Open/closed: later
    // pieces swap/extend these closures (decoy-tax spread, Lambda wander, enemy pressure).
    private final Closure escortIntent = { Map bot, Map snapshot ->
        def ls = snapshot.ownLambdas
        if (!ls) return null
        def near = ls.min { cheb(bot, it) }      // escort the nearest of your team's two Lambdas
        return [near.x, near.y]
    }
    private final Closure noIntent = { Map bot, Map snapshot -> null }

    private final Map<String, Closure> intentTargets = [
        'CIRCUIT_PATTERN' : escortIntent,
        'GEOMETRIC_ENTITY': escortIntent,
        'FLOWING_CURRENT' : escortIntent,
        'DIGITAL_GHOST'   : escortIntent,
        'BINARY_FORM'     : escortIntent,
        'CLASSIC_LAMBDA'  : noIntent,        // Lambdas wander → a later piece
    ]

    /** Start the live tick (wired in BootStrap). Thin: just drives advanceBots on a timer. */
    void startBotSystem() {
        botScheduler.scheduleAtFixedRate({
            try { tickAllActiveMatches() } catch (Exception e) { println "Cluster bot tick error: ${e.message}" }
        } as Runnable, 5, 5, TimeUnit.SECONDS)
    }

    void tickAllActiveMatches() {
        clusterMatchService.activeMatchIds().each { advanceBots(it) }
    }

    /** Spread each bot to a spawn tile (seeded → deterministic): ALPHA low quadrant, BETA high. */
    void assignSpawnPositions(String matchId) {
        def rnd = new Random(matchId.hashCode())
        clusterMatchService.botFactsForMatch(matchId).findAll { it.isBot }.each { f ->
            int baseX = (f.team == 'ALPHA') ? 0 : 5
            int baseY = (f.team == 'ALPHA') ? 0 : 5
            clusterMatchService.setMemberPosition(f.username,
                Math.min(9, baseX + rnd.nextInt(5)), Math.min(9, baseY + rnd.nextInt(5)))
        }
    }

    /**
     * One deterministic tick: move each positioned bot one Chebyshev step toward its role's intent
     * target. Package-visible so tests drive it synchronously (no wall-clock). No-op if not ACTIVE.
     */
    void advanceBots(String matchId) {
        def facts = clusterMatchService.botFactsForMatch(matchId)
        if (!facts) return
        def byTeam = facts.groupBy { it.team }
        facts.findAll { it.isBot && it.x != null && it.y != null }.each { bot ->
            def lambdas = byTeam[bot.team].findAll { it.role == 'CLASSIC_LAMBDA' && it.x != null && it.y != null }
            def target = (intentTargets[bot.role] ?: noIntent).call(bot, [ownLambdas: lambdas])
            if (target != null) {
                def step = stepToward(bot.x as int, bot.y as int, target[0] as int, target[1] as int)
                clusterMatchService.setMemberPosition(bot.username, step[0], step[1])
            }
        }
    }

    // Chebyshev distance between two position-bearing maps.
    private static int cheb(Map a, Map b) {
        return Math.max(Math.abs((a.x - b.x) as int), Math.abs((a.y - b.y) as int))
    }

    // One Chebyshev step from (fx,fy) toward (tx,ty), clamped 0-9.
    private static List<Integer> stepToward(int fx, int fy, int tx, int ty) {
        int nx = fx + Integer.signum(tx - fx)
        int ny = fy + Integer.signum(ty - fy)
        return [Math.max(0, Math.min(9, nx)), Math.max(0, Math.min(9, ny))]
    }
}
