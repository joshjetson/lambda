package ysap

import grails.testing.mixin.integration.Integration
import grails.gorm.transactions.Rollback
import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Specification

/**
 * Phase 7: the Logic Daemon endgame. Invoking is gated on all 4 symbols; the daemon is a daemon-class
 * DefragBot fought via the existing combat chain; a real kill advances the run (reset symbols + ascend
 * a level, cap 10 → escaped). The non-deterministic socket kill-chain is the already-covered combat
 * path, so the new logic (eligibility + onDaemonDefeated) is proven directly.
 */
@Integration
@Rollback
class DaemonSpec extends Specification {

    @Autowired ElementalSymbolService elementalSymbolService
    @Autowired DefragBotService defragBotService
    @Autowired LambdaPlayerService lambdaPlayerService

    private LambdaPlayer player(String name, int level, boolean allSymbols) {
        def p = lambdaPlayerService.createPlayer(name.toLowerCase(), name, 'CLASSIC_LAMBDA')
        p = LambdaPlayer.get(p.id)
        p.currentMatrixLevel = level
        if (allSymbols) {
            p.hasAirSymbol = true; p.hasFireSymbol = true; p.hasEarthSymbol = true; p.hasWaterSymbol = true
        }
        p.save(failOnError: true)
        return p
    }

    void "invoke without all 4 symbols is refused"() {
        given:
        def p = player('DaemonNoSym', 3, false)

        expect:
        elementalSymbolService.handleInvokeDaemonCommand(p, null).toLowerCase().contains('need all 4')
    }

    void "invoke with all 4 symbols summons a daemon-class encounter (even in the safe zone)"() {
        given: "player at (0,0) — the safe zone an ordinary bot would refuse to spawn in"
        def p = player('DaemonReady', 3, true)

        when:
        def out = elementalSymbolService.handleInvokeDaemonCommand(p, null)

        then:
        out.toLowerCase().contains('logic daemon')
        DefragBot.findByMatrixLevelAndPositionXAndPositionYAndIsActive(3, p.positionX, p.positionY, true)?.isDaemon
    }

    void "defeating a daemon advances the level and resets all symbol fields"() {
        given:
        def p = player('DaemonSlayer', 5, true)

        when:
        def outcome = defragBotService.onDaemonDefeated(p)

        then:
        outcome.daemonsDefeated == 1
        outcome.newLevel == 6
        !outcome.escaped
        LambdaPlayer.withTransaction {
            def mp = LambdaPlayer.get(p.id)
            assert mp.currentMatrixLevel == 6
            assert !mp.hasAirSymbol && !mp.hasFireSymbol && !mp.hasEarthSymbol && !mp.hasWaterSymbol
            assert mp.airSymbolAcquired == null && mp.waterSymbolAcquired == null
            assert mp.daemonsDefeated == 1
            true
        }
    }

    void "defeating the daemon on the final level escapes the system (no further advance)"() {
        given:
        def p = player('DaemonFinal', 10, true)

        when:
        def outcome = defragBotService.onDaemonDefeated(p)

        then:
        outcome.escaped
        outcome.newLevel == 10
        LambdaPlayer.withTransaction { LambdaPlayer.get(p.id).currentMatrixLevel == 10 }
    }
}
