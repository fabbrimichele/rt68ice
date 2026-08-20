package rt68ice.core

import spinal.core.sim._

object BusControllerSim extends App {
  SimConfig.compile(BusController()).doSim { dut =>
    dut.clockDomain.forkStimulus(period = 10)

    dut.io.cpuBus.address #= 0
    dut.io.cpuBus.dataOut #= 0
    dut.io.cpuBus.wr #= false
    dut.io.cpuBus.uds #= false
    dut.io.cpuBus.lds #= false
    dut.io.busState #= 1 // No memory access
    dut.io.uartInt #= false
    dut.io.usbInt #= false

    Seq(
      dut.io.romBus,
      dut.io.ramBus,
      dut.io.ledBus,
      dut.io.uartBus,
      dut.io.videoBus,
      dut.io.sdRamBus,
      dut.io.counterBus,
      dut.io.ledsBus,
      dut.io.usbBus
    ).foreach(_.dataIn #= 0)

    dut.clockDomain.waitSampling(2)

    def driveTransfer(address: Long, busState: Int, uds: Boolean, lds: Boolean): Unit = {
      dut.io.cpuBus.address #= address
      dut.io.busState #= busState
      dut.io.cpuBus.uds #= uds
      dut.io.cpuBus.lds #= lds
      sleep(1)
    }

    // A continuously driven or post-incremented address is not an access when
    // the CPU is idle or both data strobes are inactive.
    driveTransfer(0x00800000L, busState = 1, uds = false, lds = false)
    assert(!dut.io.busErr.toBoolean, "An idle unmapped address raised BERR")

    driveTransfer(0x00800000L, busState = 3, uds = false, lds = false)
    assert(!dut.io.busErr.toBoolean, "An unstrobed post-increment address raised BERR")

    // A genuine transfer to an unmapped address must still raise BERR.
    driveTransfer(0x00800000L, busState = 2, uds = true, lds = true)
    assert(dut.io.busErr.toBoolean, "An active unmapped read did not raise BERR")

    driveTransfer(0x00800000L, busState = 3, uds = false, lds = true)
    assert(dut.io.busErr.toBoolean, "An active unmapped write did not raise BERR")

    // Mapped SDRAM accesses and TG68K autovector interrupt-acknowledge cycles
    // remain valid transfers.
    driveTransfer(0x007ffffeL, busState = 3, uds = true, lds = true)
    assert(dut.io.sdRamSel.toBoolean, "The final SDRAM word was not selected")
    assert(!dut.io.busErr.toBoolean, "The final SDRAM word raised BERR")

    driveTransfer(0xfffffff2L, busState = 2, uds = true, lds = true)
    assert(!dut.io.busErr.toBoolean, "An interrupt-acknowledge cycle raised BERR")
  }
}
