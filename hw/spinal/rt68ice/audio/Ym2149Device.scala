package rt68ice.audio

import rt68ice.core.M68KBus
import spinal.core._
import spinal.lib._

import scala.language.postfixOps

case class Ym2149Device() extends Component {
  val io = new Bundle {
    val bus = slave(M68KBus())
    val sel = in Bool() // chip select from decoder
    val cpuClkEn = out Bool() // Hold the CPU until the PSG access completes.
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
  private object AccessState extends SpinalEnum {
    val IDLE, LATCH, SETUP, WAIT_TICK, CAPTURE, DONE = newElement()
  }
  private val state = Reg(AccessState()) init AccessState.IDLE
  private val address = Reg(Bits(32 bits)) init 0
  private val write = Reg(Bool()) init False
  private val data = Reg(Bits(8 bits)) init 0
  private val readData = Reg(Bits(8 bits)) init 0

  // Detect consecutive word phases even if the CPU keeps its strobes asserted.
  private val newPhase = io.bus.address =/= address || io.bus.wr =/= write
  io.cpuClkEn := !psgSel || (state === AccessState.DONE && !newPhase)
  io.bus.dataIn := readData.resized

  private val commandActive = state === AccessState.SETUP || state === AccessState.WAIT_TICK
  psg.io.data_i := data
  psg.io.bdir_i := commandActive && write
  psg.io.bc_i := commandActive && !address(1)

  when(!psgSel) {
    state := AccessState.IDLE
  } otherwise {
    switch(state) {
      is(AccessState.IDLE) {
        state := AccessState.LATCH
      }
      is(AccessState.LATCH) {
        // Allow the CPU's address and write data to settle before capturing.
        address := io.bus.address
        write := io.bus.wr
        data := io.bus.dataOut(7 downto 0)
        state := AccessState.SETUP
      }
      is(AccessState.SETUP) {
        // The VHDL core registers BDIR, BC1 and data on every system clock.
        state := AccessState.WAIT_TICK
      }
      is(AccessState.WAIT_TICK) {
        when(enable4Mhz.tick) {
          // Execute once, then remove the command before the next PSG tick.
          state := AccessState.CAPTURE
        }
      }
      is(AccessState.CAPTURE) {
        // The core's registered read result is now valid, one cycle after tick.
        readData := psg.io.data_r_o
        state := AccessState.DONE
      }
      is(AccessState.DONE) {
        when(newPhase) {
          state := AccessState.LATCH
        }
      }
    }
  }

  // -- PWM --
  private val pwm = DeltaSigmaPWM(sampleSizeInBits = 14)
  pwm.io.sound := psg.io.pcm14s_o
  pwm.io.sample := True
  io.pwmAudio := pwm.io.pwm
}
