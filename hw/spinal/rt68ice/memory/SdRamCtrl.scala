package rt68ice.memory

import spinal.core._
import spinal.lib._

import scala.language.postfixOps

case class SdRamCtrlConfig(
  clockSpeedMhz: Double = 28.4091,
  burstLength: Int = 1,
  burstTypeInterleaved: Boolean = false,
  casLatency: Int = 2,
  writeBurst: Boolean = false,
  p0BurstLength: Int = 1,
)

case class SdRamCtrlPins() extends Bundle with IMasterSlave {
  val dqIn    = Bits(16 bits)
  val dqOut   = Bits(16 bits)
  val dqWrite = Bool()
  val a       = Bits(13 bits)
  val dm      = Bits(2 bits)
  val ba      = Bits(2 bits)
  val cs_n    = Bool()
  val we_n    = Bool()
  val ras_n   = Bool()
  val cas_n   = Bool()
  val cke     = Bool()
  val clock   = Bool()

  override def asMaster(): Unit = {
    in(dqIn)
    out(dqOut, dqWrite, a, dm, ba, cs_n, we_n, ras_n, cas_n, cke, clock)
  }
}

case class SdRamCtrl(config: SdRamCtrlConfig = SdRamCtrlConfig()) extends Component {
  require(Set(1, 2, 4, 8).contains(config.burstLength), "burstLength must be 1, 2, 4, or 8")
  require(Set(1, 2, 4, 8).contains(config.p0BurstLength), "p0BurstLength must be 1, 2, 4, or 8")
  require(config.p0BurstLength <= config.burstLength, "p0BurstLength cannot exceed burstLength")
  require(Set(1, 2, 3).contains(config.casLatency), "casLatency must be 1, 2, or 3")

  val io = new Bundle {
    val initComplete = out Bool()

    val p0Addr      = in Bits(25 bits)
    val p0Data      = in Bits(16 bits)
    val p0ByteEn    = in Bits(2 bits)
    val p0Q         = out Bits(config.p0BurstLength * 16 bits)
    val p0WrReq     = in Bool()
    val p0RdReq     = in Bool()
    val p0Available = out Bool()
    val p0Ready     = out Bool()

    val sdRam = master(SdRamCtrlPins())
  }

  private def ceilCycles(ns: Double): Int =
    scala.math.ceil(ns / (1000.0 / config.clockSpeedMhz)).toInt

  private val settingInhibitDelayMicroSec = 100
  private val settingTrfcNs = 63
  private val settingTrcdNs = 21
  private val settingRefreshTimerNs = 7500
  private val settingTmrdCycles = 2
  private val settingUseFastInputRegister = true

  private val cyclesUntilStartInhibit =
    ceilCycles(settingInhibitDelayMicroSec * 500.0)
  private val cyclesUntilClearInhibit =
    100 + ceilCycles(settingInhibitDelayMicroSec * 1000.0)
  private val cyclesForAutorefresh =
    ceilCycles(settingTrfcNs.toDouble)
  private val cyclesForActiveRow =
    ceilCycles(settingTrcdNs.toDouble)
  private val cyclesAfterWriteForNextCommand =
    ceilCycles(33.0)
  private val cyclesPerRefresh =
    ceilCycles(settingRefreshTimerNs.toDouble)
  private val cyclesUntilInitPrechargeEnd =
    10 + cyclesUntilClearInhibit + ceilCycles(21.0)
  private val cyclesUntilRefresh1End =
    cyclesUntilInitPrechargeEnd + cyclesForAutorefresh
  private val cyclesUntilRefresh2End =
    cyclesUntilRefresh1End + cyclesForAutorefresh

  private val concreteBurstLength = config.burstLength match {
    case 1 => 0
    case 2 => 1
    case 4 => 2
    case 8 => 3
  }

  private val configuredModeValue =
    ((if (config.writeBurst) 0 else 1) << 9) |
      (config.casLatency << 4) |
      ((if (config.burstTypeInterleaved) 1 else 0) << 3) |
      concreteBurstLength

  private object State extends SpinalEnum {
    val init, idle, delay, write, read, readOutput = newElement()
  }

  private object IoOperation extends SpinalEnum {
    val none, write, read = newElement()
  }

  private object Command {
    val nop          = B"4'b0111"
    val active       = B"4'b0011"
    val read         = B"4'b0101"
    val write        = B"4'b0100"
    val precharge    = B"4'b0010"
    val autoRefresh  = B"4'b0001"
    val loadModeReg  = B"4'b0000"
  }

  private val state = RegInit(State.init)
  private val delayState = RegInit(State.idle)
  private val currentIoOperation = RegInit(IoOperation.none)

  private val delayCounter = Reg(UInt(32 bits)) init 0
  private val readCounter = Reg(UInt(4 bits)) init 0
  private val refreshCounter = Reg(UInt(16 bits)) init 0
  private val activePort = Reg(UInt(2 bits)) init 0

  private val command = Reg(Bits(4 bits)) init Command.nop
  private val dqOutput = RegInit(False)
  private val dqData = Reg(Bits(16 bits)) init 0
  private val address = Reg(Bits(13 bits)) init 0
  private val bank = Reg(Bits(2 bits)) init 0
  private val dataMask = Reg(Bits(2 bits)) init B"2'b11"
  private val clockEnable = RegInit(False)
  private val p0ReadyReg = RegInit(False)
  private val p0QReg = Reg(Bits(config.p0BurstLength * 16 bits)) init 0

  private val p0WrQueue = RegInit(False)
  private val p0RdQueue = RegInit(False)
  private val p0ByteEnQueue = Reg(Bits(2 bits)) init 0
  private val p0AddrQueue = Reg(Bits(25 bits)) init 0
  private val p0DataQueue = Reg(Bits(16 bits)) init 0

  private val readBuffer = Reg(Bits(128 bits)) init 0

  private val p0Req = io.p0WrReq || io.p0RdReq
  private val p0ReqQueue = p0WrQueue || p0RdQueue
  private val p0AddrCurrent = p0ReqQueue ? p0AddrQueue | io.p0Addr
  private val portReq = p0Req || p0ReqQueue

  private val activePortAddr = Bits(10 bits)
  private val activePortData = Bits(16 bits)
  private val activePortByteEn = Bits(2 bits)

  activePortAddr := 0
  activePortData := 0
  activePortByteEn := 0
  switch(activePort) {
    is(0) {
      activePortAddr := p0AddrQueue(9 downto 0)
      activePortData := p0DataQueue
      activePortByteEn := p0ByteEnQueue
    }
  }

  io.initComplete := state =/= State.init
  io.p0Available := state === State.idle && !portReq
  io.p0Ready := p0ReadyReg
  io.p0Q := p0QReg

  io.sdRam.dqOut := dqData
  io.sdRam.dqWrite := dqOutput
  io.sdRam.a := address
  io.sdRam.dm := dataMask
  io.sdRam.ba := bank
  io.sdRam.cs_n := command(3)
  io.sdRam.ras_n := command(2)
  io.sdRam.cas_n := command(1)
  io.sdRam.we_n := command(0)
  io.sdRam.cke := clockEnable
  io.sdRam.clock := !ClockDomain.current.readClockWire

  private def setActiveCommand(port: Int, addr: Bits): Unit = {
    command := Command.active
    bank := addr(24 downto 23)
    address := addr(22 downto 10)
    activePort := port
    delayCounter := U(scala.math.max(cyclesForActiveRow - 2, 0), 32 bits)
  }

  when(io.p0WrReq && currentIoOperation =/= IoOperation.write) {
    p0WrQueue := True
    p0ByteEnQueue := io.p0ByteEn
    p0AddrQueue := io.p0Addr
    p0DataQueue := io.p0Data
  } elsewhen(io.p0RdReq && currentIoOperation =/= IoOperation.read) {
    p0RdQueue := True
    p0AddrQueue := io.p0Addr
  }

  command := Command.nop

  when(state =/= State.init) {
    refreshCounter := refreshCounter + 1
  }

  switch(state) {
    is(State.init) {
      delayCounter := delayCounter + 1

      when(delayCounter === U(cyclesUntilStartInhibit, 32 bits)) {
        clockEnable := True
      } elsewhen(delayCounter === U(cyclesUntilClearInhibit, 32 bits)) {
        command := Command.precharge
        address(10) := True
      } elsewhen(
        delayCounter === U(cyclesUntilInitPrechargeEnd, 32 bits) ||
          delayCounter === U(cyclesUntilRefresh1End, 32 bits)
      ) {
        clockEnable := True
        command := Command.autoRefresh
      } elsewhen(delayCounter === U(cyclesUntilRefresh2End, 32 bits)) {
        command := Command.loadModeReg
        bank := 0
        address := B(configuredModeValue, 13 bits)
      } elsewhen(delayCounter === U(cyclesUntilRefresh2End + settingTmrdCycles, 32 bits)) {
        state := State.idle
      }
    }

    is(State.idle) {
      dqOutput := False
      p0ReadyReg := False
      currentIoOperation := IoOperation.none

      when(refreshCounter >= U(cyclesPerRefresh, 16 bits)) {
        state := State.delay
        delayState := State.idle
        delayCounter := U(scala.math.max(cyclesForAutorefresh - 2, 0), 32 bits)
        refreshCounter := 0
        command := Command.autoRefresh
      } elsewhen(io.p0WrReq || p0WrQueue) {
        state := State.delay
        delayState := State.write
        currentIoOperation := IoOperation.write
        p0WrQueue := False
        setActiveCommand(0, p0AddrCurrent)
      } elsewhen(io.p0RdReq || p0RdQueue) {
        state := State.delay
        delayState := State.read
        currentIoOperation := IoOperation.read
        setActiveCommand(0, p0AddrCurrent)
      }
    }

    is(State.delay) {
      when(delayCounter > 0) {
        delayCounter := delayCounter - 1
      } otherwise {
        state := delayState
        delayState := State.idle

        when(delayState === State.idle && currentIoOperation =/= IoOperation.none) {
          switch(activePort) {
            is(0) {
              p0ReadyReg := True
            }
          }
        }
      }
    }

    is(State.write) {
      state := State.delay
      delayCounter := U(scala.math.max(cyclesAfterWriteForNextCommand - 2, 0), 32 bits)

      command := Command.write
      address := B"2'b00" ## True ## activePortAddr
      dqOutput := True
      dqData := activePortData
      dataMask := ~activePortByteEn
    }

    is(State.read) {
      if (config.casLatency == 1 && !settingUseFastInputRegister) {
        state := State.readOutput
      } else {
        state := State.delay
        delayState := State.readOutput
        readCounter := 0
        delayCounter := U(
          scala.math.max(config.casLatency - 2 + (if (settingUseFastInputRegister) 1 else 0), 0),
          32 bits
        )
      }

      p0RdQueue := False
      command := Command.read
      address := B"2'b00" ## True ## activePortAddr
      dataMask := 0
    }

    is(State.readOutput) {
      val expectedCount = U(config.p0BurstLength, 4 bits)

      when(readCounter < expectedCount) {
        readCounter := readCounter + 1
      } otherwise {
        state := State.idle
      }

      switch(readCounter) {
        is(0) { readBuffer(15 downto 0) := io.sdRam.dqIn }
        is(1) { readBuffer(31 downto 16) := io.sdRam.dqIn }
        is(2) { readBuffer(47 downto 32) := io.sdRam.dqIn }
        is(3) { readBuffer(63 downto 48) := io.sdRam.dqIn }
        is(4) { readBuffer(79 downto 64) := io.sdRam.dqIn }
        is(5) { readBuffer(95 downto 80) := io.sdRam.dqIn }
        is(6) { readBuffer(111 downto 96) := io.sdRam.dqIn }
        is(7) { readBuffer(127 downto 112) := io.sdRam.dqIn }
      }

      p0QReg := readBuffer(config.p0BurstLength * 16 - 1 downto 0)
      when(readCounter === expectedCount) {
        p0ReadyReg := True
      }
    }
  }
}

object SdRamCtrlVerilog extends App {
  rt68ice.Config.spinal.generateVerilog(SdRamCtrl())
}
