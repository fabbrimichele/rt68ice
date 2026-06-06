package rt68ice.memory

import rt68ice.core.M68KBus
import spinal.core._
import spinal.lib._

import scala.language.postfixOps

//noinspection TypeAnnotation
//noinspection ScalaWeakerAccess
case class SdRamDevice() extends Component {
  val io = new Bundle {
    val bus       = slave(M68KBus())
    val sel       = in Bool()
    val cpuClkEn  = in Bool()
    val sdRam     = master(SdRam())
  }

  val sdRam = new SdRamBB()

  io.sdRam.dq     := sdRam.io.sd_data
  io.sdRam.a      := sdRam.io.sd_addr
  io.sdRam.dm     := sdRam.io.sd_dqm
  io.sdRam.ba     := sdRam.io.sd_ba
  io.sdRam.cs_n   := sdRam.io.sd_cs
  io.sdRam.we_n   := sdRam.io.sd_we
  io.sdRam.ras_n  := sdRam.io.sd_ras
  io.sdRam.cas_n  := sdRam.io.sd_cas
  io.sdRam.clock  := sdRam.io.sd_clk

  // CPU/Chipset interface
  sdRam.io.clk_8_en := io.cpuClkEn
  sdRam.io.din      := io.bus.dataOut
  io.bus.dataIn     := sdRam.io.dout
  sdRam.io.addr     := io.bus.address(23 downto 1).resized // access by words
  sdRam.io.ds       := io.bus.uds ## io.bus.lds
  sdRam.io.we       := io.bus.wr
  sdRam.io.req      := io.sel

  // unused: rom_oe, rom_addr, rom_dout, dout64
  sdRam.io.rom_oe   := False
  sdRam.io.rom_addr := B(0, 24 bits)
}
