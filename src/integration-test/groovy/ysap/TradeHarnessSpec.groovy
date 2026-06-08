package ysap

import grails.testing.mixin.integration.Integration
import spock.lang.Specification

/**
 * Phase 5 end-to-end gate: two real telnet clients complete a trade over the socket, exercising the
 * heap-command map dispatch, the cross-player offer notification (playerSessions writer lookup), and
 * the explicit `accept` consent step — none of which a single-client or service-level test exercises.
 */
@Integration
class TradeHarnessSpec extends Specification {

    static final int PORT = 2323

    void "two entities complete a fragment trade only after explicit accept"() {
        given: "two characters, both in heap"
        def a = new LambdaTelnetClient('localhost', PORT); a.createCharacter('traderalpha', 'TraderAlpha', 1)
        def b = new LambdaTelnetClient('localhost', PORT); b.createCharacter('traderbeta', 'TraderBeta', 1)
        a.command('heap'); b.command('heap')

        when: "A opens a trade with B and offers their starter fragment for 10 bits"
        a.command('trade TraderBeta')
        String offered = a.command('offer F1 1 10')

        then: "A is told the offer is awaiting B's consent"
        offered.toLowerCase().contains('awaiting')

        when: "B accepts"
        String accepted = b.command('accept')

        then: "the trade completes over the wire"
        accepted.toLowerCase().contains('trade complete')

        cleanup:
        a?.close(); b?.close()
    }
}
