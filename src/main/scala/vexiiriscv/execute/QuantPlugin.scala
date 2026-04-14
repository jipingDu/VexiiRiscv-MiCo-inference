package vexiiriscv.execute

import spinal.core._
import spinal.lib.misc.pipeline._
import vexiiriscv.riscv.{IntRegFile, RS1, Riscv}

object QuantPlugin extends AreaObject {
    val QUANT_PUSH_I8 = IntRegFile.TypeI(M"----------------000-----0001011")
    val QUANT_FLUSH   = IntRegFile.TypeR(M"0000010----------001-----0001011")
}

class QuantPlugin(val layer: LaneLayer,
                  var formatAt: Int = 0) extends ExecutionUnitElementSimple(layer) {
    import QuantPlugin._

    val logic = during setup new Logic {
        awaitBuild()
        import SrcKeys._

        val xlen     = Riscv.XLEN.get
        val IS_FLUSH = Payload(Bool())
        val IS_PUSH  = Payload(Bool())
        val RES      = Payload(Bits(xlen bits))

        val wb = newWriteback(ifp, formatAt)

        add(QUANT_PUSH_I8).srcs(SRC1.RF).decode(IS_PUSH -> True,  IS_FLUSH -> False)
        add(QUANT_FLUSH  ).srcs(SRC1.RF, SRC2.RF).decode(IS_PUSH -> False, IS_FLUSH -> True)

        uopRetainer.release()

        val execute = new el.Execute(formatAt) {
            val rs1 = el(IntRegFile, RS1).asBits

            val quantBuf   = Reg(Bits(32 bits)) init(0)
            val quantCount = Reg(UInt(2 bits))  init(0)

            val doUpdate = isValid && SEL && !isCancel

            when(doUpdate && IS_PUSH) {
                switch(quantCount) {
                    is(U"2'b00") { quantBuf( 0, 8 bits) := rs1(7 downto 0) }
                    is(U"2'b01") { quantBuf( 8, 8 bits) := rs1(7 downto 0) }
                    is(U"2'b10") { quantBuf(16, 8 bits) := rs1(7 downto 0) }
                    is(U"2'b11") { quantBuf(24, 8 bits) := rs1(7 downto 0) }
                }
                quantCount := quantCount + 1
            }

            when(doUpdate && IS_FLUSH) {
                quantCount := 0
            }

            RES := quantBuf

            wb.valid   := SEL && IS_FLUSH
            wb.payload := RES
        }
    }
}
