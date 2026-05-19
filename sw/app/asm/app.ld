MEMORY
{
    /* The hardware maps the first 8 bytes to ROM, but we define the actual ROM storage */
    RAM (rwx)   : ORIGIN = 0x00000000, LENGTH = 16K
    ROM (rx)    : ORIGIN = 0x00004000, LENGTH = 16K
}

SECTIONS
{
    .bss 0x00000400 (NOLOAD) : {
        _bss_start = .;         /* Mark the start of uninitialized RAM */
        *(.bss*)                /* buffers and variables go here */
        _bss_end = .;           /* Mark where your variables end */
        /* Calculate the Initial Stack Pointer at the very top edge of RAM */
        _stack_top = ORIGIN(RAM) + LENGTH(RAM);
    } > RAM

    .text : {
        *(.text*)
        *(.rodata*)
    } > ROM
}
