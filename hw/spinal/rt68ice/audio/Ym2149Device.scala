package rt68ice.audio

import rt68ice.core.M68KBus
import spinal.core._
import spinal.lib._

import scala.language.postfixOps

case class Ym2149Device() extends Component {
  val io = new Bundle {
    val bus = slave(M68KBus())
    val sel = in Bool() // chip select from decoder
    val pwmAudio = out Bool() // PWM sound
  }

  private val enable4Mhz = new Area {
    // Fractional divider: four ticks per 25 cycles of the 25 MHz system clock.
    // Tick intervals are six or seven cycles (4 MHz on average).
    private val psgEn = Reg(UInt(5 bits)) init 0
    val tick = psgEn >= 21
    when(tick) {
      psgEn := psgEn - 21
    } otherwise {
      psgEn := psgEn + 4
    }
  }

  // -- PSG --
  private val psg = new Ym2149BB()
  psg.io.en_clk_psg_i := enable4Mhz.tick // Host interface clock = 4 MHz
  psg.io.sel_n_i := False // False -> Divide by 2 -> Sound generator clock = 2 Mhz
  psg.io.data_i := io.bus.dataOut(7 downto 0)
  io.bus.dataIn := psg.io.data_r_o.resized

  /*
    |SND_EN|RW (68k) |A1 (68k)|BDIR|BC1|PSG State    |
    +------+---------+--------+----+---+-------------+
    |0     |X        |X       |0   |0  |Inactive     |
    |1     |1 (Read) |1       |0   |0  |Inactive     |
    |1     |1 (Read) |0       |0   |1  |Read Data    |
    |1     |0 (Write)|1       |1   |0  |Write Data   |
    |1     |0 (Write)|0       |1   |1  |Latch Address|

    Note: It works like on a real Atari ST:
    - reading from the Address Latch offset (A1=0) is
      the standard way to read data back,
    - reading from A1=1 usually results in an inactive bus.
  */
  // Qualify the address decode with a byte strobe, as on the SPI peripheral.
  private val psgSel = io.sel && (io.bus.uds || io.bus.lds)
  psg.io.bdir_i := psgSel && io.bus.wr
  psg.io.bc_i := psgSel && !io.bus.address(1)

  // -- PWM --
  private val pwm = DeltaSigmaPWM(sampleSizeInBits = 14)
  pwm.io.sound := psg.io.pcm14s_o
  pwm.io.sample := True
  io.pwmAudio := pwm.io.pwm
}
