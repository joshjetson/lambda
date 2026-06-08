package ysap

import grails.testing.mixin.integration.Integration
import grails.gorm.transactions.Rollback
import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Specification

/**
 * Phase 10 Stage 2b: the move-turn rotation. ONLY `dados`/`move` are turn-gated (everything else is
 * real-time). Solo (size <= 1) = always your turn. Driven through the in-memory rotation seam, so it's
 * fully deterministic; the 2-minute wall-clock force-advance is UI/transcript-verified.
 */
@Integration
@Rollback
class TurnRotationSpec extends Specification {

    @Autowired TelnetServerService telnetServerService
    @Autowired CoordinateStateService coordinateStateService
    @Autowired LambdaPlayerService lambdaPlayerService

    def setup() { telnetServerService.resetMoveRotation() }
    def cleanup() { telnetServerService.resetMoveRotation() }

    private LambdaPlayer player(String name) {
        def p = lambdaPlayerService.createPlayer(name.toLowerCase(), name, 'CLASSIC_LAMBDA')
        p = LambdaPlayer.get(p.id); p.positionX = 0; p.positionY = 0; p.save(failOnError: true)
        return p
    }

    private PrintWriter w() { new PrintWriter(new StringWriter()) }

    void "the first joiner holds the move-turn; only they may roll, others are told to wait"() {
        given:
        def a = player('TurnA'); def b = player('TurnB')
        telnetServerService.joinMoveRotation('turna')
        telnetServerService.joinMoveRotation('turnb')

        expect:
        telnetServerService.currentMoveTurnHolder() == 'turna'
        telnetServerService.isMyMoveTurn('turna')
        !telnetServerService.isMyMoveTurn('turnb')

        and: "the non-active player is refused and gets no roll"
        coordinateStateService.handleDadosCommand(b, w()).toLowerCase().contains('not your move')
        coordinateStateService.turnStateFor('turnb') == null

        and: "the active player can roll"
        coordinateStateService.handleDadosCommand(a, w())
        coordinateStateService.turnStateFor('turna') != null
    }

    void "completing both move axes passes the turn to the next player"() {
        given:
        def a = player('TurnA2'); def b = player('TurnB2')
        telnetServerService.joinMoveRotation('turna2')
        telnetServerService.joinMoveRotation('turnb2')
        coordinateStateService.handleDadosCommand(a, w())

        when: "a spends both axes (east then north — safe zone)"
        coordinateStateService.handleMoveCommand('move east 1', a, w())
        coordinateStateService.handleMoveCommand('move north 1', a, w())

        then: "the turn is now b's"
        telnetServerService.currentMoveTurnHolder() == 'turnb2'
        telnetServerService.isMyMoveTurn('turnb2')
        !telnetServerService.isMyMoveTurn('turna2')
    }

    void "the active player disconnecting hands the turn to the next, no deadlock"() {
        given:
        def a = player('TurnA3'); def b = player('TurnB3')
        telnetServerService.joinMoveRotation('turna3')
        telnetServerService.joinMoveRotation('turnb3')

        expect:
        telnetServerService.currentMoveTurnHolder() == 'turna3'

        when:
        telnetServerService.leaveMoveRotation('turna3')

        then:
        telnetServerService.currentMoveTurnHolder() == 'turnb3'
        telnetServerService.isMyMoveTurn('turnb3')
    }

    void "solo: the lone player is always their turn, with no 2-minute cap"() {
        given:
        def a = player('TurnSolo')
        telnetServerService.joinMoveRotation('turnsolo')

        expect:
        telnetServerService.moveRotationSize() == 1
        telnetServerService.isMyMoveTurn('turnsolo')
        !coordinateStateService.turnTimeoutPending('turnsolo')   // no 2-min pressure when alone

        and:
        coordinateStateService.handleDadosCommand(a, w())
        coordinateStateService.turnStateFor('turnsolo') != null
    }

    void "advancing in a multiplayer game arms the new holder's 2-minute cap"() {
        given:
        def a = player('TurnA5'); def b = player('TurnB5')
        telnetServerService.joinMoveRotation('turna5')
        telnetServerService.joinMoveRotation('turnb5')
        coordinateStateService.handleDadosCommand(a, w())

        when: "a completes their turn, passing to b"
        coordinateStateService.handleMoveCommand('move east 1', a, w())
        coordinateStateService.handleMoveCommand('move north 1', a, w())

        then: "b now holds the turn with a 2-minute cap armed"
        telnetServerService.currentMoveTurnHolder() == 'turnb5'
        coordinateStateService.turnTimeoutPending('turnb5')
    }
}
