package rt68ice.memory

import spinal.core._
import spinal.core.sim._

// Assuming your M68KBus definition is in rt68ice.core
import rt68ice.core.M68KBus

object SdRamDeviceSim extends App {
  SimConfig
    .withWave
    .withVerilator // Required to simulate the Verilog inside the BlackBox
    .compile {
      val dut = SdRamDevice()
      // If your BlackBox doesn't already have an addRTLPath call inside it,
      // you may need to ensure the Verilog is available here depending on your project setup.
      dut
    }
    .doSim { dut =>
      // 1. Setup the clock domain (e.g., 100 MHz)
      dut.clockDomain.forkStimulus(period = 10)

      // 2. Initialize the M68K bus signals to an idle state
      dut.io.sel #= false
      dut.io.bus.wr #= false
      dut.io.bus.uds #= false
      dut.io.bus.lds #= false
      dut.io.bus.address #= 0
      dut.io.bus.dataOut #= 0

      // Wait for reset and initialization to settle
      dut.clockDomain.waitSampling(20)

      // --- 68k Bus Emulation Helpers ---

      def m68kWrite(address: Long, data: Int, uds: Boolean = true, lds: Boolean = true): Unit = {
        // Phase 1: Assert Address and R/W
        dut.io.bus.address #= address
        dut.io.bus.wr #= true
        dut.io.bus.dataOut #= data
        dut.clockDomain.waitSampling(1)

        // Phase 2: Assert Address Strobe (sel)
        dut.io.sel #= true
        dut.clockDomain.waitSampling(1)

        // Phase 3: Assert Data Strobes (Triggers your wrTrigger)
        dut.io.bus.uds #= uds
        dut.io.bus.lds #= lds

        // Phase 4: Wait for the SDRAM controller to acknowledge via cpuClkEn
        // cpuClkEn will drop low while waiting, then go high when p0_ready is asserted
        while (!dut.io.cpuClkEn.toBoolean) {
          dut.clockDomain.waitSampling(1)
        }

        // Hold for one clock to complete the handshake, then deassert
        dut.clockDomain.waitSampling(1)
        dut.io.sel #= false
        dut.io.bus.uds #= false
        dut.io.bus.lds #= false
        dut.clockDomain.waitSampling(2) // Bus idle cycles
      }

      def m68kRead(address: Long, mockSdramData: Int, uds: Boolean = true, lds: Boolean = true): Int = {
        // Phase 1: Assert Address and R/W
        dut.io.bus.address #= address
        dut.io.bus.wr #= false
        dut.clockDomain.waitSampling(1)

        // Phase 2: Assert Address Strobe and Data Strobes
        dut.io.sel #= true
        dut.io.bus.uds #= uds
        dut.io.bus.lds #= lds

        // --- MOCK SDRAM RESPONSE ---
        // Wait for the exact number of cycles it takes for your controller
        // to issue the ACTIVATE, wait tRCD, issue READ, and wait CAS Latency.
        // (You may need to tweak this number based on your exact tRCD + CAS settings)
        dut.clockDomain.waitSampling(5)

        // Force the SDRAM data bus from the testbench
        // Note: In SpinalHDL, simulating inout pins with Verilator often requires
        // writing to the simulated input port of the analog pin.
        dut.io.sdRam.dq #= mockSdramData

        // Phase 3: Wait for acknowledgment from your controller
        while (!dut.io.cpuClkEn.toBoolean) {
          dut.clockDomain.waitSampling(1)
        }

        // Capture data right when cpuClkEn goes high
        val capturedData = dut.io.bus.dataIn.toInt

        // Handshake complete, deassert
        dut.clockDomain.waitSampling(1)
        dut.io.sel #= false
        dut.io.bus.uds #= false
        dut.io.bus.lds #= false

        // Clear the mock data from the bus
        dut.io.sdRam.dq #= 0
        dut.clockDomain.waitSampling(2)

        capturedData
      }

      // --- The Test Sequence ---

      // Give the SDRAM controller time to run its ~100us initialization sequence
      println("Waiting for SDRAM init...")
      dut.clockDomain.waitSampling(100) // Increase this if your Verilog init takes longer

      val testAddress = 0x40000L
      val testData = 0xAAAA

      println(f"Writing 0x$testData%04X to 0x$testAddress%08X...")
      m68kWrite(testAddress, testData)

      println(f"Reading from 0x$testAddress%08X...")
      val readResult = m68kRead(testAddress, mockSdramData = 0xAAAA)
      println(f"Result: 0x$readResult%04X")

      // Add a small buffer at the end so GTKWave captures the final state
      dut.clockDomain.waitSampling(20)
    }
}