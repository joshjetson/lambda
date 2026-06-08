package ysap

import grails.testing.mixin.integration.Integration
import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Specification

/**
 * Deterministic coverage for the Phase 1b combat-timer expiry path.
 *
 * Real defrag encounters spawn on an RNG roll, so the socket harness (GameplayHarnessSpec)
 * cannot reliably exercise this path — and that blind spot once hid a guaranteed runtime crash.
 * This spec injects DefragBotService and drives `expireEncounter(...)` directly (the package-
 * visible test seam), proving the expiry actually fires without exception and applies the loss.
 */
@Integration
class DefragTimerSpec extends Specification {

    @Autowired
    DefragBotService defragBotService

    @Autowired
    LambdaPlayerService lambdaPlayerService

    @Autowired
    SpecialItemService specialItemService

    private Long createPlayerAt(String username, int level, int x, int y, int bits) {
        Long id = null
        LambdaPlayer.withTransaction {
            def avatar = lambdaPlayerService.getAvailableAvatars()[0]
            def p = lambdaPlayerService.createPlayer(username, username, avatar)
            p = LambdaPlayer.get(p.id)
            p.currentMatrixLevel = level
            p.positionX = x
            p.positionY = y
            p.bits = bits
            p.save(failOnError: true)
            id = p.id
        }
        return id
    }

    private Long spawnBotAt(int level, int x, int y) {
        Long id = null
        DefragBot.withTransaction {
            def bot = defragBotService.spawnDefragBot(level, 1, x, y)
            id = bot.id
        }
        return id
    }

    void "timer expiry on an ignored encounter resets the player and deactivates the bot"() {
        given: "a player standing on a non-safe coordinate with a live bot"
        Long playerId = createPlayerAt('timeout_victim', 1, 5, 5, 500)
        Long botId = spawnBotAt(1, 5, 5)

        when: "the encounter timer fires (invoked directly for determinism)"
        defragBotService.expireEncounter(botId, playerId)

        then: "no MissingMethodException, player is defragged back to (0,0)/10 bits, bot deactivated"
        noExceptionThrown()
        LambdaPlayer.withTransaction {
            def p = LambdaPlayer.get(playerId)
            assert p.positionX == 0
            assert p.positionY == 0
            assert p.bits == 10
            true
        }
        DefragBot.withTransaction {
            assert !DefragBot.get(botId).isActive
            true
        }
    }

    void "a player who moved away before the timer is NOT penalized (escape), but the bot still deactivates"() {
        given: "a bot at (6,6) but the player has since moved to (3,3)"
        Long playerId = createPlayerAt('timeout_escapee', 1, 3, 3, 500)
        Long botId = spawnBotAt(1, 6, 6)

        when:
        defragBotService.expireEncounter(botId, playerId)

        then: "player keeps position and bits; bot is cleaned up"
        noExceptionThrown()
        LambdaPlayer.withTransaction {
            def p = LambdaPlayer.get(playerId)
            assert p.positionX == 3
            assert p.positionY == 3
            assert p.bits == 500
            true
        }
        DefragBot.withTransaction {
            assert !DefragBot.get(botId).isActive
            true
        }
    }

    void "an active SWAP_SPACE absorbs a timed-out defrag: no reset, +50 bits, bot deactivated"() {
        given: "a player on a non-safe coord with an armed Swap Space and a live bot there"
        Long playerId = createPlayerAt('timeout_swap', 1, 5, 5, 500)
        LambdaPlayer.withTransaction {
            def p = LambdaPlayer.get(playerId)
            specialItemService.createSpecialItem(p, 'SWAP_SPACE')
        }
        specialItemService.useSpecialItem(LambdaPlayer.withTransaction { LambdaPlayer.get(playerId) }, 'swap_space')
        Long botId = spawnBotAt(1, 5, 5)

        when: "the timer fires — this exercises the SWAP_SPACE branch of defragPlayer"
        defragBotService.expireEncounter(botId, playerId)

        then: "no exception (regression: this path called the wrong service), player NOT reset, +50 bits"
        noExceptionThrown()
        LambdaPlayer.withTransaction {
            def p = LambdaPlayer.get(playerId)
            assert p.positionX == 5 && p.positionY == 5     // not reset to (0,0)
            assert p.bits == 550                            // 500 + 50 absorbed
            true
        }
        DefragBot.withTransaction { !DefragBot.get(botId).isActive }
    }

    void "expiry on an already-killed (inactive) bot is a safe no-op"() {
        given:
        Long playerId = createPlayerAt('timeout_late', 1, 5, 5, 500)
        Long botId = spawnBotAt(1, 5, 5)
        DefragBot.withTransaction {
            def b = DefragBot.get(botId); b.isActive = false; b.save(failOnError: true)
        }

        when: "the timer fires after the bot was already killed"
        defragBotService.expireEncounter(botId, playerId)

        then: "no penalty applied, no exception"
        noExceptionThrown()
        LambdaPlayer.withTransaction {
            def p = LambdaPlayer.get(playerId)
            assert p.positionX == 5
            assert p.bits == 500
            true
        }
    }
}
