package rt68ice.core

import spinal.core._
import spinal.core.sim._

object MemoryMapSim extends App {
  SimConfig.compile(BusController()).doSim { dut =>
    dut.clockDomain.forkStimulus(period = 10)
    val cpu = dut.io.cpuBus
    cpu.dataOut #= 0xA55A
    cpu.wr #= false
    cpu.uds #= true
    cpu.lds #= true
    dut.io.busState #= 2

    val ports: Seq[M68KDeviceBusSignals] = Seq(
      dut.io.romBus, dut.io.ramBus, dut.io.ledBus, dut.io.uartBus,
      dut.io.videoBus, dut.io.sdRamBus, dut.io.counterBus, dut.io.ledsBus,
      dut.io.dipSwitchesBus, dut.io.cacheStatsBus, dut.io.usbBus,
      dut.io.timerBus, dut.io.spiBus, dut.io.psgBus, dut.io.i2cBus)
    ports.zipWithIndex.foreach { case (port, index) =>
      port.dataIn #= (0x1000 + index)
      port.irq #= false
      port.ready #= true
    }

    val selects = Seq(dut.io.romBus.sel, dut.io.ramBus.sel, dut.io.ledBus.sel,
      dut.io.uartBus.sel, dut.io.videoBus.fbSel, dut.io.videoBus.palSel,
      dut.io.videoBus.ctrlSel, dut.io.sdRamBus.sel, dut.io.counterBus.sel,
      dut.io.ledsBus.sel, dut.io.dipSwitchesBus.sel, dut.io.cacheStatsBus.sel,
      dut.io.usbBus.sel, dut.io.timerBus.sel, dut.io.spiBus.sel,
      dut.io.psgBus.sel, dut.io.i2cBus.sel)

    // Explicit expected addresses are independent of the decoder definitions.
    case class Window(name: String, base: Long, size: Long, select: Bool, port: M68KDeviceBusSignals)
    val windows = Seq(
      Window("framebuffer", 0x02000000L, 0x20000L, dut.io.videoBus.fbSel, dut.io.videoBus),
      Window("LED",         0x02100000L, 0x4000L, dut.io.ledBus.sel, dut.io.ledBus),
      Window("UART",        0x02104000L, 0x4000L, dut.io.uartBus.sel, dut.io.uartBus),
      Window("palette",     0x02108000L, 0x4000L, dut.io.videoBus.palSel, dut.io.videoBus),
      Window("video control",0x0210C000L,0x4000L,dut.io.videoBus.ctrlSel, dut.io.videoBus),
      Window("counter",     0x02110000L, 0x4000L, dut.io.counterBus.sel, dut.io.counterBus),
      Window("LED array",   0x02114000L, 0x4000L, dut.io.ledsBus.sel, dut.io.ledsBus),
      Window("USB",         0x02118000L, 0x4000L, dut.io.usbBus.sel, dut.io.usbBus),
      Window("timer",       0x0211C000L, 0x4000L, dut.io.timerBus.sel, dut.io.timerBus),
      Window("SPI",         0x02120000L, 0x4000L, dut.io.spiBus.sel, dut.io.spiBus),
      Window("PSG",         0x02124000L, 0x4000L, dut.io.psgBus.sel, dut.io.psgBus),
      Window("I2C",         0x02128000L, 0x4000L, dut.io.i2cBus.sel, dut.io.i2cBus),
      Window("DIP switches",0x0212C000L, 0x4000L, dut.io.dipSwitchesBus.sel, dut.io.dipSwitchesBus),
      Window("cache stats", 0x02130000L, 0x4000L, dut.io.cacheStatsBus.sel, dut.io.cacheStatsBus),
      Window("ROM",         0x021C0000L, 0x4000L, dut.io.romBus.sel, dut.io.romBus))

    dut.clockDomain.waitSampling(2)
    def drive(address: Long): Unit = {
      cpu.address #= address
      sleep(1)
    }
    def checkSelected(address: Long, selected: Bool, port: M68KDeviceBusSignals): Unit = {
      drive(address)
      assert(selected.toBoolean, f"Wrong device selected at $$$address%08X")
      assert(selects.count(_.toBoolean) == 1, "Mapped windows must not overlap")
      assert(!dut.io.busErr.toBoolean, "Mapped transfer must not raise BERR")
      assert(port.address.toLong == address, "Downstream address must retain all CPU bits")
      assert(port.dataOut.toInt == 0xA55A && port.uds.toBoolean && port.lds.toBoolean,
        "Device bus must preserve data and byte strobes")
      assert(cpu.dataIn.toInt == port.dataIn.toInt, "Selected device data must reach CPU")
    }
    def checkUnmapped(address: Long): Unit = {
      drive(address)
      assert(selects.forall(!_.toBoolean), f"Unexpected device alias at $$$address%08X")
      assert(dut.io.busErr.toBoolean, f"Unmapped transfer must raise BERR at $$$address%08X")
    }

    // Reset vectors and fast RAM retain priority over the expanded 32 MiB SDRAM.
    for (address <- Seq(0L, 2L, 6L)) checkSelected(address, dut.io.romBus.sel, dut.io.romBus)
    for (address <- Seq(8L, 0x3FEL)) checkSelected(address, dut.io.ramBus.sel, dut.io.ramBus)
    for (address <- Seq(0x400L, 0x007FFFFEL, 0x00800000L, 0x00BFFFFEL,
      0x00C00000L, 0x00D82000L, 0x00DFFFFEL, 0x00FFFFFEL,
      0x01000000L, 0x01800000L, 0x01FFFFFEL))
      checkSelected(address, dut.io.sdRamBus.sel, dut.io.sdRamBus)

    for (window <- windows) {
      for (address <- Seq(window.base, window.base + 1,
        window.base + window.size - 2, window.base + window.size - 1))
        checkSelected(address, window.select, window.port)

      // Preserve full addresses and direction during writes too. Standard
      // devices receive the gated write; self-timed devices retain direction.
      drive(window.base)
      cpu.wr #= true
      dut.io.busState #= 3
      sleep(1)
      var observedWrite = false
      for (_ <- 0 until 2) {
        dut.clockDomain.waitSampling()
        sleep(1)
        assert(window.select.toBoolean && selects.count(_.toBoolean) == 1)
        assert(window.port.address.toLong == window.base && window.port.dataOut.toInt == 0xA55A)
        observedWrite ||= window.port.wr.toBoolean
      }
      assert(observedWrite, s"${window.name} did not receive a write")
      cpu.wr #= false
      dut.io.busState #= 2
      sleep(1)

      // Former device windows are now RAM, not legacy peripheral aliases.
      checkSelected(window.base - 0x01200000L, dut.io.sdRamBus.sel, dut.io.sdRamBus)
      checkSelected(window.base - 0x01200000L + window.size - 1,
        dut.io.sdRamBus.sel, dut.io.sdRamBus)

      // Dropping bit 25 must not leave this peripheral selected, even where
      // its low-address counterpart legitimately selects fast RAM or SDRAM.
      drive(window.base & 0x01FFFFFFL)
      assert(!window.select.toBoolean, s"${window.name} decode ignores address bit 25")
    }
    for (address <- Seq(0x02020000L, 0x020FFFFEL, 0x02134000L, 0x021BFFFEL, 0x021C4000L))
      checkUnmapped(address)

    // The generic I2C register window follows the ordinary peripheral decode:
    // unlike the old DS1302-specific mapping, it also selects on fetch cycles.
    drive(0x02128000L)
    dut.io.busState #= 0
    sleep(1)
    checkSelected(0x02128000L, dut.io.i2cBus.sel, dut.io.i2cBus)
    checkSelected(0x021C0000L, dut.io.romBus.sel, dut.io.romBus)
    dut.io.busState #= 2
    drive(0xFFFFFFF2L)
    assert(!dut.io.busErr.toBoolean, "Interrupt acknowledgement must not raise BERR")
    assert(selects.forall(!_.toBoolean), "Interrupt acknowledgement must not select a device")
    println("Memory map checks passed: relocated device boundaries, former windows as RAM, bit-25 decode, vectors, I2C window and 32 MiB SDRAM")
  }
}
