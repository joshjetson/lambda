package ysap

import grails.testing.mixin.integration.Integration
import grails.gorm.transactions.Rollback
import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Specification

/**
 * Phase 10 Stage 2a: the 10-second auto-roll. When a turn opens (both axes spent) a timer is armed;
 * if the player doesn't `dados` in time it rolls for them. The 10s wall-clock + the animation are
 * UI-verified; here we drive the timer's effect (autoRollFor) directly and assert the state machine
 * (template: DefragTimerSpec).
 */
@Integration
@Rollback
class AutoRollSpec extends Specification {

    @Autowired CoordinateStateService coordinateStateService
    @Autowired LambdaPlayerService lambdaPlayerService

    private LambdaPlayer playerAt00(String name) {
        def p = lambdaPlayerService.createPlayer(name.toLowerCase(), name, 'CLASSIC_LAMBDA')
        p = LambdaPlayer.get(p.id); p.positionX = 0; p.positionY = 0; p.save(failOnError: true)
        return p
    }

    private PrintWriter w() { new PrintWriter(new StringWriter()) }  // no socket → no animation

    void "spending both axes arms a pending auto-roll; firing it rolls fresh dice"() {
        given:
        def p = playerAt00('AutoRollA')
        def pw = w()
        coordinateStateService.handleDadosCommand(p, pw)
        coordinateStateService.handleMoveCommand('move east 1', p, pw)
        coordinateStateService.handleMoveCommand('move north 1', p, pw)

        expect: "the roll cleared and an auto-roll is now pending"
        coordinateStateService.turnStateFor(p.username) == null
        coordinateStateService.autoRollPending(p.username)

        when: "the auto-roll fires"
        coordinateStateService.autoRollFor(p.username, pw)

        then: "a fresh roll is active again"
        coordinateStateService.turnStateFor(p.username) != null
    }

    void "rolling manually cancels a pending auto-roll"() {
        given:
        def p = playerAt00('AutoRollB')
        def pw = w()
        coordinateStateService.armAutoRoll(p.username, pw)

        expect:
        coordinateStateService.autoRollPending(p.username)

        when:
        coordinateStateService.handleDadosCommand(p, pw)

        then: "the manual roll cancelled the pending auto-roll and produced a roll"
        !coordinateStateService.autoRollPending(p.username)
        coordinateStateService.turnStateFor(p.username) != null
    }

    void "auto-roll is a no-op if the player already holds an active roll"() {
        given:
        def p = playerAt00('AutoRollC')
        def pw = w()
        coordinateStateService.handleDadosCommand(p, pw)
        def beforeDie = coordinateStateService.turnStateFor(p.username).yDie

        when: "the auto-roll fires while a roll is already active"
        coordinateStateService.autoRollFor(p.username, pw)

        then: "the existing roll is untouched"
        coordinateStateService.turnStateFor(p.username) != null
        coordinateStateService.turnStateFor(p.username).yDie == beforeDie
    }
}
