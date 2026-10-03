-- Run with GHDL using --std=08 -fsynopsys alongside ../spi_master.vhd.
library ieee;
use ieee.std_logic_1164.all;

entity spi_master_reset_tb is end;

architecture test of spi_master_reset_tb is
    signal clk : std_logic := '0';
    signal reset : std_logic := '1';
    signal cs : std_logic := '0';
    signal rw : std_logic := '1';
    signal addr : std_logic_vector(1 downto 0) := "00";
    signal data_in : std_logic_vector(7 downto 0) := x"00";
    signal data_out, spi_cs_n : std_logic_vector(7 downto 0);
    signal irq, spi_mosi, spi_clk : std_logic;
begin
    clk <= not clk after 5 ns;
    dut: entity work.spi_master port map (
        clk => clk, reset => reset, cs => cs, rw => rw,
        addr => addr, data_in => data_in, data_out => data_out,
        irq => irq, spi_miso => '1', spi_mosi => spi_mosi,
        spi_clk => spi_clk, spi_cs_n => spi_cs_n
    );
    process
        procedure command(value : std_logic_vector(7 downto 0)) is
        begin
            wait until rising_edge(clk);
            cs <= '1'; rw <= '0'; addr <= "10"; data_in <= value;
            wait until falling_edge(clk);
            wait for 1 ns;
            cs <= '0'; rw <= '1';
        end;
    begin
        wait for 2 ns;
        assert spi_cs_n = x"FF" report "Reset must deselect all devices" severity failure;
        assert spi_clk = '0' report "Reset must hold SPI clock low" severity failure;
        reset <= '0';
        command(x"02");
        assert spi_cs_n = x"FE" report "Port 0 must select SD only" severity failure;
        command(x"00");
        assert spi_cs_n = x"FF" report "SD deselect failed" severity failure;
        command(x"12");
        assert spi_cs_n = x"FD" report "Port 1 must select flash only" severity failure;
        command(x"10");
        assert spi_cs_n = x"FF" report "Flash deselect failed" severity failure;
        command(x"12");
        reset <= '1';
        wait for 1 ns;
        assert spi_cs_n = x"FF" report "Reset must release selected flash" severity failure;
        reset <= '0';
        wait for 20 ns;
        assert spi_cs_n = x"FF" report "Reset release must keep devices deselected" severity failure;
        report "SPI reset and SD/flash chip-select checks passed";
        std.env.stop;
        wait;
    end process;
end;
