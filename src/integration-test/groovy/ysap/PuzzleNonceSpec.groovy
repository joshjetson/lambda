package ysap

import grails.testing.mixin.integration.Integration
import grails.gorm.transactions.Rollback
import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Specification

/**
 * Phase 8 (surgical): solving a puzzle room (Path A) now records the element's nonce as discovered,
 * so the Phase-6 `unlock_symbol` path and the trade economy — both of which read `discoveredNonces` —
 * can finally see a nonce earned through puzzle play (previously it was discarded on solve).
 */
@Integration
@Rollback
class PuzzleNonceSpec extends Specification {

    @Autowired CompetitivePuzzleService competitivePuzzleService
    @Autowired LambdaPlayerService lambdaPlayerService
    @Autowired PuzzleService puzzleService

    void "solving a puzzle room records the element's nonce as discovered"() {
        given: "a player whose WATER puzzle room is calculated, with the session nonce + room seeded"
        def sess = 'tsess'
        def player = LambdaPlayer.withTransaction {
            def p = lambdaPlayerService.createPlayer('pznonce', 'PzNonce', 'CLASSIC_LAMBDA')
            LambdaPlayer.get(p.id)
        }
        LambdaPlayer.withTransaction {
            new ElementalNonce(nonceName: 'WATER_KEY_1', nonceValue: 'NV123', elementType: 'WATER',
                    chemicalClue: 'x1(h)x2(o)', commandFlag: '--decode', description: 'water key',
                    matrixLevel: 1, mapNumber: 1, gameSessionId: sess, discoveryMethod: 'PUZZLE_ROOM').save(failOnError: true)
            new PuzzleRoom(roomName: 'Water Chamber', description: 'a chamber', matrixLevel: 1, mapNumber: 1,
                    gameSessionId: sess, positionX: 5, positionY: 5, elementType: 'WATER',
                    executableFile: 'print(1)', fileName: 'water.py', requiredFlag: '--decode', requiredNonce: 'NV123').save(failOnError: true)
            new PlayerPuzzleState(playerId: player.id.toString(), gameSessionId: sess, mapNumber: 1, elementType: 'WATER',
                    variableCoordinateX: 1, variableCoordinateY: 1, puzzleRoomCoordinateX: 5, puzzleRoomCoordinateY: 5,
                    hasCalculatedCoords: true, hasObtainedSymbol: false).save(failOnError: true)
        }

        when: "the player executes the puzzle room with the correct flag + nonce"
        def result = competitivePuzzleService.executePlayerPuzzleRoom(player, sess, 1, '--decode', 'NV123', 'water.py', 5, 5)

        then: "the symbol is granted AND the nonce is now discovered (reachable by unlock_symbol / trade)"
        result.success
        LambdaPlayer.withTransaction {
            def p = LambdaPlayer.get(player.id)
            assert p.hasWaterSymbol
            assert p.discoveredNonces?.any { it.nonceName == 'WATER_KEY_1' && it.isDiscovered }
            true
        }
    }

    // Regression: a missing `elementType` on HiddenVariable made initializePuzzleSystem throw, and
    // because the 4-fragment seed shares that one transaction, the seed rolled back too — so the
    // defrag puzzle-fragment reward later failed with "not found". With elementType set, the whole
    // init commits and the boot-seeded templates persist and can be awarded.
    void "boot-seeded puzzle logic fragments persist and can be awarded (puzzle-init fix)"() {
        given: "the defrag reward pool names"
        def names = ['Atmospheric Processor', 'Thermal Signature Decoder',
                     'Geological Survey Tool', 'Hydro-Chemical Validator']

        expect: "the boot seeding committed (would be null/rolled-back before the fix)"
        names.every { String n -> LambdaPlayer.withTransaction { PuzzleLogicFragment.findByName(n) != null } }

        when: "a defrag-kill-style puzzle fragment reward is awarded"
        def player = LambdaPlayer.withTransaction {
            def p = lambdaPlayerService.createPlayer('pzreward', 'PzReward', 'CLASSIC_LAMBDA')
            LambdaPlayer.get(p.id)
        }
        def result = puzzleService.awardPuzzleFragment(player, 'Atmospheric Processor')

        then: "it is granted (previously failed with 'not found')"
        result.success
    }
}
