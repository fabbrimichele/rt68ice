package rt68ice.video

import rt68ice.core.M68KBus
import rt68ice.video.VgaRasterEngine.rgbConfig
import spinal.core._
import spinal.lib._
import spinal.lib.graphic.vga._

import scala.language.postfixOps

object VgaDevice {
  val ControlAddress = 0
  val InterruptStatusAddress = 1
  val InterruptEnableAddress = 2

  val VerticalBlankInterruptBit = 0
}

//noinspection TypeAnnotation
//noinspection ScalaWeakerAccess
case class VgaDevice(vgaCd: ClockDomain) extends Component {
  import VgaDevice._

  val io = new Bundle {
    val bus     = slave(M68KBus())
    val fbSel   = in Bool()
    val palSel  = in Bool()
    val ctrlSel = in Bool()
    val int     = out Bool()
    val vga     = master(Vga(rgbConfig))
  }

  // ----------------------
  // Memory definitions
  // ----------------------
  // Framebuffer: 640x480 2bpp = 75 KB
  val framebuffer = Mem(Bits(16 bits), 38400)

  // Palette: 4 colors
  val palette = VgaPalette(vgaCd)
  palette.io.address := io.bus.address
  palette.io.lds := io.bus.lds
  palette.io.uds := io.bus.uds
  palette.io.wr := io.bus.wr
  palette.io.dataOut := io.bus.dataOut
  palette.io.sel := io.palSel

  // Control register
  // bits 1-0: screen mode (0 = 320x240 8bpp, 1 = 640x240 4bpp, 2 = 640x480 2bpp)
  val ctrlReg = Reg(Bits(16 bits)) init 0
  val resolution = ctrlReg(1 downto 0)
  val interruptEnable = RegInit(False)
  val interruptPending = RegInit(False)

  val wordAddress = io.bus.address(2 downto 1).asUInt
  val registerRead = io.ctrlSel && !io.bus.wr && (io.bus.uds || io.bus.lds)
  val registerWrite = io.ctrlSel && io.bus.wr && (io.bus.uds || io.bus.lds)

  when(registerWrite) {
    switch(wordAddress) {
      is(ControlAddress) {
        when(io.bus.uds) { ctrlReg(15 downto 8) := io.bus.dataOut(15 downto 8) }
        when(io.bus.lds) { ctrlReg(7 downto 0) := io.bus.dataOut(7 downto 0) }
      }
      is(InterruptEnableAddress) {
        when(io.bus.lds) {
          interruptEnable := io.bus.dataOut(VerticalBlankInterruptBit)
        }
      }
    }
  }

  // Reading IRQ_STATUS acknowledges the current VBL. The event assignment
  // below has higher priority, so a coincident new frame remains pending.
  when(registerRead && (wordAddress === InterruptStatusAddress)) {
    interruptPending := False
  }

  io.int := interruptPending && interruptEnable

  // ------------------------------------------------------------------
  // 68000 bus side: Framebuffer (Interleaved) + Device data out
  // ------------------------------------------------------------------
  val ramAddress = io.bus.address(16 downto 1).asUInt

  val cpuRamData = framebuffer.readWriteSync(
    address = ramAddress,
    data    = io.bus.dataOut,
    enable  = io.fbSel,
    write   = io.bus.wr,
    mask    = io.bus.uds ## io.bus.lds,
    clockCrossing = true
  )

  // Memory blocks routing when reading
  val interruptStatusRead = B(0, 16 bits)
  interruptStatusRead(VerticalBlankInterruptBit) := interruptPending

  val interruptEnableRead = B(0, 16 bits)
  interruptEnableRead(VerticalBlankInterruptBit) := interruptEnable

  io.bus.dataIn := 0
  when (io.fbSel) {
    io.bus.dataIn := cpuRamData
  } elsewhen io.palSel {
    io.bus.dataIn := palette.io.dataIn
  } elsewhen io.ctrlSel {
    io.bus.dataIn := wordAddress.mux(
      ControlAddress -> ctrlReg,
      InterruptStatusAddress -> interruptStatusRead,
      InterruptEnableAddress -> interruptEnableRead,
      default -> B(0, 16 bits)
    )
  }

  // ----------------------
  // VGA side
  // ----------------------
  val vgaArea = new ClockingArea(vgaCd) {
    val rasterEngine = VgaRasterEngine()
    rasterEngine.io.resolution := BufferCC(resolution)

    // Convert the one-pixel-clock VBL pulse into a toggle so no event is lost
    // while crossing into the system clock domain.
    val vBlankToggle = RegInit(False)
    when(rasterEngine.io.vBlankStart) {
      vBlankToggle := !vBlankToggle
    }

    rasterEngine.io.memData := framebuffer.readSync(
      address = rasterEngine.io.memAddress,
      enable = True,
      clockCrossing = true,
    )

    palette.io.colorIndex := rasterEngine.io.colorIndex

    io.vga.color.r := palette.io.pixelColor(23 downto 16).asUInt
    io.vga.color.g := palette.io.pixelColor(15 downto 8).asUInt
    io.vga.color.b := palette.io.pixelColor(7 downto 0).asUInt

    io.vga.hSync := rasterEngine.io.hSync
    io.vga.vSync := rasterEngine.io.vSync
    io.vga.colorEn := rasterEngine.io.colorEn
  }

  val vBlankToggleSync = BufferCC(vgaArea.vBlankToggle, init = False)
  val vBlankEvent = vBlankToggleSync =/= RegNext(vBlankToggleSync, False)

  when(vBlankEvent) {
    interruptPending := True
  }
}
