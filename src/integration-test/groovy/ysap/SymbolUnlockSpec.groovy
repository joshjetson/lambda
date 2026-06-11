package ysap

import grails.testing.mixin.integration.Integration
import grails.gorm.transactions.Rollback
import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Specification

/**
 * Phase 6: the `unlock_symbol` quest. A hidden elemental symbol is unlocked only when the player
 * stands on its coordinate AND holds a discovered ElementalNonce of that element whose commandFlag
 * matches (Option B — the nonce is the key, reusing the existing nonce economy).
 *
 * Symbols are created deterministically here (not relying on the random boot placement) so the
 * positive/negative paths are stable.
 */
@Integration
@Rollback
class SymbolUnlockSpec extends Specification {

    @Autowired ElementalSymbolService elementalSymbolService
    @Autowired LambdaPlayerService lambdaPlayerService

    private LambdaPlayer playerAt(String name, int level, int x, int y) {
        def p = lambdaPlayerService.createPlayer(name.toLowerCase(), name, 'CLASSIC_LAMBDA')
        p = LambdaPlayer.get(p.id)
        p.currentMatrixLevel = level; p.positionX = x; p.positionY = y
        p.save(failOnError: true)
        return p
    }

    private ElementalSymbol placeSymbol(String type, int level, int x, int y) {
        // Boot placement randomly seeds symbols across the map; clear this tile so the test's symbol is
        // the SOLE occupant (else the service may acquire the boot symbol and leave ours hidden → flake).
        ElementalSymbol.findAllByMatrixLevelAndPositionXAndPositionY(level, x, y).each { it.delete(failOnError: true) }
        new ElementalSymbol(symbolType: type, symbolIcon: '🜄', symbolName: "${type} symbol",
                description: 'a hidden elemental force', matrixLevel: level, positionX: x, positionY: y,
                isHidden: true).save(failOnError: true)
    }

    private void giveNonce(LambdaPlayer p, String element, String flag) {
        def n = new ElementalNonce(nonceName: "${element}-${flag}", nonceValue: '0xABCD', elementType: element,
                chemicalClue: 'x1(h)x2(o)', commandFlag: flag, description: 'a discovered key', isDiscovered: true,
                matrixLevel: 1, mapNumber: 1, gameSessionId: 'test', discoveryMethod: 'PUZZLE_ROOM').save(failOnError: true)
        def mp = LambdaPlayer.get(p.id); mp.addToDiscoveredNonces(n); mp.save(failOnError: true)
    }

    void "standing on the symbol with the right discovered-nonce flag acquires it"() {
        given:
        def symbol = placeSymbol('WATER', 1, 3, 3)
        def p = playerAt('SymWinner', 1, 3, 3)
        giveNonce(p, 'WATER', '--h2o')

        when:
        def out = elementalSymbolService.handleUnlockSymbolCommand('unlock_symbol water --h2o', p)

        then:
        out.toUpperCase().contains('SYMBOL ACQUIRED')
        LambdaPlayer.get(p.id).hasWaterSymbol
        ElementalSymbol.get(symbol.id).isHidden == false
    }

    void "unlocking where no symbol of that type sits reports no resonance"() {
        given: "player on a guaranteed-empty coordinate — clear any boot-seeded symbol that randomly landed here"
        def p = playerAt('SymEmpty', 1, 6, 6)
        giveNonce(p, 'WATER', '--h2o')
        ElementalSymbol.findAllByMatrixLevelAndPositionXAndPositionY(1, 6, 6).each { it.delete(failOnError: true) }

        expect:
        elementalSymbolService.handleUnlockSymbolCommand('unlock_symbol water --h2o', p).toLowerCase().contains('resonance')
    }

    void "standing on the symbol without the discovered nonce is refused"() {
        given:
        placeSymbol('FIRE', 1, 2, 2)
        def p = playerAt('SymNoNonce', 1, 2, 2)

        expect:
        elementalSymbolService.handleUnlockSymbolCommand('unlock_symbol fire --burn', p).toLowerCase().contains('not discovered')
        !LambdaPlayer.get(p.id).hasFireSymbol
    }

    void "the right nonce element but a wrong flag is rejected as invalid flag"() {
        given:
        placeSymbol('EARTH', 1, 5, 5)
        def p = playerAt('SymBadFlag', 1, 5, 5)
        giveNonce(p, 'EARTH', '--mineral')

        expect:
        elementalSymbolService.handleUnlockSymbolCommand('unlock_symbol earth --wrong', p).toLowerCase().contains('invalid flag')
        !LambdaPlayer.get(p.id).hasEarthSymbol
    }
}
