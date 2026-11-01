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

    Seq(
      dut.io.romBus,
      dut.io.ramBus,
      dut.io.ledBus,
      dut.io.uartBus,
      dut.io.videoBus,
      dut.io.sdRamBus,
      dut.io.counterBus,
      dut.io.ledsBus,
      dut.io.usbBus,
      dut.io.timerBus,
      dut.io.spiBus, dut.io.psgBus, dut.io.ds1302Bus
    ).foreach { port =>
      port.dataIn #= 0
      port.irq #= false
    }

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
    driveTransfer(0x00e20000L, busState = 1, uds = false, lds = false)
    assert(!dut.io.busErr.toBoolean, "An idle unmapped address raised BERR")

    driveTransfer(0x00e20000L, busState = 3, uds = false, lds = false)
    assert(!dut.io.busErr.toBoolean, "An unstrobed post-increment address raised BERR")

    // A genuine transfer to an unmapped address must still raise BERR.
    driveTransfer(0x00e20000L, busState = 2, uds = true, lds = true)
    assert(dut.io.busErr.toBoolean, "An active unmapped read did not raise BERR")

    driveTransfer(0x00e20000L, busState = 3, uds = false, lds = true)
    assert(dut.io.busErr.toBoolean, "An active unmapped write did not raise BERR")

    // Mapped SDRAM accesses and TG68K autovector interrupt-acknowledge cycles
    // remain valid transfers.
    driveTransfer(0x00800000L, busState = 3, uds = true, lds = true)
    assert(dut.io.sdRamBus.sel.toBoolean, "The second SDRAM bank was not selected")
    assert(!dut.io.busErr.toBoolean, "The second SDRAM bank raised BERR")

    driveTransfer(0x00c00000L, busState = 3, uds = true, lds = true)
    assert(dut.io.sdRamBus.sel.toBoolean, "The final 2 MiB SDRAM mapping was not selected")
    assert(!dut.io.busErr.toBoolean, "The final 2 MiB SDRAM mapping raised BERR")

    // This is the upper-SDRAM address reported by EmuTOS after the Line-F
    // exception. It must remain a valid, strobe-qualified SDRAM transfer.
    driveTransfer(0x00d82000L, busState = 2, uds = true, lds = true)
    assert(dut.io.sdRamBus.sel.toBoolean, "$D82000 was not selected as SDRAM")
    assert(!dut.io.busErr.toBoolean, "$D82000 raised BERR")

    driveTransfer(0x00dffffeL, busState = 3, uds = true, lds = true)
    assert(dut.io.sdRamBus.sel.toBoolean, "The final SDRAM word was not selected")
    assert(!dut.io.busErr.toBoolean, "The final SDRAM word raised BERR")

    driveTransfer(0xfffffff2L, busState = 2, uds = true, lds = true)
    assert(!dut.io.busErr.toBoolean, "An interrupt-acknowledge cycle raised BERR")

    // Interrupt priorities descend from USB 6 through UART 3.
    dut.io.timerBus.irq #= true
    sleep(1)
    assert(dut.io.ipl.toInt == 2, "The timer did not request interrupt level 5")

    dut.io.videoBus.irq #= true
    sleep(1)
    assert(dut.io.ipl.toInt == 2, "Video incorrectly took priority over the timer")

    dut.io.usbBus.irq #= true
    sleep(1)
    assert(dut.io.ipl.toInt == 1, "USB did not take priority over the timer")

    dut.io.usbBus.irq #= false
    dut.io.timerBus.irq #= false
    sleep(1)
    assert(dut.io.ipl.toInt == 3, "Video did not request interrupt level 4")

    dut.io.videoBus.irq #= false
    dut.io.uartBus.irq #= true
    sleep(1)
    assert(dut.io.ipl.toInt == 4, "UART did not request interrupt level 3")
  }
}
