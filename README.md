# VexiiRiscv + MiCo

## Project Status

This repository currently includes an LLM inference benchmark flow built around:

- Model: `sw/llama2/llama_3M_W1A8_bench.bin`
- Main software config: `OPT=bnrv SPRAM=1 BITNET_QUANT=2 USE_SIMD=32`
- Main benchmark: `sw/llama2_benchmark.c`

### Best restored baseline

The current clean restored baseline is:

- `INT8 KV`
- `Q -> int8`
- `Q·K` with `int32` accumulation
- delayed dequantization
- RVF-enabled quantization path (`fcvt.w.s`)

Representative result:

- `Prefill Time: 1964742`
- `QMatMul Time: 597154`
- `Quant Time: 111935`
- `Attention Time: 1155780`
- `Softmax Time: 117997`

Log reference:

- `bench_ctx_runs/restore_rvf.989157.log`

### Key findings

- A plain `INT8 KV` path reduced KV memory footprint but was initially slower than the FP32 KV baseline.
- Converting the `Q·K` kernel to `int32 dot + delayed dequant` recovered performance and slightly outperformed the FP32 KV baseline.
- The main remaining attention bottleneck is `A·V` accumulation, not `Q·K`.
- Several later experiments were explored, including on-chip KV, packed writes, group-wise K, int4 V, and VPU bring-up. Most did not exceed the restored `INT8 KV + int32 dot` baseline.
- A major regression source during debugging was that `quant.c` had accidentally been rebuilt without RVF support; restoring `MARCH=rv32imfc` brought `Quant Time` back from roughly `330k` cycles to roughly `112k`.


VexiiRiscv-MiCo is a mixed-precision computing extension plugin for VexiiRiscv.

You can find the MiCo plugin in scala class `vexiiriscv.execute.MiCoPlugin`.

## MiCo Plugin

The MiCo plugin provides 10 custom insturctions, focusing on signed dot product operations between two 32/64-bit vectors. Each of the packed vectors can contain INT8/INT4/INT2/INT1 data.

### Usage

To add the `MiCoPlugin` into `Param.scala`, you need to find the lines about the `lane0`, and add one more line for `MiCoPlugin`:
```scala
val early0 = new LaneLayer("early0", lane0, priority = 0)
plugins += lane0
plugins += new SrcPlugin(early0, executeAt = 0, relaxedRs = relaxedSrc)
plugins += new MiCoPlugin(early0) // Add MiCoPlugin Here!
plugins += new IntAluPlugin(early0, formatAt = 0)
plugins += shifter(early0, formatAt = relaxedShift.toInt)
plugins += new IntFormatPlugin(lane0)
plugins += new BranchPlugin(layer=early0, aluAt=0, jumpAt=relaxedBranchtoInt, wbAt=0)
```

Then you can generate/simulate VexiiRiscv with MiCo Plugin, check the VexiiRiscv guides below.

Due to the custom instructions added, please turn off RVLS when simulating (`--no-rvls-check`).

# VexiiRiscv

VexiiRiscv (Vex2Risc5) is the successor of VexRiscv. Work in progress, here are its currently implemented features :

- RV32/64 I[M][A][F][D][C][S][U][B]
- Up to 5.24 coremark/Mhz 2.50 dhystone/Mhz
- In-order execution
- early [late-alu]
- single/dual issue (can be asymmetric)
- BTB, GShare, RAS branch prediction
- cacheless fetch/load/store
- Optional I$, D$
- Optional SV32/SV39 MMU
- Can run linux / buildroot / Debian
- Pipeline visualisation in simulation via Konata
- Lock step simulation via RVLS and Spike
- AXI4, Wishbone, Tilelink memory busses (RVA is not available in some configs, see the RTD doc SoC main page)
- ... and many other things

Here is a demonstration of a quad core VexiiRiscv running debian on FPGA : https://youtu.be/dR_jqS13D2c?t=112

Overall the goal is to have a design which can stretch (through configuration) from Cortex M0 up to a Cortex A53 and potentialy beyond.

Here is the online documentation : 

- https://spinalhdl.github.io/VexiiRiscv-RTD/master/VexiiRiscv/Introduction/#
- https://spinalhdl.github.io/VexiiRiscv-RTD/master/VexiiRiscv/HowToUse/index.html

Here is the VexiiRiscv's scala doc (auto-generated from the source code) :

- https://spinalhdl.github.io/VexiiRiscv/doc/vexiiriscv/index.html

A roadmap is available here : 

- https://github.com/SpinalHDL/VexiiRiscv/issues/1

# TL;DR Getting started

The quickest way for getting started is to pull the Docker image with all the dependencies installed

Please refer to the self contained tutorial for a comprehensive step by step instruction manual with
screenshots: https://spinalhdl.github.io/VexiiRiscv-RTD/master/VexiiRiscv/Tutorial/index.html

After running the generation you'll find a file named "VexiiRiscv.v" in the root
of the repository folder, which you can drag into your Quartus or whatever.

We decided to not start covering FPGA boards because there's just too many, so it's up to you
to define your pin configuration for your specific FPGA board

If you want to know what else you can do with sbt, please refer to the complete documentation.

# Rebuild the Docker container

In case you wanna rebuild leviathan's Docker container you can run

    docker build . -f docker/Dockerfile -t vexiiriscv --progress=plain
