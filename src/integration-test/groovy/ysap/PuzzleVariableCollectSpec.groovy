package ysap

import grails.testing.mixin.integration.Integration
import grails.gorm.transactions.Rollback
import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Specification

/**
 * Regression for the "things don't update after an action" class of bug, reported via:
 * collect a puzzle variable, then scan — the scan must NOT keep advertising a variable you already
 * collected. Two underlying defects are pinned here:
 *   1. collectPlayerVariable persisted the player's collectedVariables but silently failed to flush
 *      PlayerPuzzleState.hasCollectedVariable (a dirty-checking quirk in the graph) — fixed with a
 *      direct bulk UPDATE.
 *   2. getPlayerSpecificPuzzleElements (the scan source) read a stale hasCollectedVariable — fixed by
 *      refreshing the states before filtering.
 */
@Integration
@Rollback
class PuzzleVariableCollectSpec extends Specification {

    @Autowired CompetitivePuzzleService competitivePuzzleService
    @Autowired LambdaPlayerService lambdaPlayerService

    void "collecting a variable persists the collected flag AND stops the scan advertising it"() {
        given: "a player, a session FIRE variable at (3,3), and the player's FIRE puzzle state there (uncollected)"
        def sess = 'cvsess'
        def player = LambdaPlayer.withTransaction {
            def p = lambdaPlayerService.createPlayer('cvtest', 'CvTest', 'CLASSIC_LAMBDA')
            LambdaPlayer.get(p.id)
        }
        LambdaPlayer.withTransaction {
            new GameSession(sessionId: sess, isActive: true).save(failOnError: true)
            new HiddenVariable(variableName: 'test_var', variableValue: '42', variableType: 'NUMERIC',
                    description: 'a test var', matrixLevel: 1, elementType: 'FIRE', mapNumber: 1,
                    gameSessionId: sess, positionX: 3, positionY: 3).save(failOnError: true)
            new PlayerPuzzleState(playerId: player.id.toString(), gameSessionId: sess, mapNumber: 1,
                    elementType: 'FIRE', variableCoordinateX: 3, variableCoordinateY: 3,
                    puzzleRoomCoordinateX: 7, puzzleRoomCoordinateY: 7, hasCollectedVariable: false).save(failOnError: true)
        }

        expect: "before collecting, the scan advertises the variable at (3,3)"
        competitivePuzzleService.getPlayerSpecificPuzzleElements(player, sess, 1, 3, 3).any { it.type == 'player_variable' }

        when: "the player collects it"
        def result = competitivePuzzleService.collectPlayerVariable(player, 'test_var', sess, 1, 3, 3)

        then: "collection succeeds and the flag is PERSISTED (re-read from a fresh load)"
        result.success
        PlayerPuzzleState.withTransaction {
            PlayerPuzzleState.findByPlayerIdAndGameSessionIdAndMapNumberAndElementType(
                player.id.toString(), sess, 1, 'FIRE').hasCollectedVariable
        }

        and: "the scan NO LONGER advertises the variable — the reported bug is gone"
        !competitivePuzzleService.getPlayerSpecificPuzzleElements(player, sess, 1, 3, 3).any { it.type == 'player_variable' }

        and: "collecting again is rejected as already-collected (not a fresh collect)"
        competitivePuzzleService.collectPlayerVariable(player, 'test_var', sess, 1, 3, 3).message.toLowerCase().contains('already')
    }
}
