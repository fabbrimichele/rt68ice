package rt68ice.video

import rt68ice.core.M68KBus
import rt68ice.video.VgaRasterEngine.rgbConfig
import spinal.core._
import spinal.lib._
import spinal.lib.graphic.vga._

import scala.language.postfixOps

//noinspection TypeAnnotation
//noinspection ScalaWeakerAccess
case class VgaDevice(vgaCd : ClockDomain) extends Component {
  val io = new Bundle {
    val bus     = slave(M68KBus())
    val fbSel   = in Bool()
    val palSel  = in Bool()
    val vga     = master(Vga(rgbConfig))
  }

  // ----------------------
  // Memory definitions
  // ----------------------
  // Framebuffer: 640x480 2bpp = 75 KB
  val bank0 = Mem(Bits(16 bits), 19200)
  val bank1 = Mem(Bits(16 bits), 19200)

  // Palette: 4 colors
  val palette = VgaPalette(vgaCd)
  palette.io.address := io.bus.address
  palette.io.lds := io.bus.lds
  palette.io.uds := io.bus.uds
  palette.io.wr := io.bus.wr
  palette.io.dataOut := io.bus.dataOut
  palette.io.sel := io.palSel

  // ----------------------
  // 68000 bus side
  // ----------------------
  // Framebuffer
  val bankSelect = io.bus.address(1)
  val ramAddress = io.bus.address(16 downto 2).asUInt

  val cpuBank0Data = bank0.readWriteSync(
    address = ramAddress,
    data    = io.bus.dataOut,
    enable  = io.fbSel && !bankSelect,
    write   = io.bus.wr,
    mask    = io.bus.uds ## io.bus.lds,
    clockCrossing = true
  )

  val cpuBank1Data = bank1.readWriteSync(
    address = ramAddress,
    data    = io.bus.dataOut,
    enable  = io.fbSel && bankSelect,
    write   = io.bus.wr,
    mask    = io.bus.uds ## io.bus.lds,
    clockCrossing = true
  )

  // Memory blocks routing when reading
  // neither io.fbSel nor io.palSel case is managed by the bus controller
  when (io.fbSel) {
    io.bus.dataIn := Mux(bankSelect, cpuBank1Data, cpuBank0Data)
  } elsewhen io.palSel {
    io.bus.dataIn := palette.io.dataIn
  } otherwise {
    io.bus.dataIn := 0
  }

  // ----------------------
  // VGA side
  // ----------------------
  val vgaArea = new ClockingArea(vgaCd) {
    val rasterEngine = VgaRasterEngine()

    rasterEngine.io.bank0Data := bank0.readSync(
      address = rasterEngine.io.wordAddress,
      enable = True,
      clockCrossing = true,
    )

    rasterEngine.io.bank1Data := bank1.readSync(
      address = rasterEngine.io.wordAddress,
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
}