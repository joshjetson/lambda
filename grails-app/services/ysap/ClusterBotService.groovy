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
    def telnetServerService

    private final ScheduledExecutorService botScheduler = Executors.newScheduledThreadPool(1)

    private int tick = 0   // coarse clock so wandering Lambdas keep drifting (deterministic per tick)

    // O(1) role → "where this bot wants to be" (a target [x,y] or null = hold). Open/closed map.
    //
    // escortSpread: each escort commits to ONE of its team's two Lambdas (stable hash partition) so
    // escorts split across BOTH — paying the "decoy tax" that makes the protection geometry an
    // ambiguous bluff for the enemy hunter rather than a giveaway. (CLUSTER_DESIGN's fun knob.)
    private final Closure escortSpread = { Map bot, Map snapshot ->
        def ls = snapshot.ownLambdas
        if (!ls) return null
        def chosen = ls[Math.abs(bot.username.hashCode()) % ls.size()]
        return [chosen.x, chosen.y]
    }
    // huntEnemy: sabotage roles close on the nearest enemy Lambda → pressure on the human's side.
    private final Closure huntEnemy = { Map bot, Map snapshot ->
        def ls = snapshot.enemyLambdas
        if (!ls) return null
        def near = ls.min { cheb(bot, it) }
        return [near.x, near.y]
    }
    // wander: the team's two Lambdas drift toward OPPOSITE objectives (so they spread apart and the
    // escort split is a readable — but subtle — deduction signal, not a useless 4/4 where both
    // adjacent Lambdas share every escort). Objectives slowly swap so the board keeps moving.
    private static final List OBJECTIVES = [[1, 8], [8, 1], [8, 8], [1, 1]].asImmutable()
    private final Closure wander = { Map bot, Map snapshot ->
        def ls = snapshot.ownLambdas.sort { it.username }
        int idx = Math.max(0, ls.findIndexOf { it.username == bot.username }) % 2
        int phase = tick.intdiv(8) % 2          // shift objectives every 8 ticks so they keep moving
        return OBJECTIVES[idx + 2 * phase]      // idx0 → corner 0 or 2; idx1 → corner 1 or 3 (always apart)
    }
    // collect: bot Lambdas head for the nearest uncollected symbol → a real race the human can lose.
    private final Closure collectIntent = { Map bot, Map snapshot ->
        def syms = snapshot.symbols
        if (!syms) return wander.call(bot, snapshot)
        def near = syms.min { cheb(bot, [x: it.x, y: it.y]) }
        return [near.x, near.y]
    }

    private final Map<String, Closure> intentTargets = [
        'CIRCUIT_PATTERN' : escortSpread,   // Tracker guards its Lambdas (intel near them)
        'BINARY_FORM'     : escortSpread,   // Trapper guards the approach
        'FLOWING_CURRENT' : escortSpread,   // Disruptor screens its Lambdas
        'GEOMETRIC_ENTITY': huntEnemy,      // Scout pushes into enemy ground
        'DIGITAL_GHOST'   : huntEnemy,      // Saboteur infiltrates the enemy Lambdas
        'CLASSIC_LAMBDA'  : collectIntent,  // Lambdas race for the symbols (collect on arrival)
    ]

    /** Start the live tick (wired in BootStrap). Thin: just drives advanceBots on a timer. */
    void startBotSystem() {
        botScheduler.scheduleAtFixedRate({
            try { tickAllActiveMatches() } catch (Exception e) { println "Cluster bot tick error: ${e.message}" }
        } as Runnable, 7, 7, TimeUnit.SECONDS)   // bots step ~every 7s — an active human out-paces them
    }

    void tickAllActiveMatches() {
        clusterMatchService.activeMatchIds().each { id -> advanceBots(id); checkBotWin(id) }
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
        // Lazily seat any bot still without a position (first tick of a freshly-started match) —
        // self-healing, so no coupling back into the referee's start path.
        if (facts.any { it.isBot && (it.x == null || it.y == null) }) {
            assignSpawnPositions(matchId)
            facts = clusterMatchService.botFactsForMatch(matchId)
        }
        tick++
        def positionedLambdas = facts.findAll { it.role == 'CLASSIC_LAMBDA' && it.x != null && it.y != null }
        def symbols = clusterMatchService.uncollectedSymbolsFor(matchId)
        facts.findAll { it.isBot && it.x != null && it.y != null }.each { bot ->
            def snapshot = [ownLambdas:   positionedLambdas.findAll { it.team == bot.team },
                            enemyLambdas: positionedLambdas.findAll { it.team != bot.team },
                            symbols:      symbols]
            def target = (intentTargets[bot.role] ?: wander).call(bot, snapshot)
            if (target != null) {
                def step = stepToward(bot.x as int, bot.y as int, target[0] as int, target[1] as int)
                clusterMatchService.setMemberPosition(bot.username, step[0], step[1])   // collects on arrival
            }
        }
    }

    /**
     * Scheduler-only: a bot TRUE Lambda that has gathered all 4 symbols invokes the daemon and wins.
     * Kept OUT of advanceBots so the synchronously-driven harness never ends a match mid-test.
     */
    void checkBotWin(String matchId) {
        def winner = clusterMatchService.botFactsForMatch(matchId).find {
            it.isBot && it.role == 'CLASSIC_LAMBDA' && it.isTrueLambda && it.heldCount >= 4
        }
        if (winner) declareBotWin(matchId, winner.username)
    }

    // The AI won: end the match and tell every human still in it (so a loss is legible, not a silent drop).
    private void declareBotWin(String matchId, String botUsername) {
        def team = clusterMatchService.declareWin(botUsername)
        clusterMatchService.botFactsForMatch(matchId).findAll { !it.isBot }.each { human ->
            try {
                def w = telnetServerService.writerForUsername(human.username)
                if (w) { w.print("\r\n🏆 TEAM ${team} (bots) defeated the Logic Daemon — the match is over.\r\n"); w.flush() }
            } catch (Exception ignored) { }
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
