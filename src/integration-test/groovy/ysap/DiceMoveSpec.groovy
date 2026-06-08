package ysap

import grails.testing.mixin.integration.Integration
import grails.gorm.transactions.Rollback
import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Specification

/**
 * Phase 10 Stage 1: dice roll + per-axis move. The 4s `dados` animation is non-deterministic (timing)
 * and verified via UI capture; here we assert the deterministic invariants of the move economy
 * (roll-required, per-axis budgets, one-commit-per-axis lock, over-budget rejection, movementRangeBonus).
 *
 * Moves go east-then-north (the BootStrap wipes (0,1), which would block a north-first move).
 * Both targets stay in the safe zone (no random encounters).
 */
@Integration
@Rollback
class DiceMoveSpec extends Specification {

    @Autowired CoordinateStateService coordinateStateService
    @Autowired LambdaPlayerService lambdaPlayerService
    @Autowired TelnetServerService telnetServerService

    // Empty rotation → solo → every player is always "their move turn" (the gate passes).
    def setup() { telnetServerService.resetMoveRotation() }

    private LambdaPlayer playerAt00(String name) {
        def p = lambdaPlayerService.createPlayer(name.toLowerCase(), name, 'CLASSIC_LAMBDA')
        p = LambdaPlayer.get(p.id); p.positionX = 0; p.positionY = 0; p.save(failOnError: true)
        return p
    }

    private PrintWriter dummyWriter() { new PrintWriter(new StringWriter()) }  // no socket → no animation

    void "move before rolling is rejected"() {
        given:
        def p = playerAt00('DiceNoRoll')

        expect:
        coordinateStateService.handleMoveCommand('move north 1', p, dummyWriter()).toLowerCase().contains('roll first')
    }

    void "dados sets each axis budget to its die (no bonus)"() {
        given:
        def p = playerAt00('DiceRoll')

        when:
        coordinateStateService.handleDadosCommand(p, dummyWriter())
        def s = coordinateStateService.turnStateFor(p.username)

        then:
        s != null
        s.yBudget == s.yDie
        s.xBudget == s.xDie
        s.yDie >= 1 && s.yDie <= 6 && s.xDie >= 1 && s.xDie <= 6
    }

    void "moving spends and locks that axis; the other axis still moves; then the roll clears"() {
        given:
        def p = playerAt00('DiceMove')
        def w = dummyWriter()
        coordinateStateService.handleDadosCommand(p, w)

        when: "spend the X axis: east 1 → (1,0)"
        coordinateStateService.handleMoveCommand('move east 1', p, w)

        then:
        coordinateStateService.turnStateFor(p.username).xUsed
        LambdaPlayer.get(p.id).positionX == 1

        when: "a second X move is refused"
        def refused = coordinateStateService.handleMoveCommand('move east 1', p, w)

        then:
        refused.toLowerCase().contains('already committed')

        when: "spend the Y axis: north 1 → (1,1); both axes spent clears the roll"
        coordinateStateService.handleMoveCommand('move north 1', p, w)

        then:
        coordinateStateService.turnStateFor(p.username) == null
        LambdaPlayer.get(p.id).positionY == 1
    }

    void "a count over the axis budget is rejected and does not lock the axis"() {
        given:
        def p = playerAt00('DiceOver')
        coordinateStateService.handleDadosCommand(p, dummyWriter())
        def budget = coordinateStateService.turnStateFor(p.username).xBudget

        when:
        def out = coordinateStateService.handleMoveCommand("move east ${budget + 1}", p, dummyWriter())

        then:
        out.toLowerCase().contains('budget')
        !coordinateStateService.turnStateFor(p.username).xUsed
    }

    void "movementRangeBonus adds to each axis only while the recursion window is active"() {
        given: "a player with an active recurse-movement effect (+2 range)"
        def p = playerAt00('DiceBonus')
        LambdaPlayer.withTransaction {
            def m = LambdaPlayer.get(p.id)
            m.movementRangeBonus = 2
            m.activeRecursionEffect = 'movement'
            m.recursionEffectExpires = new Date(System.currentTimeMillis() + 60000)
            m.save(failOnError: true)
        }
        def fresh = LambdaPlayer.withTransaction { LambdaPlayer.get(p.id) }

        when:
        coordinateStateService.handleDadosCommand(fresh, dummyWriter())
        def s = coordinateStateService.turnStateFor(p.username)

        then:
        s.yBudget == s.yDie + 2
        s.xBudget == s.xDie + 2
    }
}
