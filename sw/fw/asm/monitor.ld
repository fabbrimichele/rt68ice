MEMORY
{
    /* The hardware maps the first 8 bytes to ROM, but we define the actual ROM storage */
    RAM (rwx)   : ORIGIN = 0x00000000, LENGTH = 16K
    ROM (rx)    : ORIGIN = 0x00004000, LENGTH = 16K
}

SECTIONS
{
    /* Vector Table Boundaries */
    _vector_table_start = ORIGIN(RAM);
    _vector_table_end   = ORIGIN(RAM) + 0x0400;

    /* Define the boundary where the user loading area begins */
    _user_ram_start = _vector_table_end;

    /* Force the monitor's private variables to start exactly at $3F00 */
    .bss 0x00003F00 (NOLOAD) : {
        _bss_start = .;
        *(.bss*)
        _bss_end = .;

    } > RAM

    /* The initial stack pointer is at the top of user RAM */
    _user_ram_end = _bss_start;
    _stack_top = _bss_start;

    .text ORIGIN(ROM) : {
        *(.text*)
        *(.rodata*)
    } > ROM
}
