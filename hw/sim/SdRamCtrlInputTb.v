`timescale 1ns/1ps

// Simulation-only model of the forwarded inverted clock.
module ODDRX1F(input D0, D1, SCLK, RST, output Q);
  assign Q = RST ? 1'b0 : (SCLK ? D0 : D1);
endmodule

module SdRamCtrlInputTb;
  parameter BURST_LENGTH = 4;
  parameter CAS_LATENCY = 2;
  reg clk = 0;
  always #10 clk = ~clk; // 50 MHz controller, SDRAM rising edge on its falling edge.
  reg reset = 1;
  reg [23:0] address = 0;
  reg [15:0] data = 0;
  reg [1:0] byteEnable = 2'b11;
  reg rd = 0, wr = 0;
  wire [BURST_LENGTH*16-1:0] q;
  wire initComplete, available, ready, memClock, cke;
  wire [15:0] dq;
  wire [12:0] a;
  wire [1:0] dm, ba;
  wire cs_n, ras_n, cas_n, we_n;
  reg [15:0] memory [0:511];
  reg [15:0] readDrive = 16'hzzzz;
  reg pendingRead = 0;
  integer readWait = 0, readWord = 0, readBase = 0;
  integer readCommands = 0, writeCommands = 0;
  integer i, round, base;

  assign dq = readDrive;
  SdRamCtrl dut (
    .io_initComplete(initComplete), .io_p0Addr(address), .io_p0Data(data),
    .io_p0ByteEn(byteEnable), .io_p0Q(q), .io_p0WrReq(wr), .io_p0RdReq(rd),
    .io_p0Available(available), .io_p0Ready(ready), .io_sdRam_dq(dq),
    .io_sdRam_a(a), .io_sdRam_dm(dm), .io_sdRam_ba(ba),
    .io_sdRam_cs_n(cs_n), .io_sdRam_ras_n(ras_n),
    .io_sdRam_cas_n(cas_n), .io_sdRam_we_n(we_n),
    .io_sdRam_cke(cke), .io_sdRam_clock(memClock), .clk(clk), .reset(reset)
  );

  // Minimal single-row SDRAM pin model. CAS2 data follows the next rising
  // SDRAM edge after READ; CAS3 waits one further edge. tAC=6.5 ns exercises
  // the capture edge; it does not model board routing or establish timing closure.
  always @(posedge memClock) begin
    if (reset) begin
      pendingRead = 0;
      readDrive <= 16'hzzzz;
    end else if (cke) begin
      if (pendingRead) begin
        if (readWait != 0) readWait = readWait - 1;
        else if (readWord < BURST_LENGTH) begin
          readDrive <= #6.5 memory[readBase + readWord];
          readWord = readWord + 1;
        end else begin
          readDrive <= #6.5 16'hzzzz;
          pendingRead = 0;
        end
      end
      if ({cs_n, ras_n, cas_n, we_n} == 4'b0101) begin
        if (pendingRead) $fatal(1, "Overlapping read bursts");
        if (dm != 0) $fatal(1, "Read burst must enable both bytes");
        readCommands = readCommands + 1;
        readBase = a[8:0];
        readWord = 0;
        readWait = CAS_LATENCY - 2;
        pendingRead = 1;
      end
      if ({cs_n, ras_n, cas_n, we_n} == 4'b0100) begin
        if (pendingRead) $fatal(1, "Write overlaps a read burst");
        if ($isunknown(dq)) $fatal(1, "Write data is not driven at SDRAM clock edge");
        writeCommands = writeCommands + 1;
        if (!dm[1]) memory[a[8:0]][15:8] = dq[15:8];
        if (!dm[0]) memory[a[8:0]][7:0] = dq[7:0];
      end
    end
  end

  task waitIdle;
    begin
      wait (available && !ready);
      @(negedge clk);
    end
  endtask

  task readBurst(input integer wordAddress);
    integer beforeCount, word;
    reg [BURST_LENGTH*16-1:0] wanted;
    begin
      waitIdle();
      for (word=0; word<BURST_LENGTH; word=word+1)
        wanted[word*16 +: 16] = memory[wordAddress + word];
      beforeCount = readCommands;
      address = wordAddress;
      byteEnable = 2'b11;
      rd = 1;
      @(negedge clk); rd = 0;
      wait (ready); #1;
      if (q !== wanted)
        $fatal(1, "Burst alignment/data failure BL=%0d CL=%0d address=%h got=%h expected=%h",
          BURST_LENGTH, CAS_LATENCY, wordAddress, q, wanted);
      if (readCommands != beforeCount + 1) $fatal(1, "Read request was lost or duplicated");
      @(negedge clk);
    end
  endtask

  task writeWord(input integer wordAddress, input [15:0] value, input [1:0] lanes);
    integer beforeCount;
    reg [15:0] wanted;
    begin
      waitIdle();
      wanted = memory[wordAddress];
      if (lanes[1]) wanted[15:8] = value[15:8];
      if (lanes[0]) wanted[7:0] = value[7:0];
      beforeCount = writeCommands;
      address = wordAddress; data = value; byteEnable = lanes;
      wr = 1;
      @(negedge clk); wr = 0;
      wait (ready); #1;
      if (memory[wordAddress] !== wanted) $fatal(1, "Write data/byte lanes changed");
      if (writeCommands != beforeCount + 1) $fatal(1, "Write request was lost or duplicated");
      @(negedge clk);
    end
  endtask

  initial begin
    for (i=0; i<512; i=i+1) memory[i] = 16'h1357 ^ (i * 16'h0101);
    #55; reset = 0;
    wait(initComplete);
    for (round=0; round<32; round=round+1) begin
      base = (round * BURST_LENGTH) % 256;
      // Every burst word differs, exposing dropped, shifted, and repeated samples.
      readBurst(base);
      writeWord(base, 16'h0404 ^ round, 2'b11);
      readBurst(base);
      writeWord(base, 16'hA5E9, 2'b10);
      readBurst(base);
      writeWord(base, 16'h5A3C, 2'b01);
      readBurst(base);
    end
    $display("PASS SDRAM input stage: BL=%0d CL=%0d, %0d read bursts, %0d writes",
      BURST_LENGTH, CAS_LATENCY, readCommands, writeCommands);
    $finish;
  end
  initial begin #2000000; $fatal(1, "SDRAM input-stage simulation timed out"); end
endmodule
