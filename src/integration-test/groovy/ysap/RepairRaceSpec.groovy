package ysap

import grails.testing.mixin.integration.Integration
import grails.gorm.transactions.Rollback
import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Specification

/**
 * Phase 4: repair mini-game reward + the two-player race condition (the HEAD-commit TODO).
 *
 * The socket harness cannot drive the timing-based digit-lock to the exact random code, so the
 * winner/loser/reward behaviour is verified at the service/integration level: the atomic claim
 * (`tryClaimRepair`) resolves the race, and `completeRepair` rewards the winner / kicks the loser.
 */
@Integration
@Rollback
class RepairRaceSpec extends Specification {

    @Autowired CoordinateStateService coordinateStateService
    @Autowired LambdaPlayerService lambdaPlayerService
    @Autowired SimpleRepairService simpleRepairService

    private LambdaPlayer player(String name) {
        def p = lambdaPlayerService.createPlayer(name, name, 'CLASSIC_LAMBDA')
        return LambdaPlayer.get(p.id)
    }

    private SimpleRepairService.RepairSession winningSession(String username, int level, int x, int y) {
        def s = new SimpleRepairService.RepairSession(
            playerUsername: username, matrixLevel: level, targetX: x, targetY: y,
            repairCode: '123', lockedDigits: [1, 2, 3], currentSlot: 3, isActive: true)
        assert s.isCorrect()
        return s
    }

    void "tryClaimRepair resolves the race: the first claimant wins, the second loses"() {
        given: "a wiped coordinate"
        coordinateStateService.damageCoordinate(2, 4, 4, 100)

        expect: "exactly one claim succeeds"
        coordinateStateService.tryClaimRepair(2, 4, 4)        // first → wins
        !coordinateStateService.tryClaimRepair(2, 4, 4)       // already repaired → loses
    }

    void "the winner is rewarded with bits and the success box shows the prize"() {
        given:
        def p = player('repair_winner')
        coordinateStateService.damageCoordinate(2, 5, 5, 100)
        int before = LambdaPlayer.get(p.id).bits

        when:
        def result = simpleRepairService.completeRepair(winningSession('repair_winner', 2, 5, 5))

        then:
        result.gameWon
        result.message.contains("+${SimpleRepairService.REPAIR_REWARD_BITS} bits")
        LambdaPlayer.get(p.id).bits == before + SimpleRepairService.REPAIR_REWARD_BITS
    }

    void "repairPanelLines renders an in-place slot panel from live session state (the HUD fix)"() {
        given: "a player adjacent to a freshly wiped coordinate, repair initiated"
        def p = player('repair_panel')
        LambdaPlayer.withTransaction {
            def m = LambdaPlayer.get(p.id); m.currentMatrixLevel = 1; m.positionX = 0; m.positionY = 0; m.save(failOnError: true)
        }
        coordinateStateService.damageCoordinate(1, 0, 1, 100)   // wipe the adjacent (0,1)
        def res = simpleRepairService.initiateRepair(LambdaPlayer.get(p.id), 0, 1, new PrintWriter(new StringWriter()))

        expect: "init succeeded and the panel is a fixed layout (CODE/YOU rows, active-slot caret, hints)"
        res.success
        def lines = simpleRepairService.repairPanelLines('repair_panel')
        lines != null
        lines.any { it.contains('SECTOR REPAIR') }
        lines.any { it.startsWith('  CODE') }
        lines.any { it.startsWith('  YOU') }
        lines.any { it.contains('^') }                                  // caret marks the spinning slot
        lines.any { it.toLowerCase().contains('lock the spinning digit') }
        lines.every { it.length() <= 56 }                               // fits the HUD panel, no truncation

        when: "the first slot is locked"
        simpleRepairService.handleSpaceBarPress('repair_panel')

        then: "the panel advances — slot 2 is now the active (caret) slot"
        def after = simpleRepairService.repairPanelLines('repair_panel')
        after.any { it.contains('Slot 2 of') }

        and: "with no active session, the panel is null (drives the HUD self-heal back to the map)"
        simpleRepairService.stopRepairSession('repair_panel')
        simpleRepairService.repairPanelLines('repair_panel') == null
    }

    void "a player who finishes second is pre-empted: no prize, session ended"() {
        given: "the coordinate has already been repaired by someone else"
        def p = player('repair_loser')
        coordinateStateService.damageCoordinate(2, 6, 6, 100)
        coordinateStateService.tryClaimRepair(2, 6, 6)        // another entity already won it
        int before = LambdaPlayer.get(p.id).bits

        when:
        def result = simpleRepairService.completeRepair(winningSession('repair_loser', 2, 6, 6))

        then: "kicked out with no reward"
        !result.gameWon
        result.message.toUpperCase().contains('PRE-EMPTED')
        LambdaPlayer.get(p.id).bits == before
        !simpleRepairService.isPlayerInRepairSession('repair_loser')
    }
}
