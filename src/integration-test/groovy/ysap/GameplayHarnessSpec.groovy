package ysap

import grails.testing.mixin.integration.Integration
import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Stepwise

/**
 * End-to-end gameplay harness: boots the full Grails app (which starts the telnet
 * server via BootStrap on the test port 2323), then connects a real socket client
 * and plays the game, asserting on what the server sends back.
 *
 * This is the regression net for the telnet game — add a feature, add a step here.
 * Run with:  ./gradlew integrationTest --tests ysap.GameplayHarnessSpec
 *
 * @Stepwise: steps run top-to-bottom and share one connected @Shared client, so the
 * character created in step 1 is reused by every later step.
 */
@Integration
@Stepwise
class GameplayHarnessSpec extends Specification {

    /** Must match environments.test.lambda.telnet.port in application.yml. */
    static final int TELNET_PORT = 2323

    @Shared
    LambdaTelnetClient bot

    @Autowired
    TelnetServerService telnetServerService

    @Autowired
    ClusterMatchService clusterMatchService

    @Autowired
    ClusterRoleService clusterRoleService

    def cleanupSpec() {
        bot?.close()
    }

    /** Poll a condition until true or the deadline passes (for async server-side cleanup). */
    private boolean pollUntil(long ms, Closure<Boolean> cond) {
        long deadline = System.currentTimeMillis() + ms
        while (System.currentTimeMillis() < deadline) {
            if (cond()) return true
            Thread.sleep(100)
        }
        return cond()
    }

    void "a brand-new entity can be created and spawns at level 1 (0,0)"() {
        given: "a fresh socket connection to the running game server"
        bot = new LambdaTelnetClient('localhost', TELNET_PORT)

        when: "we walk the character-creation handshake"
        bot.createCharacter('botuser', 'BotUser', 1)

        then: "we land at the in-game prompt at the spawn coordinate"
        bot.text() =~ /1:\(0,0\)\s*>/
    }

    void "status reports the player and its starting bits"() {
        when:
        String out = bot.command('status')

        then: "status output mentions the entity and the bits economy"
        out.toLowerCase().contains('botuser') || out.toLowerCase().contains('bituser') || out.toLowerCase().contains('lambda')
        out.toLowerCase().contains('bit')
    }

    void "cc teleports the player to a new coordinate and the prompt reflects it"() {
        when: "we move into the safe zone (1,1) — no random encounters there"
        String out = bot.command('cc 1,1')

        then: "the prompt now shows the new coordinate"
        out =~ /1:\(1,1\)\s*>/
    }

    void "scan runs at the current coordinate and produces output"() {
        when:
        String out = bot.command('scan')

        then: "scan returns SOMETHING (fragment, clear, or proximity report) and a fresh prompt"
        out.trim().length() > 0
        out =~ LambdaTelnetClient.PROMPT
    }

    void "inventory lists the player's holdings"() {
        when:
        String out = bot.command('inventory')

        then: "inventory references bits and/or fragments"
        out.toLowerCase().contains('bit') || out.toLowerCase().contains('fragment') || out.toLowerCase().contains('inventory')
    }

    void "an unknown command is handled gracefully and returns to the prompt"() {
        when:
        String out = bot.command('flibbertigibbet')

        then: "the unknown-command fallback is preserved (it moved into the extracted dispatchCommand)"
        out.toLowerCase().contains('unknown command')
        out =~ LambdaTelnetClient.PROMPT
    }

    void "help lists available commands"() {
        when:
        String out = bot.command('help')

        then:
        out.toLowerCase().contains('scan') || out.toLowerCase().contains('command') || out.toLowerCase().contains('help')
    }

    // --- Phase 0 regression pins: capture CURRENT behavior of commands that later phases change.
    // If a later phase alters these, it must consciously update the assertion here.

    void "PIN symbols: reports elemental symbol collection status"() {
        when:
        String out = bot.command('symbols')

        then:
        out =~ LambdaTelnetClient.PROMPT
        out.toLowerCase() =~ /symbol|air|fire|earth|water|element/
    }

    void "PIN use: an item the player does not own is rejected, not crashed"() {
        when:
        String out = bot.command('use nonexistent_widget')

        then: "returns to a prompt with a 'do not have / not found'-style message (Phase 3 changes effects)"
        out =~ LambdaTelnetClient.PROMPT
        out.toLowerCase() =~ /don'?t have|not found|no .*item|unknown|usage|use /
    }

    void "PIN recurse: bare command shows usage (Phase 2 makes it consume charges)"() {
        when:
        String out = bot.command('recurse')

        then:
        out =~ LambdaTelnetClient.PROMPT
        out.toLowerCase() =~ /recurse|ability|abilities|usage|charge/
    }

    void "PIN repair: repairing a healthy coordinate does not start the mini-game (Phase 4 adds reward)"() {
        when: "(9,9) is undamaged, so this should report no-repair-needed and return to prompt"
        String out = bot.command('repair 9 9', 6000)

        then:
        out =~ LambdaTelnetClient.PROMPT
        out.toLowerCase() =~ /repair|damage|health|coordinate|not /
    }

    void "trade + offer is wired (Phase 5): offer without a target gives trade guidance, not 'unknown'"() {
        given:
        bot.command('heap')

        when: "offer is now a real heap command — without an active trade it guides you to start one"
        String offerOut = bot.command('offer F1 1 10')

        then:
        offerOut.toLowerCase().contains('start a trade first')

        cleanup:
        bot.command('exit')
    }

    // --- Phase 2: recurse is now a real charge/cooldown economy (botuser is CLASSIC_LAMBDA → fusion).

    void "recurse fusion activates for a Classic Lambda and consumes a charge"() {
        when:
        String out = bot.command('recurse fusion')

        then: "activation message, and status reflects one charge spent"
        out.toLowerCase() =~ /activated|recursion/
        bot.command('status') =~ /Recursion Charges: 1\/2/
    }

    void "recurse reused immediately is blocked (cooldown), not silently re-applied"() {
        when:
        String out = bot.command('recurse fusion')

        then:
        out.toLowerCase() =~ /cooldown|charge/
    }

    void "recurse for a non-matching ethnicity is refused"() {
        when:
        String out = bot.command('recurse stealth')

        then:
        out.toLowerCase().contains('not available')
    }

    void "unlock_symbol is a wired command (Phase 6) — not 'unknown'"() {
        when: "no discovered nonce / maybe no symbol here — either way it's the symbol handler responding"
        String out = bot.command('unlock_symbol water --x')

        then:
        out.toLowerCase() =~ /symbol|resonance|nonce|flag/
    }

    void "invoke is a wired command (Phase 7) — gated on all 4 symbols, not 'unknown'"() {
        when: "botuser holds no symbols, so invoking the daemon is refused with guidance"
        String out = bot.command('invoke')

        then:
        out.toLowerCase() =~ /symbol|daemon/
    }

    void "move is a wired command (Phase 10) — needs a dados roll first, not 'unknown'"() {
        when: "no roll yet, so move guides the player (roll first, or wait-your-turn) — proves it's dispatched"
        String out = bot.command('move north 1')

        then:
        out.toLowerCase() =~ /roll first|not your move/
    }

    // --- Connection robustness (QA-found crash family): a thrown handler must not kill the thread,
    // and a dropped client must not leave a ghost session. The StaleStateException race that exposed
    // this in defrag combat can't be reproduced deterministically over a socket, so we prove the
    // invariant it violated: any throwing handler is caught and the connection survives.

    void "a handler that throws is caught — the connection survives and still prompts"() {
        when: "the diagnostic seam handler throws; processGameCommand is now guarded"
        String boomOut = bot.command('__boom')

        then: "the failure is reported to the player, not fatal"
        boomOut.toLowerCase().contains('command failed')

        and: "the very next command still works — the thread did not die"
        bot.command('status') =~ LambdaTelnetClient.PROMPT
    }

    void "a disconnected entity is cleaned out of playerSessions (finally-cleanup, no ghost)"() {
        given: "a second entity connects and is registered"
        def ghost = new LambdaTelnetClient('localhost', TELNET_PORT)
        ghost.createCharacter('ghostx', 'GhostX', 1)

        expect: "it is present in the live session map"
        pollUntil(5_000) { telnetServerService.playerSessions.values().any { it?.username == 'ghostx' } }

        when: "it drops its connection"
        ghost.close()

        then: "cleanup runs in the finally block → the session is removed (no ghost left behind)"
        pollUntil(5_000) { !telnetServerService.playerSessions.values().any { it?.username == 'ghostx' } }
    }

    // --- Cluster Mode PIECE 1: match + team + role + true/decoy foundation (additive; Node untouched).
    // botuser was created avatar 1 = CLASSIC_LAMBDA = the Lambda/Collector role.

    void "cluster create starts a match and seats the creator on a team"() {
        when:
        String out = bot.command('cluster create')

        then:
        out.toLowerCase() =~ /cluster|match|team|alpha/
        out =~ LambdaTelnetClient.PROMPT
    }

    void "cluster status shows the player on a team with their role (from ethnicity)"() {
        when:
        String out = bot.command('cluster status')

        then:
        out.toLowerCase() =~ /team|alpha|beta/
        out.toLowerCase() =~ /collector|lambda/   // role derived from avatarSilhouette CLASSIC_LAMBDA
    }

    void "with two Lambdas, exactly one is the true Lambda and each entity sees only its own bit"() {
        given: "a second Lambda joins the creator's match"
        def lam2 = new LambdaTelnetClient('localhost', TELNET_PORT)
        lam2.createCharacter('clusterb', 'ClusterB', 1)   // avatar 1 → CLASSIC_LAMBDA
        String joinOut = lam2.command('cluster join')

        expect: "the joiner landed on a team"
        joinOut.toLowerCase() =~ /team|alpha|beta/

        and: "both Lambdas are on the same team, and exactly one carries the true bit"
        def a = clusterMatchService.clusterStateFor('botuser')
        def b = clusterMatchService.clusterStateFor('clusterb')
        a != null && b != null
        a.team == b.team
        a.isTrueLambda != b.isTrueLambda                              // one true, one decoy
        clusterMatchService.trueLambdaCountOnTeamOf('botuser') == 1

        and: "each entity's status states ITS OWN identity (self-only); roster carries no other's bit"
        bot.command('cluster status').toLowerCase() =~ /true lambda|decoy/
        lam2.command('cluster status').toLowerCase() =~ /true lambda|decoy/

        cleanup:
        lam2?.close()
    }

    void "cluster is a wired command — bare invocation renders the usage guide"() {
        when:
        String out = bot.command('cluster')

        then: "the usage panel's own lines appear — only the wired handler produces these, never the\n        unknown-command fallback (a positive match, robust to async auto-roll noise in the stream)"
        out.toLowerCase() =~ /cluster create|cluster join|cluster status/
    }

    // --- Cluster Mode PIECE 2: bot seat-fill + match-start gate (LOBBY → ACTIVE).

    void "cluster start fills both teams to 7 with bots, activates, and sets one true Lambda per team"() {
        when: "the creator starts the match (botuser is in a LOBBY match on ALPHA)"
        bot.command('cluster start')
        def s = clusterMatchService.matchSummaryFor('botuser')

        then: "both teams are filled to 7, the match is ACTIVE, exactly one true Lambda per team, bots present"
        s != null
        s.state == 'ACTIVE'
        s.teamSizes['ALPHA'] == 7
        s.teamSizes['BETA'] == 7
        s.trueLambdasPerTeam['ALPHA'] == 1
        s.trueLambdasPerTeam['BETA'] == 1
        s.bots > 0
    }

    // --- Cluster Mode PIECE 3: enemy-view identity firewall.

    void "the firewall hides the true Lambda from enemies but reveals it to teammates"() {
        given: "the ACTIVE match has both teams holding two Lambdas (from cluster start)"
        def lambdas = clusterMatchService.lambdaUsernamesOnTeamOf('botuser')   // ALPHA's two Lambdas
        def enemy = clusterMatchService.anEnemyMemberUsername('botuser')        // a BETA member

        expect:
        lambdas.size() == 2
        enemy != null

        and: "an ENEMY's view of the two Lambdas is identical — no true/decoy leak"
        clusterRoleService.firewallViewOf(enemy, lambdas[0]) == clusterRoleService.firewallViewOf(enemy, lambdas[1])
        clusterRoleService.firewallViewOf(enemy, lambdas[0]).lambda == 'HIDDEN'

        and: "a TEAMMATE distinguishes them — one TRUE, one DECOY"
        clusterRoleService.firewallViewOf('botuser', lambdas[0]) != clusterRoleService.firewallViewOf('botuser', lambdas[1])
    }

    // --- Cluster Mode PIECE 4: scan all (Circuit tracker) — location-only intel.

    void "scan all is Circuit-only, shows enemy positions, hides Ghosts, never leaks Lambda identity"() {
        given: "a Circuit on ALPHA, plus a positioned enemy Lambda and enemy Ghost on BETA"
        def circuit = clusterMatchService.memberWithRole('botuser', 'CIRCUIT_PATTERN', true)
        def enemyLambda = clusterMatchService.memberWithRole('botuser', 'CLASSIC_LAMBDA', false)
        def enemyGhost = clusterMatchService.memberWithRole('botuser', 'DIGITAL_GHOST', false)
        clusterMatchService.setMemberPosition(enemyLambda, 5, 5)
        clusterMatchService.setMemberPosition(enemyGhost, 3, 3)

        expect:
        circuit && enemyLambda && enemyGhost

        when: "the Circuit sweeps"
        String out = clusterRoleService.scanAllFor(circuit)

        then: "the enemy Lambda's position shows, generically; the Ghost is excluded"
        out.contains('(5,5)')
        out.toLowerCase().contains('lambda')
        !out.toLowerCase().contains('true lambda')
        !out.toLowerCase().contains('decoy')
        !out.contains('(3,3)')                                   // ghost invisible to the array

        and: "a non-Circuit (a Lambda) is refused the ability"
        clusterRoleService.scanAllFor(lambdas0()).toLowerCase().contains('circuit')

        and: "an immediate second sweep is on cooldown"
        clusterRoleService.scanAllFor(circuit).toLowerCase().contains('recharging')
    }

    private String lambdas0() { clusterMatchService.lambdaUsernamesOnTeamOf('botuser')[0] }

    // --- Cluster Mode PIECE 5: protection-geometry correlation (leak signal #1).

    void "the tracker's protection read shows escort asymmetry between the two enemy Lambdas"() {
        given: "BETA's Circuit will read ALPHA; ALPHA's Λ are split, with escorts clustered on one"
        def betaCircuit = clusterMatchService.memberWithRole('botuser', 'CIRCUIT_PATTERN', false)
        def alphaLambdas = clusterMatchService.lambdaUsernamesOnTeamOf('botuser').sort()  // [botuser, clusterb]
        clusterMatchService.setMemberPosition(alphaLambdas[0], 5, 5)   // Λ#1 — guarded
        clusterMatchService.setMemberPosition(alphaLambdas[1], 8, 1)   // Λ#2 — alone
        clusterMatchService.setMemberPosition(clusterMatchService.memberWithRole('botuser', 'GEOMETRIC_ENTITY', true), 5, 6)
        clusterMatchService.setMemberPosition(clusterMatchService.memberWithRole('botuser', 'FLOWING_CURRENT', true), 6, 5)

        expect:
        betaCircuit != null

        when: "the enemy Circuit sweeps ALPHA"
        String out = clusterRoleService.scanAllFor(betaCircuit)

        then: "the read surfaces the asymmetry — Λ#1 has 2 nearby allies, Λ#2 has 0 (correlation, not identity)"
        out.contains('(5,5): 2')
        out.contains('(8,1): 0')
        !out.toLowerCase().contains('true lambda')
    }
}
