package ysap.helpers

import ysap.SimpleRepairService

import java.util.concurrent.*

class DigitCycler {
    int slotIndex
    SimpleRepairService.RepairSession session
    ScheduledExecutorService scheduler
    ScheduledFuture<?> task

    DigitCycler(int slotIndex, SimpleRepairService.RepairSession session, ScheduledExecutorService scheduler) {
        this.slotIndex = slotIndex
        this.session = session
        this.scheduler = scheduler
    }

    void start() {
        stop()

        session.cyclingValues[slotIndex] = new Random().nextInt(10)

        task = scheduler.scheduleAtFixedRate({
            if (!session.isActive) return

            def oldVal = session.cyclingValues[slotIndex] ?: 0
            session.cyclingValues[slotIndex] = (oldVal + 1) % 10

            try {
                def codeDisplay = session.repairCode.split('').join(' ')
                def currentDisplay = session.getCurrentDisplay()
                def status = "Slot ${slotIndex + 1}/${session.repairCode.length()}"
                def updateText = "🔧 REPAIR SECTOR: ${codeDisplay} | Current: ${currentDisplay} | ${status} | ENTER = Lock"

                session.playerWriter.println(updateText)
                session.playerWriter.flush()
            } catch (Exception e) {
                session.isActive = false
            }
        }, 1, session.getCyclingSpeed(), TimeUnit.MILLISECONDS)

        session.cyclingTask = task // 🔄 Maintain your original contract
    }

    void stop() {
        if (task && !task.isCancelled()) {
            task.cancel(false)
        }
    }
}
