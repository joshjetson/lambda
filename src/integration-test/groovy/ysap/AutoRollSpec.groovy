package ysap

import grails.testing.mixin.integration.Integration
import grails.gorm.transactions.Rollback
import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Specification

/**
 * Phase 10 Stage 2a (auto-roll) — verified deterministically by driving the timer's effect directly.
 * Under Stage 2b the auto-roll is armed via `armTurnControls` (when a move-turn opens); the 10s
 * wall-clock + animation are UI-verified. Empty rotation = solo, so the move-turn gate always passes.
 */
@Integration
@Rollback
class AutoRollSpec extends Specification {

    @Autowired CoordinateStateService coordinateStateService
    @Autowired LambdaPlayerService lambdaPlayerService
    @Autowired TelnetServerService telnetServerService

    def setup() { telnetServerService.resetMoveRotation() }

    private LambdaPlayer player(String name) {
        def p = lambdaPlayerService.createPlayer(name.toLowerCase(), name, 'CLASSIC_LAMBDA')
        p = LambdaPlayer.get(p.id); p.positionX = 0; p.positionY = 0; p.save(failOnError: true)
        return p
    }

    private PrintWriter w() { new PrintWriter(new StringWriter()) }  // no socket → no animation

    void "arming a player's turn schedules a pending auto-roll; firing it rolls fresh dice"() {
        given:
        def p = player('AutoRollA')
        coordinateStateService.armTurnControls(p.username, w(), false)

        expect:
        coordinateStateService.autoRollPending(p.username)

        when: "the auto-roll fires (it would normally fire after 10s of idle)"
        coordinateStateService.autoRollFor(p.username, w())

        then:
        coordinateStateService.turnStateFor(p.username) != null
    }

    void "rolling manually cancels a pending auto-roll"() {
        given:
        def p = player('AutoRollB')
        coordinateStateService.armAutoRoll(p.username, w())

        expect:
        coordinateStateService.autoRollPending(p.username)

        when:
        coordinateStateService.handleDadosCommand(p, w())

        then: "the manual roll cancelled the pending auto-roll and produced a roll"
        !coordinateStateService.autoRollPending(p.username)
        coordinateStateService.turnStateFor(p.username) != null
    }

    void "auto-roll is a no-op if the player already holds an active roll"() {
        given:
        def p = player('AutoRollC')
        def pw = w()
        coordinateStateService.handleDadosCommand(p, pw)
        def beforeDie = coordinateStateService.turnStateFor(p.username).yDie

        when:
        coordinateStateService.autoRollFor(p.username, pw)

        then:
        coordinateStateService.turnStateFor(p.username) != null
        coordinateStateService.turnStateFor(p.username).yDie == beforeDie
    }
}
