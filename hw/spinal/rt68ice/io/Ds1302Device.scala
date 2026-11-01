package rt68ice.io

import rt68ice.core.M68KDeviceBus
import spinal.core._
import spinal.lib.{BufferCC, CounterFreeRun, master, slave}

import scala.language.postfixOps

//noinspection TypeAnnotation
//noinspection ScalaWeakerAccess
case class Ds1302Device() extends Component {
  val io = new Bundle {
    val bus = slave(M68KDeviceBus())
    val cpuClkEn = out Bool()
    val ds1302 = master(Ds1302())
  }

  // This device does not generate interrupt requests.
  io.bus.irq := False

  // New divided clock (250 KHz for 25 MHz clock)
  val divider = CounterFreeRun(50)
  val rtcClock = Reg(Bool()) init False
  when (divider.willOverflow) {
    rtcClock := !rtcClock
  }

  val rtcCd = ClockDomain(
    clock = rtcClock,
    reset = ClockDomain.current.readResetWire,
    config = ClockDomainConfig(
      resetKind = ASYNC,
      resetActiveLevel = HIGH
    ),
    frequency = FixedFrequency(250 kHz)
  )

  val ds1302 = rtcCd {
    new Ds1302BB()
  }

  // External interface
  io.ds1302.ce := ds1302.io.CE
  io.ds1302.sclk := ds1302.io.SCLK
  io.ds1302.io <> ds1302.io.IO

  // The VHDL uses the request edge as a clock for BUSY. Never feed it a
  // combinational address decode: a transient can assert BUSY without a
  // request surviving until the slower serial engine samples it.
  private object AccessState extends SpinalEnum {
    val IDLE, LATCH, START, WAIT_BUSY, WAIT_DONE, DONE = newElement()
  }
  private val state = Reg(AccessState()) init AccessState.IDLE
  private val address = Reg(Bits(32 bits)) init 0
  private val write = Reg(Bool()) init False
  private val writeData = Reg(Bits(8 bits)) init 0
  private val readData = Reg(Bits(8 bits)) init 0
  private val requestActive = Reg(Bool()) init False
  private val selected = io.bus.sel && io.bus.uds
  private val newPhase = io.bus.address =/= address || io.bus.wr =/= write
  private val busy = BufferCC(ds1302.io.BUSY, init = False)
  // The serial result settles before BUSY falls. Capture these synchronized
  // bits only after observing that completion, with the address still held.
  private val returnedData = BufferCC(ds1302.io.DO, init = B(0, 8 bits))

  ds1302.io.nCE := !requestActive
  ds1302.io.nRD := write
  ds1302.io.nWR := !write
  ds1302.io.A := address(6 downto 1)
  ds1302.io.DI := writeData

  // Stall only the selected CPU transfer. A completed result remains valid
  // until the CPU leaves the access, including any other bus wait states.
  io.cpuClkEn := !selected || (state === AccessState.DONE && !newPhase)
  io.bus.dataIn(15 downto 8) := readData
  io.bus.dataIn(7 downto 0) := 0

  switch(state) {
    is(AccessState.IDLE) {
      when(selected) { state := AccessState.LATCH }
    }
    is(AccessState.LATCH) {
      when(selected) {
        address := io.bus.address
        write := io.bus.wr
        writeData := io.bus.dataOut(15 downto 8)
        state := AccessState.START
      } otherwise {
        state := AccessState.IDLE
      }
    }
    is(AccessState.START) {
      when(!selected) {
        state := AccessState.IDLE
      } elsewhen(newPhase) {
        state := AccessState.LATCH
      } otherwise {
        // Address and direction have been stable for a full system cycle.
        requestActive := True
        state := AccessState.WAIT_BUSY
      }
    }
    is(AccessState.WAIT_BUSY) {
      when(busy) { state := AccessState.WAIT_DONE }
    }
    is(AccessState.WAIT_DONE) {
      when(!busy) {
        readData := returnedData
        requestActive := False
        state := AccessState.DONE
      }
    }
    is(AccessState.DONE) {
      when(!selected) {
        state := AccessState.IDLE
      } elsewhen(newPhase) {
        // TG68K can keep its strobes asserted between consecutive words.
        state := AccessState.LATCH
      }
    }
  }
}
