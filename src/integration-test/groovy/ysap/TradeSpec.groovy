package ysap

import grails.testing.mixin.integration.Integration
import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Specification

/**
 * Phase 5: `trade` completion via the consent (`offer` → `accept`) flow.
 * Fast service-level coverage of the parser/guards and the standard-fragment transfer; the
 * end-to-end socket path is covered by TradeHarnessSpec (two real clients).
 *
 * @Integration without @Rollback (matching DefragTimerSpec/RecurseSpec): `findChatUser` issues its
 * own query for in-mingle players, which only sees committed rows — so these tests commit.
 */
@Integration
class TradeSpec extends Specification {

    @Autowired ChatService chatService
    @Autowired LambdaPlayerService lambdaPlayerService

    private LambdaPlayer mingler(String display) {
        Long id = null
        LambdaPlayer.withTransaction {
            def p = lambdaPlayerService.createPlayer(display.toLowerCase(), display, 'CLASSIC_LAMBDA')
            p = LambdaPlayer.get(p.id)
            p.isInMingle = true
            p.save(failOnError: true)
            id = p.id
        }
        return LambdaPlayer.withTransaction { LambdaPlayer.get(id) }
    }

    private int bits(LambdaPlayer p) { LambdaPlayer.withTransaction { LambdaPlayer.get(p.id).bits } }

    void "offer without an active trade target is rejected"() {
        given:
        def seller = mingler('OfferGuardSeller')

        expect:
        chatService.handleChatCommand('offer F1 1 10', seller, null).toLowerCase().contains('start a trade first')
    }

    void "accept with no pending offer is rejected"() {
        given:
        def buyer = mingler('AcceptGuardBuyer')

        expect:
        chatService.handleChatCommand('accept', buyer, null).toLowerCase().contains('no pending offer')
    }

    void "a fragment trade moves nothing until the buyer accepts, then transfers item + bits"() {
        given:
        def seller = mingler('FragSeller')
        def buyer = mingler('FragBuyer')
        int sBits = bits(seller)
        int bBits = bits(buyer)

        when: "seller opens a trade and offers their starter fragment for 10 bits"
        chatService.handleChatCommand('trade FragBuyer', seller, null)
        def offered = chatService.handleChatCommand('offer F1 1 10', seller, null)

        then: "consent pending — nothing has moved yet"
        offered.toLowerCase().contains('awaiting')
        bits(seller) == sBits
        bits(buyer) == bBits

        when: "buyer accepts"
        def accepted = chatService.handleChatCommand('accept', buyer, null)

        then: "bits move buyer→seller and the fragment lands on the buyer (stacked onto their starter)"
        accepted.toLowerCase().contains('trade complete')
        bits(seller) == sBits + 10
        bits(buyer) == bBits - 10
        LambdaPlayer.withTransaction {
            LambdaPlayer.get(buyer.id).logicFragments.find { it.name == 'Basic Print' }.quantity == 2
        }
    }

    void "a buyer who cannot afford the price has the trade rejected and keeps their bits"() {
        given:
        def seller = mingler('PoorSeller')
        def buyer = mingler('PoorBuyer')
        LambdaPlayer.withTransaction { def b = LambdaPlayer.get(buyer.id); b.bits = 5; b.save(failOnError: true) }

        when:
        chatService.handleChatCommand('trade PoorBuyer', seller, null)
        chatService.handleChatCommand('offer F1 1 50', seller, null)
        def accepted = chatService.handleChatCommand('accept', buyer, null)

        then:
        accepted.toLowerCase().contains('trade failed')
        bits(buyer) == 5
        LambdaPlayer.withTransaction { LambdaPlayer.get(seller.id).logicFragments.find { it.name == 'Basic Print' } != null }
    }

    void "a bad item code is rejected"() {
        given:
        def seller = mingler('BadCodeSeller')
        def buyer = mingler('BadCodeBuyer')

        when:
        chatService.handleChatCommand('trade BadCodeBuyer', seller, null)
        def out = chatService.handleChatCommand('offer ZZ9 5', seller, null)

        then:
        out.toLowerCase().contains('bad item code')
    }
}
