`timescale 1ns/1ps

// Exercise the generated SpinalHDL M68K wrapper and actual translated VHDL core.
module M68KUpperAddressTb;
  reg clk = 0;
  always #20 clk = !clk; // 25 MHz; all inputs settle before the sampling edge.
  reg reset = 1;
  reg clock_enable = 1;
  wire [31:0] address;
  wire [15:0] data_out;
  reg [15:0] data_in;
  wire wr, uds, lds;
  wire [1:0] bus_state;

  M68K dut (
    .clk(clk), .reset(reset),
    .io_bus_address(address), .io_bus_dataOut(data_out), .io_bus_dataIn(data_in),
    .io_bus_wr(wr), .io_bus_uds(uds), .io_bus_lds(lds),
    .io_ipl(3'b111), .io_busErr(1'b0), .io_clockEn(clock_enable),
    .io_busState(bus_state)
  );

  reg [15:0] rom [0:2047];
  reg [15:0] low_data [0:7];
  reg [15:0] high_data [0:7];
  reg [15:0] top_data [0:7];
  reg [15:0] boundary_low = 0;
  reg [15:0] boundary_high = 0;
  integer wait_cycles = 0;
  integer phase = 0;
  integer i;
  reg truncate_address;
  reg saw_high_fetch = 0;
  reg saw_high_read = 0;
  reg saw_high_write = 0;
  reg saw_top_write = 0;
  reg [1:0] high_byte_writes = 0;
  reg [1:0] crossing_writes = 0;
  wire active = (uds || lds) && bus_state != 2'b01;
  // A negative-control run deliberately aliases memory at 16 MiB. The program
  // must reject that model, rather than passing just because a high bus bit rose.
  wire [31:0] memory_address = truncate_address ? (address & 32'h00ffffff) : address;
  wire [31:0] word_address = memory_address & 32'hfffffffe;

  function [15:0] read_word;
    input [31:0] a;
    begin
      if (a < 32'h00001000)
        read_word = rom[a >> 1];
      else if (a >= 32'h01000100 && a < 32'h01001000)
        read_word = rom[(a - 32'h01000000) >> 1];
      else if (a >= 32'h00010000 && a < 32'h00010010)
        read_word = low_data[(a - 32'h00010000) >> 1];
      else if (a >= 32'h01010000 && a < 32'h01010010)
        read_word = high_data[(a - 32'h01010000) >> 1];
      else if (a >= 32'h01fffff0 && a < 32'h02000000)
        read_word = top_data[(a - 32'h01fffff0) >> 1];
      else if (a == 32'h00fffffe)
        read_word = boundary_low;
      else if (a == 32'h01000000)
        read_word = boundary_high;
      else
        read_word = 16'hffff;
    end
  endfunction

  always @* data_in = read_word(word_address);

  // Pause the actual CPU, including between halves of longword accesses.
  always @(negedge clk) begin
    if (!reset) begin
      phase = phase + 1;
      clock_enable = wait_cycles == 0 || (phase % (wait_cycles + 1)) == 0;
    end
  end

  task store_word;
    input [31:0] a;
    input [15:0] value;
    begin
      if (a >= 32'h00010000 && a < 32'h00010010)
        low_data[(a - 32'h00010000) >> 1] = value;
      else if (a >= 32'h01010000 && a < 32'h01010010)
        high_data[(a - 32'h01010000) >> 1] = value;
      else if (a >= 32'h01fffff0 && a < 32'h02000000)
        top_data[(a - 32'h01fffff0) >> 1] = value;
      else if (a == 32'h00fffffe)
        boundary_low = value;
      else if (a == 32'h01000000)
        boundary_high = value;
      else
        $fatal(1, "Unexpected write to %08x", a);
    end
  endtask

  reg [15:0] merged;
  always @(posedge clk) begin
    if (!reset && clock_enable && active) begin
      if (!wr) begin
        if (bus_state == 2'b00 && address >= 32'h01000100 && address < 32'h01001000)
          saw_high_fetch = 1;
        if (address >= 32'h01010000 && address < 32'h01010010)
          saw_high_read = 1;
      end else if (word_address == 32'h0000f100) begin
        if (data_out == 16'hdead)
          $fatal(1, "CPU test program reported failure (memory aliases or returned wrong data)");
        if (data_out != 16'h600d || !uds || !lds)
          $fatal(1, "Invalid CPU result marker");
        if (!saw_high_fetch || !saw_high_read || !saw_high_write || !saw_top_write)
          $fatal(1, "CPU did not exercise all upper-address paths");
        if (high_byte_writes != 2'b11 || crossing_writes != 2'b11)
          $fatal(1, "Missing byte lanes or boundary-crossing writes");
        if (low_data[0] != 16'ha5c7 || high_data[0] != 16'hb6d8 ||
            low_data[2] != 16'h1122 || low_data[3] != 16'h3344 ||
            high_data[2] != 16'h5566 || high_data[3] != 16'h7788 ||
            top_data[2] != 16'h99aa || top_data[3] != 16'hbbcc ||
            top_data[7] != 16'h5a3c || boundary_low != 16'habcd || boundary_high != 16'h0123)
          $fatal(1, "Independent memory scoreboard mismatch");
        $display("PASS: 68000 mode upper addresses, byte lanes, longwords, 16 MiB crossing, high instruction fetch (wait_cycles=%0d)", wait_cycles);
        $finish;
      end else begin
        merged = read_word(word_address);
        if (uds) merged[15:8] = data_out[15:8];
        if (lds) merged[7:0] = data_out[7:0];
        store_word(word_address, merged);
        if (address >= 32'h01010000 && address < 32'h01010010) begin
          saw_high_write = 1;
          if (uds && !lds) high_byte_writes[1] = 1;
          if (lds && !uds) high_byte_writes[0] = 1;
        end
        if (address >= 32'h01fffff0 && address < 32'h02000000) saw_top_write = 1;
        if (word_address == 32'h00fffffe) crossing_writes[0] = 1;
        if (word_address == 32'h01000000) crossing_writes[1] = 1;
      end
    end
  end

  initial begin
    truncate_address = $test$plusargs("truncate_address");
    if (!$value$plusargs("wait_cycles=%d", wait_cycles)) wait_cycles = 0;
    for (i = 0; i < 8; i = i + 1) begin
      low_data[i] = 0;
      high_data[i] = 0;
      top_data[i] = 0;
    end
    $readmemh("simWorkspace/M68KUpperAddress/program.hex", rom);
    repeat (16) @(negedge clk);
    reset = 0;
    repeat (100000) @(negedge clk);
    $fatal(1, "CPU timeout: address=%08x busstate=%b", address, bus_state);
  end
endmodule
