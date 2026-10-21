package chipyard.fpga.vcu118

import chisel3._
import chisel3.util.Cat
import chisel3.experimental.{BaseModule}

import org.chipsalliance.diplomacy.nodes.{HeterogeneousBag}
import freechips.rocketchip.tilelink.{TLBundle}

import sifive.blocks.devices.uart.{UARTPortIO}
import sifive.blocks.devices.spi.{HasPeripherySPI, SPIPortIO}

import chipyard._
import chipyard.harness._
import chipyard.iobinders._

/*** UART ***/
class WithUART extends HarnessBinder({
  case (th: VCU118FPGATestHarnessImp, port: UARTPort, chipId: Int) => {
    th.vcu118Outer.io_uart_bb.bundle <> port.io
  }
})

/*** SPI ***/
class WithSPISDCard extends HarnessBinder({
  case (th: VCU118FPGATestHarnessImp, port: SPIPort, chipId: Int) => {
    th.vcu118Outer.io_spi_bb.bundle <> port.io
  }
})

/*** Experimental DDR ***/
class WithDDRMem extends HarnessBinder({
  case (th: VCU118FPGATestHarnessImp, port: TLMemPort, chipId: Int) => {
    // val bundles = th.vcu118Outer.ddrClient.out.map(_._1)
    val bundles = Seq(th.vcu118Outer.ddrClient, th.vcu118Outer.ddrClient2).flatMap(_.out.map(_._1))
    val ddrClientBundle = Wire(new HeterogeneousBag(bundles.map(_.cloneType)))
    bundles.zip(ddrClientBundle).foreach { case (bundle, io) => bundle <> io }
    ddrClientBundle <> port.io

    // The 2 channels interleave on address bit 6 (System.scala:68), and each bank is 2 GB.
    // Remove bit 6 so each channel's addresses fill its bank 0..2GB with no gaps and no wrap-around.
    if (bundles.size > 1) {
      val base = th.vcu118Outer.dp(ExtTLMem).get.master.base
      ddrClientBundle.zip(port.io).foreach { case (ddr, chip) =>
        val off = chip.a.bits.address - base.U
        ddr.a.bits.address := base.U + Cat(off(31, 7), off(5, 0))
      }
    }
  }
})

class WithJTAG extends HarnessBinder({
  case (th: VCU118FPGATestHarnessImp, port: JTAGPort, chipId: Int) => {
    val jtag_io = th.vcu118Outer.jtagPlacedOverlay.overlayOutput.jtag.getWrappedValue
    port.io.TCK := jtag_io.TCK
    port.io.TMS := jtag_io.TMS
    port.io.TDI := jtag_io.TDI
    port.io.reset.foreach(_ := th.referenceReset)
    jtag_io.TDO.data := port.io.TDO
    jtag_io.TDO.driven := true.B
    // ignore srst_n
    jtag_io.srst_n := DontCare

  }
})
