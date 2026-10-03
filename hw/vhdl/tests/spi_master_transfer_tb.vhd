-- Verify the monitor's 8-bit, clock/16 setting with MOSI/MISO loopback.
library ieee;
use ieee.std_logic_1164.all;

entity spi_master_transfer_tb is end;

architecture test of spi_master_transfer_tb is
    signal clk : std_logic := '0';
    signal reset : std_logic := '1';
    signal cs : std_logic := '0';
    signal rw : std_logic := '1';
    signal addr : std_logic_vector(1 downto 0) := "00";
    signal data_in : std_logic_vector(7 downto 0) := x"00";
    signal data_out, spi_cs_n : std_logic_vector(7 downto 0);
    signal irq, spi_mosi, spi_clk : std_logic;
begin
    clk <= not clk after 20 ns; -- 25 MHz bus clock.
    dut: entity work.spi_master port map (
        clk => clk, reset => reset, cs => cs, rw => rw,
        addr => addr, data_in => data_in, data_out => data_out,
        irq => irq, spi_miso => spi_mosi, spi_mosi => spi_mosi,
        spi_clk => spi_clk, spi_cs_n => spi_cs_n
    );
    process
        procedure write_register(reg_addr : std_logic_vector(1 downto 0);
                                 value : std_logic_vector(7 downto 0)) is
        begin
            wait until rising_edge(clk);
            cs <= '1'; rw <= '0'; addr <= reg_addr; data_in <= value;
            wait until falling_edge(clk);
            wait for 1 ns;
            cs <= '0'; rw <= '1';
        end;
        procedure transfer(value : std_logic_vector(7 downto 0)) is
            variable edge_time : time;
        begin
            write_register("00", value);
            write_register("10", x"13");
            wait until rising_edge(spi_clk) for 2 us;
            assert spi_clk = '1' report "Transfer did not start" severity failure;
            assert data_out(0) = '1' report "BUSY missing" severity failure;
            assert spi_cs_n = x"FD" report "Flash CS not selected" severity failure;
            for bit_index in 1 to 7 loop
                edge_time := now;
                wait until rising_edge(spi_clk) for 1 us;
                assert now - edge_time = 640 ns
                    report "SPI clock is not 1.5625 MHz" severity failure;
            end loop;
            wait until data_out(0) = '0' for 1 us;
            assert data_out(0) = '0' report "Transfer did not finish" severity failure;
            assert spi_clk = '0' report "Clock not idle low" severity failure;
            assert spi_cs_n = x"FD" report "CS released between bytes" severity failure;
            addr <= "00";
            wait for 1 ns;
            assert data_out = value report "Loopback byte mismatch" severity failure;
        end;
    begin
        wait for 2 ns;
        reset <= '0';
        write_register("11", x"0B");
        write_register("10", x"12");
        transfer(x"9F");
        transfer(x"00");
        transfer(x"FF");
        transfer(x"AA");
        transfer(x"55");
        write_register("10", x"10");
        assert spi_cs_n = x"FF" report "Flash deselect failed" severity failure;
        report "SPI 1.5625 MHz timing, BUSY and byte transfer checks passed";
        std.env.stop;
        wait;
    end process;
end;
