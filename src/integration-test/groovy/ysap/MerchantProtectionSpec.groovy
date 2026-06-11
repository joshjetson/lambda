package ysap

import grails.testing.mixin.integration.Integration
import grails.gorm.transactions.Rollback
import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Specification

/**
 * Auto-defrag must never wipe the per-level merchant's tile — a wiped coord renders as X (hiding the
 * merchant) and can't be entered, which previously made the only merchant unreachable. Drives the
 * (package-visible) wipe cycle directly so it's deterministic: across this many cycles the merchant's
 * coordinate is guaranteed to be targeted, so a missing guard would wipe it.
 */
@Integration
@Rollback
class MerchantProtectionSpec extends Specification {

    @Autowired AutoDefragService autoDefragService
    @Autowired CoordinateStateService coordinateStateService

    void "auto-defrag never wipes a merchant's coordinate, but still wipes others"() {
        given: "an active merchant at a non-safe coordinate on level 1"
        LambdaMerchant.withTransaction {
            new LambdaMerchant(merchantName: 'TestMerch', matrixLevel: 1, positionX: 5, positionY: 5,
                    merchantType: 'FRAGMENT_TRADER', isActive: true, inventory: '[]').save(failOnError: true)
        }

        when: "many wipe cycles run (each destroys 1-3 random level 1-3 coords)"
        600.times { autoDefragService.destroyRandomCoordinates() }

        then: "the merchant's tile is still intact (a missing guard would have wiped it by now)"
        coordinateStateService.getCoordinateHealth(1, 5, 5).health > 0

        and: "the wiper genuinely ran — other coordinates did get wiped"
        CoordinateState.withTransaction { CoordinateState.findAllByHealthLessThanEquals(0).size() > 0 }
    }
}
