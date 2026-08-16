MEMORY
{
    /* The hardware maps the first 8 bytes to ROM, but we define the actual ROM storage */
    SDRAM (rwx) : ORIGIN = 0x00800000, LENGTH = 8M
    ROM (rx)    : ORIGIN = 0x00004000, LENGTH = 16K
    FB  (rw)    : ORIGIN = 0x00100000, LENGTH = 76800
}

SECTIONS
{
    .text : {
        *(.text*)
        *(.rodata*)
    } > SDRAM

    .bss (NOLOAD) : {
        *(.bss*)                /* buffers and variables go here */
    } > SDRAM

    .fb (NOLOAD) : {
        _fb_start = .;
        *(.fb*)
        _fb_end = .;
    } > FB

    _fb_len_words = LENGTH(FB) / 2;
}
