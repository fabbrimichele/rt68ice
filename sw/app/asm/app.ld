MEMORY
{
    /* Reserve the first 64 KiB for vectors, firmware data and system use. */
    SDRAM (rwx) : ORIGIN = 0x00010000, LENGTH = 0x007F0000
    ROM (rx)    : ORIGIN = 0x00FC0000, LENGTH = 16K
    FB  (rw)    : ORIGIN = 0x00E00000, LENGTH = 76800
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
