package ysap

import grails.testing.mixin.integration.Integration
import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Specification

/**
 * Deterministic, service-level coverage for the Phase 2 recursion economy
 * (charges, cooldown, ethnicity lock, daily refill, and the gate-on-read effect window).
 * Drives LambdaPlayerService.handleRecurseCommand directly so the assertions don't depend
 * on the statistical effects (avoidance/efficiency) — only on the deterministic economy.
 */
@Integration
class RecurseSpec extends Specification {

    @Autowired
    LambdaPlayerService lambdaPlayerService

    @Autowired
    EntropyService entropyService

    private LambdaPlayer freshPlayer(String name, String ethnicity) {
        LambdaPlayer.withTransaction {
            def p = lambdaPlayerService.createPlayer(name, name, ethnicity)
            return LambdaPlayer.get(p.id)
        }
    }

    void "matching ability activates the effect, sets the bonus, and consumes a charge"() {
        given:
        def player = freshPlayer('rec_classic', 'CLASSIC_LAMBDA')

        when:
        def out = lambdaPlayerService.handleRecurseCommand('fusion', player, null)

        then:
        out.toLowerCase().contains('activated')
        LambdaPlayer.withTransaction {
            def p = LambdaPlayer.get(player.id)
            assert p.recursionCharges == 1
            assert p.activeRecursionEffect == 'fusion'
            assert p.fusionSuccessBonus == 0.15d
            assert lambdaPlayerService.recursionEffectActive(p)
            true
        }
    }

    void "a non-matching ethnicity is refused and consumes no charge"() {
        given:
        def player = freshPlayer('rec_wrong', 'CLASSIC_LAMBDA')

        when:
        def out = lambdaPlayerService.handleRecurseCommand('stealth', player, null)

        then:
        out.toLowerCase().contains('not available')
        LambdaPlayer.withTransaction { LambdaPlayer.get(player.id).recursionCharges == 2 }
    }

    void "an unknown ability lists the available ones"() {
        given:
        def player = freshPlayer('rec_unknown', 'CLASSIC_LAMBDA')

        expect:
        lambdaPlayerService.handleRecurseCommand('', player, null).toLowerCase().contains('available')
    }

    void "immediate re-use is blocked by cooldown"() {
        given:
        def player = freshPlayer('rec_cooldown', 'CLASSIC_LAMBDA')
        lambdaPlayerService.handleRecurseCommand('fusion', player, null)

        when:
        def out = lambdaPlayerService.handleRecurseCommand('fusion', player, null)

        then:
        out.toLowerCase().contains('cooldown')
    }

    void "charges deplete to zero and refill on entropy refresh"() {
        given: "a player with no charges and a stale entropy refresh (so refresh is permitted)"
        def player = freshPlayer('rec_refill', 'CLASSIC_LAMBDA')
        def stale = LambdaPlayer.withTransaction {
            def p = LambdaPlayer.get(player.id)
            p.recursionCharges = 0
            p.lastEntropyRefresh = new Date(System.currentTimeMillis() - 21L * 60 * 60 * 1000)
            p.save(failOnError: true)
            return p
        }

        when: "recursing with no charges is blocked, then a daily entropy refresh refills"
        def blocked = lambdaPlayerService.handleRecurseCommand('fusion', stale, null)
        entropyService.refreshPlayerEntropy(stale)

        then:
        blocked.toLowerCase().contains('charge')
        LambdaPlayer.withTransaction { LambdaPlayer.get(player.id).recursionCharges == 2 }
    }

    void "an expired effect is no longer active (gate-on-read)"() {
        given:
        def player = freshPlayer('rec_expired', 'FLOWING_CURRENT')
        lambdaPlayerService.handleRecurseCommand('mine', player, null)

        when: "force the effect window to close"
        LambdaPlayer.withTransaction {
            def p = LambdaPlayer.get(player.id)
            p.recursionEffectExpires = new Date(System.currentTimeMillis() - 1000)
            p.save(failOnError: true)
        }

        then: "the central gate now reports inactive, so consumers ignore the stale bonus"
        LambdaPlayer.withTransaction {
            !lambdaPlayerService.recursionEffectActive(LambdaPlayer.get(player.id))
        }
    }
}
