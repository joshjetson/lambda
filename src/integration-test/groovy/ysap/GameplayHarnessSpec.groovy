package ysap

import grails.testing.mixin.integration.Integration
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

    def cleanupSpec() {
        bot?.close()
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
}
