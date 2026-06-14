package ysap

import grails.testing.mixin.integration.Integration
import org.springframework.beans.factory.annotation.Autowired
import groovy.json.JsonSlurper
import java.util.concurrent.CountDownLatch
import spock.lang.Specification

/**
 * Per-player + global-unique merchant stock.
 *
 * @Integration without @Rollback (matching TradeSpec): handlePurchase commits the merchant mutation in
 * its own transaction, and the assertions re-read it — so these tests must commit, not roll back.
 *
 * Drives the public seam handleMerchantInteraction(merchant, "shop"/"buy <n>", player) — the same path
 * the telnet dispatch uses — rather than the private display/purchase methods.
 */
@Integration
class MerchantStockSpec extends Specification {

    @Autowired LambdaMerchantService lambdaMerchantService
    @Autowired LambdaPlayerService lambdaPlayerService

    private LambdaPlayer makePlayer(String name) {
        Long id = null
        LambdaPlayer.withTransaction {
            def p = lambdaPlayerService.createPlayer(name.toLowerCase(), name, 'CLASSIC_LAMBDA')
            id = p.id
        }
        return LambdaPlayer.withTransaction { LambdaPlayer.get(id) }
    }

    /** Seed a merchant at a unique throwaway level with a hand-built inventory JSON. */
    private LambdaMerchant makeMerchant(int level, String inventoryJson) {
        Long id = null
        LambdaMerchant.withTransaction {
            def m = new LambdaMerchant(merchantName: "Test Vendor L${level}", matrixLevel: level,
                    positionX: 5, positionY: 5, merchantType: 'FRAGMENT_TRADER', isActive: true,
                    spawnedDate: new Date(), inventory: inventoryJson).save(failOnError: true)
            id = m.id
        }
        return LambdaMerchant.withTransaction { LambdaMerchant.get(id) }
    }

    private String shop(LambdaMerchant m, LambdaPlayer p) {
        lambdaMerchantService.handleMerchantInteraction(m, 'shop', p).output as String
    }

    private Map buy(LambdaMerchant m, int n, LambdaPlayer p) {
        lambdaMerchantService.handleMerchantInteraction(m, "buy ${n}", p)
    }

    private List invFragNames(LambdaMerchant m) {
        LambdaMerchant.withTransaction {
            def inv = new JsonSlurper().parseText(LambdaMerchant.get(m.id).inventory)
            (inv.fragments + inv.specialItems).collect { it.name }
        }
    }

    void "a COMMON item is hidden from the buyer but stays available to other players"() {
        given: "a merchant with a single cheap common fragment, and two players"
        def m = makeMerchant(2, '{"fragments":[{"name":"Data Types","price":40,"rarity":"common"}],"specialItems":[]}')
        def a = makePlayer('CommonBuyerA')
        def b = makePlayer('CommonBuyerB')

        expect: "both initially see it"
        shop(m, a).contains('Data Types')
        shop(m, b).contains('Data Types')

        when: "A buys it"
        def res = buy(m, 1, a)

        then: "the purchase succeeds"
        res.success
        res.output.contains('Purchased Data Types')

        and: "A no longer sees it, but B still does"
        !shop(m, a).contains('Data Types')
        shop(m, b).contains('Data Types')

        and: "the merchant's global inventory still holds it (common items are not globally depleted)"
        invFragNames(m).contains('Data Types')

        and: "B can still buy it"
        buy(m, 1, b).success
    }

    void "a UNIQUE item is removed for everyone once any player buys it (first-come-first-served)"() {
        given: "a merchant with one unique fragment"
        def m = makeMerchant(3, '{"fragments":[{"name":"Exception Handling","price":50,"rarity":"rare","unique":true}],"specialItems":[]}')
        def a = makePlayer('UniqueBuyerA')
        def b = makePlayer('UniqueBuyerB')

        expect: "the shop flags the unique item with a compact '*' marker and a legend (fits the box)"
        def shopOut = shop(m, a)
        shopOut =~ /1\*\s+Exception Handling/
        shopOut.contains('* unique')

        when: "A buys the unique item"
        def res = buy(m, 1, a)

        then: "A gets it"
        res.success

        and: "it is gone from the merchant's global inventory"
        !invFragNames(m).contains('Exception Handling')

        and: "B no longer sees it and cannot buy it"
        !shop(m, b).contains('Exception Handling')
        !buy(m, 1, b).success
    }

    void "two players racing the last UNIQUE item: exactly one wins, no double-sell"() {
        given: "one unique fragment and two players who will buy index 1 simultaneously"
        def m = makeMerchant(5, '{"fragments":[{"name":"Exception Handling","price":50,"rarity":"rare","unique":true}],"specialItems":[]}')
        def a = makePlayer('RaceBuyerA')
        def b = makePlayer('RaceBuyerB')
        def start = new CountDownLatch(1)
        def results = Collections.synchronizedList([])

        when: "both fire buy 1 at the same instant"
        def ta = Thread.start { start.await(); try { results << buy(m, 1, a) } catch (Throwable t) { results << [success: false, output: "EX:${t.class.simpleName}:${t.message}"] } }
        def tb = Thread.start { start.await(); try { results << buy(m, 1, b) } catch (Throwable t) { results << [success: false, output: "EX:${t.class.simpleName}:${t.message}"] } }
        start.countDown()
        ta.join(10000); tb.join(10000)

        then: "exactly one purchase succeeded — the merchant-row version conflict + retry prevented a double-sell"
        results.count { it.success } == 1
        results.count { !it.success } == 1
        results.find { !it.success }.output.contains('Someone just bought the last')

        and: "the item is globally gone"
        !invFragNames(m).contains('Exception Handling')

        and: "exactly one of the two players actually holds it"
        def aHas = LambdaPlayer.withTransaction { LambdaPlayer.get(a.id).logicFragments?.any { it?.name == 'Exception Handling' } }
        def bHas = LambdaPlayer.withTransaction { LambdaPlayer.get(b.id).logicFragments?.any { it?.name == 'Exception Handling' } }
        (aHas ? 1 : 0) + (bHas ? 1 : 0) == 1
    }

    void "buy index maps to the visible list after a unique item is removed"() {
        given: "fragments [F1 common, F2 unique, F3 common] + one common special"
        def json = '{"fragments":[' +
                '{"name":"Data Types","price":10,"rarity":"common"},' +
                '{"name":"Exception Handling","price":10,"rarity":"rare","unique":true},' +
                '{"name":"Conditional Logic","price":10,"rarity":"common"}],' +
                '"specialItems":[{"name":"Scanner Boost","price":10}]}'
        def m = makeMerchant(4, json)
        def a = makePlayer('IndexBuyerA')

        when: "A buys the unique F2 (index 2), removing it globally"
        def r2 = buy(m, 2, a)

        then: "that bought the unique item"
        r2.success
        r2.output.contains('Exception Handling')

        when: "A's visible list re-numbers to [Data Types(1), Conditional Logic(2), Scanner Boost(3)]; buy the tail first so earlier indices stay put"
        def r3 = buy(m, 3, a)   // index 3 is now the special whose number shifted down from 4 to 3

        then: "buy 3 purchases Scanner Boost — the special re-numbered after the unique vanished"
        r3.success
        r3.output.contains('Scanner Boost')

        when: "buy 2 resolves to Conditional Logic — proving index 2 is no longer the gone unique"
        def r2b = buy(m, 2, a)

        then:
        r2b.success
        r2b.output.contains('Conditional Logic')
    }
}
