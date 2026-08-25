package rt68ice.timer

import rt68ice.core.M68KBus
import spinal.core._
import spinal.lib._

import scala.language.postfixOps

object Timer {
  val ControlAddress = 0
  val StatusAddress = 1
  val DividerHighAddress = 2
  val DividerLowAddress = 3
  val ReloadHighAddress = 4
  val ReloadLowAddress = 5
  val ValueHighAddress = 6
  val ValueLowAddress = 7

  val EnableBit = 0
  val AutoReloadBit = 1
  val InterruptEnableBit = 2
  val ReloadCommandBit = 3

  val InterruptPendingBit = 0
}

// Programmable, memory-mapped countdown timer.
//
// The divider produces one timer tick every (divider + 1) input clocks.  The
// timer expires after reloadValue ticks, then either stops or reloads itself.
// Expiry is latched independently of the interrupt mask so the device can also
// be polled. Software acknowledges an expiry by reading STATUS.
//noinspection TypeAnnotation
//noinspection ScalaWeakerAccess
case class Timer() extends Component {
  import Timer._

  val io = new Bundle {
    val bus = slave(M68KBus())
    val sel = in Bool()
    val int = out Bool()
  }

  val enabled = RegInit(False)
  val autoReload = RegInit(False)
  val interruptEnable = RegInit(False)
  val interruptPending = RegInit(False)

  val divider = Reg(UInt(32 bits)) init 0
  val dividerCounter = Reg(UInt(32 bits)) init 0
  val reloadValue = Reg(UInt(32 bits)) init 0
  val value = Reg(UInt(32 bits)) init 0

  // Latch the low half when the high half is read, preventing a rollover from
  // producing a torn 32-bit VALUE read on the 68000's 16-bit data bus.
  val valueLowLatch = Reg(Bits(16 bits)) init 0

  io.int := interruptPending && interruptEnable

  val wordAddress = io.bus.address(3 downto 1).asUInt
  val registerRead = io.sel && !io.bus.wr && (io.bus.uds || io.bus.lds)
  val registerWrite = io.sel && io.bus.wr && (io.bus.uds || io.bus.lds)

  // Reading STATUS acknowledges the current expiry. Timer operation below has
  // higher priority so an expiry coincident with the read remains pending.
  when(registerRead && (wordAddress === StatusAddress)) {
    interruptPending := False
  }

  // Timer operation. Register writes below deliberately have higher assignment
  // priority, so stop, start and explicit reload commands are deterministic.
  when(enabled) {
    when(dividerCounter === divider) {
      dividerCounter := 0
      when(value <= 1) {
        interruptPending := True
        when(autoReload) {
          value := reloadValue
        } otherwise {
          value := 0
          enabled := False
        }
      } otherwise {
        value := value - 1
      }
    } otherwise {
      dividerCounter := dividerCounter + 1
    }
  } otherwise {
    dividerCounter := 0
  }

  when(registerRead && (wordAddress === ValueHighAddress)) {
    valueLowLatch := value(15 downto 0).asBits
  }

  when(registerWrite) {
    switch(wordAddress) {
      is(ControlAddress) {
        // Control bits occupy the low byte of this 16-bit register.
        when(io.bus.lds) {
          enabled := io.bus.dataOut(EnableBit)
          autoReload := io.bus.dataOut(AutoReloadBit)
          interruptEnable := io.bus.dataOut(InterruptEnableBit)

          // Starting a stopped timer and the explicit RELOAD command both
          // begin a complete interval and reset the divider phase.
          when(
            (io.bus.dataOut(EnableBit) && !enabled) ||
              io.bus.dataOut(ReloadCommandBit)
          ) {
            value := reloadValue
            dividerCounter := 0
          }
        }
      }
      is(DividerHighAddress) {
        when(io.bus.uds) { divider(31 downto 24) := io.bus.dataOut(15 downto 8).asUInt }
        when(io.bus.lds) { divider(23 downto 16) := io.bus.dataOut(7 downto 0).asUInt }
      }
      is(DividerLowAddress) {
        when(io.bus.uds) { divider(15 downto 8) := io.bus.dataOut(15 downto 8).asUInt }
        when(io.bus.lds) { divider(7 downto 0) := io.bus.dataOut(7 downto 0).asUInt }
      }
      is(ReloadHighAddress) {
        when(io.bus.uds) { reloadValue(31 downto 24) := io.bus.dataOut(15 downto 8).asUInt }
        when(io.bus.lds) { reloadValue(23 downto 16) := io.bus.dataOut(7 downto 0).asUInt }
      }
      is(ReloadLowAddress) {
        when(io.bus.uds) { reloadValue(15 downto 8) := io.bus.dataOut(15 downto 8).asUInt }
        when(io.bus.lds) { reloadValue(7 downto 0) := io.bus.dataOut(7 downto 0).asUInt }
      }
    }
  }

  val controlRead = B(0, 16 bits)
  controlRead(EnableBit) := enabled
  controlRead(AutoReloadBit) := autoReload
  controlRead(InterruptEnableBit) := interruptEnable

  val statusRead = B(0, 16 bits)
  statusRead(InterruptPendingBit) := interruptPending
  statusRead(1) := enabled

  io.bus.dataIn := 0
  when(registerRead) {
    io.bus.dataIn := wordAddress.mux(
      ControlAddress -> controlRead,
      StatusAddress -> statusRead,
      DividerHighAddress -> divider(31 downto 16).asBits,
      DividerLowAddress -> divider(15 downto 0).asBits,
      ReloadHighAddress -> reloadValue(31 downto 16).asBits,
      ReloadLowAddress -> reloadValue(15 downto 0).asBits,
      ValueHighAddress -> value(31 downto 16).asBits,
      ValueLowAddress -> valueLowLatch,
    )
  }
}
