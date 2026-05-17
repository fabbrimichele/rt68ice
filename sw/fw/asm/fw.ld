MEMORY
{
    /* The hardware maps the first 8 bytes to ROM, but we define the actual ROM storage */
    RAM (rwx)   : ORIGIN = 0x00000400, LENGTH = 16K - 0x400
    ROM (rx)    : ORIGIN = 0x00004000, LENGTH = 16K
}

SECTIONS
{
    .bss (NOLOAD) : {
        . = ORIGIN(RAM) + LENGTH(RAM);
        _bss_start = .;
        *(.bss*)
    } > RAM

    .text : {
        *(.text*)
        *(.rodata*)
    } > ROM
}
