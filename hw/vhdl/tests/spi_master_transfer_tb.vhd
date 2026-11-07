-- Verify byte/word SPI transfers through the 16-bit register interface.
-- Run with GHDL using --std=08 -fsynopsys alongside ../spi_master.vhd.
library ieee;
use ieee.std_logic_1164.all;

entity spi_master_transfer_tb is end;

architecture test of spi_master_transfer_tb is
    signal clk : std_logic := '0';
    signal reset : std_logic := '1';
    signal cs : std_logic := '0';
    signal rw : std_logic := '1';
    signal addr : std_logic_vector(1 downto 0) := "00";
    signal data_in : std_logic_vector(15 downto 0) := x"0000";
    signal data_out : std_logic_vector(15 downto 0);
    signal spi_cs_n : std_logic_vector(7 downto 0);
    signal irq, spi_mosi, spi_clk : std_logic;
    signal invert_rx : std_logic := '0';
    signal clock_edges : natural := 0;
begin
    clk <= not clk after 20 ns; -- 25 MHz bus clock.
    dut: entity work.spi_master port map (
        clk => clk, reset => reset, cs => cs, rw => rw,
        addr => addr, data_in => data_in, data_out => data_out,
        irq => irq, spi_miso => spi_mosi xor invert_rx,
        spi_mosi => spi_mosi, spi_clk => spi_clk, spi_cs_n => spi_cs_n
    );
    process(spi_clk)
    begin
        if rising_edge(spi_clk) then
            clock_edges <= clock_edges + 1;
        end if;
    end process;
    process
        procedure write_register(reg_addr : std_logic_vector(1 downto 0);
                                 value : std_logic_vector(15 downto 0)) is
        begin
            wait until rising_edge(clk);
            cs <= '1'; rw <= '0'; addr <= reg_addr; data_in <= value;
            wait until falling_edge(clk);
            wait for 1 ns;
            cs <= '0'; rw <= '1';
        end;
        procedure transfer(value : std_logic_vector(15 downto 0);
                           bits : positive := 8;
                           period : time := 640 ns;
                           reload : boolean := true) is
            variable edge_time : time;
            variable initial_edges : natural;
            variable expected : std_logic_vector(15 downto 0);
        begin
            if reload then
                write_register("00", value);
            end if;
            initial_edges := clock_edges;
            write_register("01", x"0013");
            for bit_index in bits - 1 downto 0 loop
                edge_time := now;
                wait until rising_edge(spi_clk) for period * 2;
                assert spi_clk = '1' report "Transfer stalled" severity failure;
                if bit_index /= bits - 1 then
                    assert now - edge_time = period
                        report "Wrong SPI clock period" severity failure;
                end if;
                assert spi_mosi = value(bit_index)
                    report "MOSI data or MSB-first ordering mismatch" severity failure;
                assert data_out(0) = '1' report "BUSY missing" severity failure;
                assert spi_cs_n = x"FD" report "Flash CS not selected" severity failure;
            end loop;
            wait until data_out(0) = '0' for period * 2;
            assert data_out(0) = '0' report "Transfer did not finish" severity failure;
            assert spi_clk = '0' report "Clock not idle low" severity failure;
            assert spi_cs_n = x"FD" report "CS released between transfers" severity failure;
            addr <= "00";
            wait for 1 ns;
            expected := value;
            if invert_rx = '1' then
                expected := not value;
            end if;
            -- Sub-word transfers only guarantee the low 'bits' received bits.
            assert data_out(bits - 1 downto 0) = expected(bits - 1 downto 0)
                report "Received data mismatch" severity failure;
            wait for period;
            assert clock_edges = initial_edges + bits
                report "Wrong number of SPI clock pulses" severity failure;
        end;
    begin
        wait for 2 ns;
        reset <= '0';
        write_register("10", x"000B"); -- 8-bit, clock/16.
        write_register("01", x"0012");
        transfer(x"009F");
        transfer(x"0000");
        transfer(x"00FF");
        transfer(x"00AA");
        transfer(x"0055");
        transfer(x"ABCD"); -- Only low byte CD must be transmitted.

        write_register("10", x"001B"); -- 16-bit, clock/16.
        invert_rx <= '1';
        transfer(x"ABCD", 16);
        -- RX differs from TX; a second START must reuse the original TX word.
        transfer(x"ABCD", 16, reload => false);
        transfer(x"1357", 16);

        write_register("10", x"0018"); -- 16-bit, clock/2 (12.5 MHz).
        transfer(x"96E1", 16, 80 ns);
        transfer(x"0000", 16, 80 ns);
        transfer(x"FFFF", 16, 80 ns);

        write_register("10", x"0008"); -- Return to 8-bit at the same clock.
        transfer(x"ABCD", 8, 80 ns);
        write_register("01", x"0010");
        assert spi_cs_n = x"FF" report "Flash deselect failed" severity failure;
        report "SPI byte/word, ordering, persistent TX, timing, BUSY and clock-count checks passed";
        std.env.stop;
        wait;
    end process;
end;
