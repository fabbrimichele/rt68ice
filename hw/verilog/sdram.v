module sdram (
	clk,
	reset,
	init_complete,
	p0_addr,
	p0_data,
	p0_byte_en,
	p0_q,
	p0_wr_req,
	p0_rd_req,
	p0_available,
	p0_ready,
	SDRAM_DQ,
	SDRAM_A,
	SDRAM_DQM,
	SDRAM_BA,
	SDRAM_nCS,
	SDRAM_nWE,
	SDRAM_nRAS,
	SDRAM_nCAS,
	SDRAM_CKE,
	SDRAM_CLK
);
	reg _sv2v_0;
	parameter CLOCK_SPEED_MHZ = 28.4091;
	parameter BURST_LENGTH = 1;
	parameter BURST_TYPE = 0;
	parameter CAS_LATENCY = 2;
	parameter WRITE_BURST = 0;
	parameter P0_BURST_LENGTH = BURST_LENGTH;
	input wire clk;
	input wire reset;
	output wire init_complete;
	input wire [23:0] p0_addr;
	input wire [15:0] p0_data;
	input wire [1:0] p0_byte_en;
	output reg [(P0_BURST_LENGTH * 16) - 1:0] p0_q;
	input wire p0_wr_req;
	input wire p0_rd_req;
	output wire p0_available;
	output reg p0_ready = 0;
	inout wire [15:0] SDRAM_DQ;
	output reg [12:0] SDRAM_A;
	output reg [1:0] SDRAM_DQM;
	output reg [1:0] SDRAM_BA;
	output wire SDRAM_nCS;
	output wire SDRAM_nWE;
	output wire SDRAM_nRAS;
	output wire SDRAM_nCAS;
	output reg SDRAM_CKE;
	output wire SDRAM_CLK;

    // -7 Speed Grade (Most Common)
	localparam SETTING_INHIBIT_DELAY_MICRO_SEC = 100;
    localparam SETTING_T_CK_MIN_CLOCK_CYCLE_TIME_NANO_SEC = 7;
    localparam SETTING_T_RAS_MIN_ROW_ACTIVE_TIME_NANO_SEC = 42;
    localparam SETTING_T_RC_MIN_ROW_CYCLE_TIME_NANO_SEC = 63;
    localparam SETTING_T_RP_MIN_PRECHARGE_CMD_PERIOD_NANO_SEC = 21;
    localparam SETTING_T_RFC_MIN_AUTOREFRESH_PERIOD_NANO_SEC = 63;
    localparam SETTING_T_RC_MIN_ACTIVE_TO_ACTIVE_PERIOD_NANO_SEC = 63;
    localparam SETTING_T_RCD_MIN_READ_WRITE_DELAY_NANO_SEC = 21;
    localparam SETTING_T_WR_MIN_WRITE_AUTO_PRECHARGE_RECOVERY_NANO_SEC = 14;
    localparam SETTING_T_MRD_MIN_LOAD_MODE_CLOCK_CYCLES = 2;
    //localparam SETTING_REFRESH_TIMER_NANO_SEC = 7812; // 64ms / 8192 rows
    localparam SETTING_REFRESH_TIMER_NANO_SEC = 7500; // 64ms / 8192 rows
    localparam SETTING_USE_FAST_INPUT_REGISTER = 1;

	localparam CLOCK_PERIOD_NANO_SEC = 1000.0 / CLOCK_SPEED_MHZ;
	function integer rtoi;
		input integer x;
		rtoi = x;
	endfunction

	/* verilator lint_off REALCVT */
	localparam CYCLES_UNTIL_START_INHIBIT = (rtoi(50000 / CLOCK_PERIOD_NANO_SEC) > (50000 / CLOCK_PERIOD_NANO_SEC) ? rtoi(50000 / CLOCK_PERIOD_NANO_SEC) : rtoi(50000 / CLOCK_PERIOD_NANO_SEC) + 1);
	localparam CYCLES_UNTIL_CLEAR_INHIBIT = 100 + (rtoi(100000 / CLOCK_PERIOD_NANO_SEC) > (100000 / CLOCK_PERIOD_NANO_SEC) ? rtoi(100000 / CLOCK_PERIOD_NANO_SEC) : rtoi(100000 / CLOCK_PERIOD_NANO_SEC) + 1);
	localparam CYCLES_FOR_AUTOREFRESH = (rtoi(SETTING_T_RFC_MIN_AUTOREFRESH_PERIOD_NANO_SEC / CLOCK_PERIOD_NANO_SEC) > (SETTING_T_RFC_MIN_AUTOREFRESH_PERIOD_NANO_SEC / CLOCK_PERIOD_NANO_SEC) ? rtoi(SETTING_T_RFC_MIN_AUTOREFRESH_PERIOD_NANO_SEC / CLOCK_PERIOD_NANO_SEC) : rtoi(SETTING_T_RFC_MIN_AUTOREFRESH_PERIOD_NANO_SEC / CLOCK_PERIOD_NANO_SEC) + 1);
	localparam CYCLES_BETWEEN_ACTIVE_COMMAND = (rtoi(SETTING_T_RC_MIN_ACTIVE_TO_ACTIVE_PERIOD_NANO_SEC / CLOCK_PERIOD_NANO_SEC) > (SETTING_T_RC_MIN_ACTIVE_TO_ACTIVE_PERIOD_NANO_SEC / CLOCK_PERIOD_NANO_SEC) ? rtoi(SETTING_T_RC_MIN_ACTIVE_TO_ACTIVE_PERIOD_NANO_SEC / CLOCK_PERIOD_NANO_SEC) : rtoi(SETTING_T_RC_MIN_ACTIVE_TO_ACTIVE_PERIOD_NANO_SEC / CLOCK_PERIOD_NANO_SEC) + 1);
	localparam CYCLES_FOR_ACTIVE_ROW = (rtoi(SETTING_T_RCD_MIN_READ_WRITE_DELAY_NANO_SEC / CLOCK_PERIOD_NANO_SEC) > (SETTING_T_RCD_MIN_READ_WRITE_DELAY_NANO_SEC / CLOCK_PERIOD_NANO_SEC) ? rtoi(SETTING_T_RCD_MIN_READ_WRITE_DELAY_NANO_SEC / CLOCK_PERIOD_NANO_SEC) : rtoi(SETTING_T_RCD_MIN_READ_WRITE_DELAY_NANO_SEC / CLOCK_PERIOD_NANO_SEC) + 1);
	localparam CYCLES_AFTER_WRITE_FOR_NEXT_COMMAND = (rtoi(33 / CLOCK_PERIOD_NANO_SEC) > (33 / CLOCK_PERIOD_NANO_SEC) ? rtoi(33 / CLOCK_PERIOD_NANO_SEC) : rtoi(33 / CLOCK_PERIOD_NANO_SEC) + 1);
	localparam CYCLES_PER_REFRESH = (rtoi(SETTING_REFRESH_TIMER_NANO_SEC / CLOCK_PERIOD_NANO_SEC) > (SETTING_REFRESH_TIMER_NANO_SEC / CLOCK_PERIOD_NANO_SEC) ? rtoi(SETTING_REFRESH_TIMER_NANO_SEC / CLOCK_PERIOD_NANO_SEC) : rtoi(SETTING_REFRESH_TIMER_NANO_SEC / CLOCK_PERIOD_NANO_SEC) + 1);
	localparam CYCLES_UNTIL_INIT_PRECHARGE_END = (10 + CYCLES_UNTIL_CLEAR_INHIBIT) + (rtoi(SETTING_T_RP_MIN_PRECHARGE_CMD_PERIOD_NANO_SEC / CLOCK_PERIOD_NANO_SEC) > (SETTING_T_RP_MIN_PRECHARGE_CMD_PERIOD_NANO_SEC / CLOCK_PERIOD_NANO_SEC) ? rtoi(SETTING_T_RP_MIN_PRECHARGE_CMD_PERIOD_NANO_SEC / CLOCK_PERIOD_NANO_SEC) : rtoi(SETTING_T_RP_MIN_PRECHARGE_CMD_PERIOD_NANO_SEC / CLOCK_PERIOD_NANO_SEC) + 1);
	localparam CYCLES_UNTIL_REFRESH1_END = CYCLES_UNTIL_INIT_PRECHARGE_END + CYCLES_FOR_AUTOREFRESH;
	localparam CYCLES_UNTIL_REFRESH2_END = CYCLES_UNTIL_REFRESH1_END + CYCLES_FOR_AUTOREFRESH;

	/* State machine labels */
	localparam STATE_INIT      = 3'd0;
    localparam STATE_IDLE      = 3'd1;
    localparam STATE_DELAY     = 3'd2;
    localparam STATE_WRITE     = 3'd3;
    localparam STATE_READ      = 3'd4;
    localparam STATE_READ_DATA = 3'd5;

	/* verilator lint_on REALCVT */
	wire [2:0] concrete_burst_length = (BURST_LENGTH == 1 ? 3'h0 : (BURST_LENGTH == 2 ? 3'h1 : (BURST_LENGTH == 4 ? 3'h2 : 3'h3)));
	wire [12:0] configured_mode = {3'b000, ~WRITE_BURST[0], 2'b00, CAS_LATENCY[2:0], BURST_TYPE[0], concrete_burst_length};
	localparam P0_OUTPUT_WIDTH = (P0_BURST_LENGTH * 16) - 1;
	reg [2:0] state;
	reg [31:0] delay_counter = 0;
	reg [3:0] read_counter = 0;
	reg [15:0] refresh_counter = 0;
	reg [1:0] active_port = 0;
	reg [2:0] delay_state;
	reg [1:0] current_io_operation;
	reg [3:0] sdram_command;
	assign {SDRAM_nCS, SDRAM_nRAS, SDRAM_nCAS, SDRAM_nWE} = sdram_command;
	reg p0_wr_queue = 0;
	reg p0_rd_queue = 0;
	reg [1:0] p0_byte_en_queue = 0;
	reg [23:0] p0_addr_queue = 0;
	reg [15:0] p0_data_queue = 0;
	wire p0_req = p0_wr_req || p0_rd_req;
	wire p0_req_queue = p0_wr_queue || p0_rd_queue;
	wire [23:0] p0_addr_current = (p0_req_queue ? p0_addr_queue : p0_addr);
	wire port_req = p0_req || p0_req_queue;
	task set_active_command;
		input reg [1:0] port;
		input reg [23:0] addr;
		begin
			sdram_command <= 4'b0011;
			SDRAM_BA <= addr[23:23];
			SDRAM_A <= addr[21:9];
			active_port <= port;
			delay_counter <= (CYCLES_FOR_ACTIVE_ROW > 32'h00000002 ? CYCLES_FOR_ACTIVE_ROW - 32'h00000002 : 32'h00000000);
		end
	endtask
	function [27:0] get_active_port;
		input reg _sv2v_unused;
		reg [27:0] selection;
		begin
			selection[27-:10] = 10'h000;
			selection[17-:16] = 16'h0000;
			selection[1-:2] = 2'h0;
			case (active_port)
				0: begin
					selection[27-:10] = p0_addr_queue[8:0];
					selection[17-:16] = p0_data_queue;
					selection[1-:2] = p0_byte_en_queue;
				end
			endcase
			get_active_port = selection;
		end
	endfunction
	reg dq_output = 0;
	reg [15:0] sdram_data = 0;
    reg [127:0] temp;
	assign SDRAM_DQ = (dq_output ? sdram_data : 16'hzzzz);
	assign init_complete = state != 3'd0;
	assign p0_available = (state == 3'd1) && ~port_req;
	always @(posedge clk)
		if (reset) begin
			SDRAM_CKE <= 0;
			delay_counter <= 0;
			delay_state <= 3'd1;
			current_io_operation <= 2'd0;
			sdram_command <= 4'b0111;
			p0_ready <= 0;
			p0_wr_queue <= 0;
			p0_rd_queue <= 0;
			dq_output <= 0;
			p0_q <= 0;
		end
		else begin
			if (p0_wr_req && (current_io_operation != 2'd1)) begin
				p0_wr_queue <= 1;
				p0_byte_en_queue <= p0_byte_en;
				p0_addr_queue <= p0_addr;
				p0_data_queue <= p0_data;
			end
			else if (p0_rd_req && (current_io_operation != 2'd2)) begin
				p0_rd_queue <= 1;
				p0_addr_queue <= p0_addr;
			end
			sdram_command <= 4'b0111;
			if (state != 3'd0)
				refresh_counter <= refresh_counter + 16'h0001;
			case (state)
				STATE_INIT: begin
					delay_counter <= delay_counter + 32'h00000001;
					if (delay_counter == CYCLES_UNTIL_START_INHIBIT)
						SDRAM_CKE <= 1;
					else if (delay_counter == CYCLES_UNTIL_CLEAR_INHIBIT) begin
						sdram_command <= 4'b0010;
						SDRAM_A[10] <= 1;
					end
					else if ((delay_counter == CYCLES_UNTIL_INIT_PRECHARGE_END) || (delay_counter == CYCLES_UNTIL_REFRESH1_END)) begin
						SDRAM_CKE <= 1;
						sdram_command <= 4'b0001;
					end
					else if (delay_counter == CYCLES_UNTIL_REFRESH2_END) begin
						sdram_command <= 4'b0000;
						SDRAM_BA <= 2'b00;
						SDRAM_A <= configured_mode;
					end
					else if (delay_counter == (CYCLES_UNTIL_REFRESH2_END + SETTING_T_MRD_MIN_LOAD_MODE_CLOCK_CYCLES))
						state <= 3'd1;
				end
				STATE_IDLE: begin
					dq_output <= 0;
					p0_ready <= 0;
					current_io_operation <= 2'd0;
					if (refresh_counter >= CYCLES_PER_REFRESH[15:0]) begin
						state <= 3'd2;
						delay_state <= 3'd1;
						delay_counter <= CYCLES_FOR_AUTOREFRESH - 32'h00000002;
						refresh_counter <= 0;
						sdram_command <= 4'b0001;
					end
					else if (p0_wr_req || p0_wr_queue) begin
						state <= 3'd2;
						delay_state <= 3'd3;
						current_io_operation <= 2'd1;
						p0_wr_queue <= 0;
						set_active_command(0, p0_addr_current);
					end
					else if (p0_rd_req || p0_rd_queue) begin
						state <= 3'd2;
						delay_state <= 3'd4;
						current_io_operation <= 2'd2;
						set_active_command(0, p0_addr_current);
					end
				end
				STATE_DELAY:
					if (delay_counter > 0)
						delay_counter <= delay_counter - 32'h00000001;
					else begin
						state <= delay_state;
						delay_state <= 3'd1;
						if ((delay_state == 3'd1) && (current_io_operation != 2'd0))
							case (active_port)
								0: p0_ready <= 1;
							endcase
					end
				STATE_WRITE: begin : sv2v_autoblock_1
					reg [27:0] active_port_entries;
					state <= 3'd2;

					// Fix: Guard against underflow
                    delay_counter <= (CYCLES_AFTER_WRITE_FOR_NEXT_COMMAND > 32'h00000002) ?
                                      CYCLES_AFTER_WRITE_FOR_NEXT_COMMAND - 32'h00000002 : 32'h00000000;

					// delay_counter <= CYCLES_AFTER_WRITE_FOR_NEXT_COMMAND - 32'h00000002;
					active_port_entries = get_active_port(0);
					sdram_command <= 4'b0100;
					SDRAM_A <= {3'b001, active_port_entries[27-:10]};
					dq_output <= 1;
					sdram_data <= active_port_entries[17-:16];
					SDRAM_DQM <= ~active_port_entries[1-:2];
				end
				STATE_READ: begin : sv2v_autoblock_2
					reg [27:0] active_port_entries;
					if ((CAS_LATENCY == 1) && ~SETTING_USE_FAST_INPUT_REGISTER)
						state <= 3'd5;
					else begin
						state <= 3'd2;
						delay_state <= 3'd5;
						read_counter <= 0;
						delay_counter <= (CAS_LATENCY - 32'h00000002) + SETTING_USE_FAST_INPUT_REGISTER;
					end
					active_port_entries = get_active_port(0);
					p0_rd_queue <= 0;
					sdram_command <= 4'b0101;
					SDRAM_A <= {3'b001, active_port_entries[27-:10]};
					SDRAM_DQM <= 2'b00;
				end
				STATE_READ_DATA: begin : sv2v_autoblock_3
					//reg [127:0] temp;
					reg [3:0] expected_count;
					case (active_port)
						0: begin
							temp[P0_OUTPUT_WIDTH:0] = p0_q;
							expected_count = P0_BURST_LENGTH;
						end
					endcase
					if (read_counter < expected_count)
						read_counter <= read_counter + 4'h1;
					else
						state <= 3'd1;
					case (read_counter)
						0: temp[15:0] = SDRAM_DQ;
						1: temp[31:16] = SDRAM_DQ;
						2: temp[47:32] = SDRAM_DQ;
						3: temp[63:48] = SDRAM_DQ;
						4: temp[79:64] = SDRAM_DQ;
						5: temp[95:80] = SDRAM_DQ;
						6: temp[111:96] = SDRAM_DQ;
						7: temp[127:112] = SDRAM_DQ;
					endcase
					case (active_port)
						0: begin
							p0_q <= temp[P0_OUTPUT_WIDTH:0];
							if (read_counter == expected_count)
								p0_ready <= 1;
						end
					endcase
				end
				default: state <= 3'd0;
			endcase
		end
	initial _sv2v_0 = 0;

`ifndef VERILATOR
    ODDRX1F sdramclk_ddr (
		.D0(1'b0),        // Value on rising edge
		.D1(1'b1),        // Value on falling edge
		.SCLK(clk),       // System clock input
		.RST(1'b0),       // Reset (disabled)
		.Q(SDRAM_CLK)     // Output pin driving the external SDRAM clock
	);
`else
    // A simple behavioral equivalent for Verilator simulation
    // If D0=0 and D1=1, the output is inverted. If D0=1 and D1=0, it matches the input.
    assign SDRAM_CLK = ~clk;
`endif

endmodule
