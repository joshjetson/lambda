package ysap

import grails.testing.mixin.integration.Integration
import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Specification

/**
 * Deterministic coverage for the Phase 3 special-item effects. Items normally drop from RNG
 * defrag kills, so these grant items directly via SpecialItemService and drive the consumer paths,
 * proving the formerly-inert effect flags now actually do something (and are spent exactly once).
 */
@Integration
class SpecialItemEffectsSpec extends Specification {

    @Autowired SpecialItemService specialItemService
    @Autowired LambdaPlayerService lambdaPlayerService
    @Autowired EntropyService entropyService
    @Autowired CoordinateStateService coordinateStateService

    private LambdaPlayer player(String name) {
        LambdaPlayer.withTransaction {
            def p = lambdaPlayerService.createPlayer(name, name, 'CLASSIC_LAMBDA')
            return LambdaPlayer.get(p.id)
        }
    }

    private LambdaPlayer reload(LambdaPlayer p) {
        LambdaPlayer.withTransaction { LambdaPlayer.get(p.id) }
    }

    void "using a duration item arms hasActiveEffect; consumeEffect clears and deletes the one-shot token"() {
        given:
        def p = player('si_active')
        specialItemService.createSpecialItem(p, 'BIT_MULTIPLIER')

        when:
        specialItemService.useSpecialItem(p, 'bit_multiplier')

        then: "armed and still present as the active-effect token (maxUses=1 does NOT delete it yet)"
        specialItemService.hasActiveEffect(p, 'BIT_MULTIPLIER')

        when:
        specialItemService.consumeEffect(p, 'BIT_MULTIPLIER')

        then: "effect cleared and the depleted token removed"
        !specialItemService.hasActiveEffect(p, 'BIT_MULTIPLIER')
        LambdaPlayer.withTransaction {
            LambdaPlayer.get(p.id).specialItems.find { it.itemType == 'BIT_MULTIPLIER' } == null
        }
    }

    void "BIT_MULTIPLIER doubles exactly one bit grant via applyBitModifiers, then is spent"() {
        given:
        def p = player('si_bits')
        specialItemService.createSpecialItem(p, 'BIT_MULTIPLIER')
        specialItemService.useSpecialItem(p, 'bit_multiplier')

        expect:
        specialItemService.applyBitModifiers(p, 100) == 200   // doubled
        specialItemService.applyBitModifiers(p, 100) == 100   // already consumed
    }

    void "ENTROPY_STABILIZER halts decay while active"() {
        given: "a player whose entropy would otherwise decay (stale refresh, 10h ago)"
        def p = player('si_entropy')
        LambdaPlayer.withTransaction {
            def m = LambdaPlayer.get(p.id)
            m.entropy = 80.0
            m.lastEntropyRefresh = new Date(System.currentTimeMillis() - 10L * 60 * 60 * 1000)
            m.save(failOnError: true)
        }

        when: "decay is real without the stabilizer"
        def before = entropyService.calculateEntropyDecay(reload(p))

        and: "activate the stabilizer"
        specialItemService.createSpecialItem(reload(p), 'ENTROPY_STABILIZER')
        specialItemService.useSpecialItem(reload(p), 'entropy_stabilizer')
        def after = entropyService.calculateEntropyDecay(reload(p))

        then:
        before.entropyLoss > 0
        after.entropyLoss == 0.0
    }

    void "INSTANT_REPAIR_KIT restores a damaged adjacent coordinate"() {
        given: "player at (5,5) level 2 with (5,6) wiped"
        def p = player('si_repair')
        LambdaPlayer.withTransaction {
            def m = LambdaPlayer.get(p.id); m.currentMatrixLevel = 2; m.positionX = 5; m.positionY = 5; m.save(failOnError: true)
        }
        coordinateStateService.damageCoordinate(2, 5, 6, 100)
        assert coordinateStateService.getCoordinateHealth(2, 5, 6).health <= 0
        specialItemService.createSpecialItem(reload(p), 'INSTANT_REPAIR_KIT')

        when:
        def res = specialItemService.useSpecialItem(reload(p), 'instant_repair_kit')

        then:
        res.success
        coordinateStateService.getCoordinateHealth(2, 5, 6).health >= 100
    }

    void "RESPAWN_CACHE stores the use location and consumeRespawnCache returns it once"() {
        given: "player uses a respawn cache at (4,7) level 3"
        def p = player('si_respawn')
        LambdaPlayer.withTransaction {
            def m = LambdaPlayer.get(p.id); m.currentMatrixLevel = 3; m.positionX = 4; m.positionY = 7; m.save(failOnError: true)
        }
        specialItemService.createSpecialItem(reload(p), 'RESPAWN_CACHE')
        specialItemService.useSpecialItem(reload(p), 'respawn_cache')

        when:
        def coords = specialItemService.consumeRespawnCache(reload(p))

        then: "the cached location is returned, then the cache is spent"
        coords.x == 4
        coords.y == 7
        coords.level == 3
        specialItemService.consumeRespawnCache(reload(p)) == null
    }

    void "the two formerly-unreachable items can now be created"() {
        given:
        def p = player('si_newitems')

        expect:
        specialItemService.createSpecialItem(p, 'MATRIX_CLIPPER') != null
        specialItemService.createSpecialItem(p, 'INSTANT_REPAIR_KIT') != null
    }

    void "an already-active one-shot cannot be re-armed (no negative uses)"() {
        given:
        def p = player('si_double')
        specialItemService.createSpecialItem(p, 'STEALTH_CLOAK')
        specialItemService.useSpecialItem(p, 'stealth_cloak')

        when: "use it again while still armed"
        def res = specialItemService.useSpecialItem(reload(p), 'stealth_cloak')

        then: "refused cleanly, not a validation crash"
        !res.success
        res.message.toLowerCase().contains('already active')
    }
}
