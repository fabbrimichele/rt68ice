package rt68ice.core

import spinal.core.sim._

object MemoryMapSim extends App {
  SimConfig.compile(BusController()).doSim { dut =>
    dut.clockDomain.forkStimulus(period = 10)

    dut.io.cpuBus.dataOut #= 0
    dut.io.cpuBus.wr #= false
    dut.io.cpuBus.uds #= true
    dut.io.cpuBus.lds #= true
    dut.io.busState #= 2 // Data read
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

    def driveAddress(address: Long): Unit = {
      dut.io.cpuBus.address #= address
      sleep(1)
    }

    driveAddress(0x00000000L)
    assert(dut.io.romSel.toBoolean, "Reset vectors are not mapped to ROM")

    driveAddress(0x00000008L)
    assert(dut.io.ramSel.toBoolean, "Low vector memory is not mapped to fast RAM")

    driveAddress(0x00003ffeL)
    assert(dut.io.ramSel.toBoolean, "The end of fast RAM is not mapped")

    driveAddress(0x00004000L)
    assert(dut.io.sdRamSel.toBoolean, "SDRAM does not follow the fast RAM overlay")

    driveAddress(0x007ffffeL)
    assert(dut.io.sdRamSel.toBoolean, "The final SDRAM word is not mapped")

    driveAddress(0x00800000L)
    assert(dut.io.busErr.toBoolean, "The address after the SDRAM window is unexpectedly mapped")

    driveAddress(0x00e00000L)
    assert(dut.io.vidFbSel.toBoolean, "The framebuffer is not mapped at $E00000")

    driveAddress(0x00f00000L)
    assert(dut.io.ledSel.toBoolean, "The LED peripheral is not mapped at $F00000")

    driveAddress(0x00f04000L)
    assert(dut.io.uartSel.toBoolean, "The UART is not mapped at $F04000")

    driveAddress(0x00f08000L)
    assert(dut.io.vidPalSel.toBoolean, "The video palette is not mapped at $F08000")

    driveAddress(0x00f0c000L)
    assert(dut.io.vidCtrlSel.toBoolean, "Video control is not mapped at $F0C000")

    driveAddress(0x00f10000L)
    assert(dut.io.counterSel.toBoolean, "The counter is not mapped at $F10000")

    driveAddress(0x00f14000L)
    assert(dut.io.ledsSel.toBoolean, "The LED array is not mapped at $F14000")

    driveAddress(0x00f18000L)
    assert(dut.io.usbSel.toBoolean, "USB is not mapped at $F18000")

    driveAddress(0x00fc0000L)
    assert(dut.io.romSel.toBoolean, "The boot ROM is not mapped at $FC0000")

    driveAddress(0x0000c000L)
    assert(dut.io.sdRamSel.toBoolean, "The former UART window is not available as SDRAM")
    assert(!dut.io.uartSel.toBoolean, "The UART still responds at its former address")

    driveAddress(0xfffffff2L)
    assert(!dut.io.busErr.toBoolean, "An interrupt-acknowledge cycle raised BERR")
  }
}
