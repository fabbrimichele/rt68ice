MEMORY
{
    /* The hardware maps the first 8 bytes to ROM, but we define the actual ROM storage */
    RAM (rwx)   : ORIGIN = 0x00000000, LENGTH = 16K
    ROM (rx)    : ORIGIN = 0x00004000, LENGTH = 16K
    FB  (rw)    : ORIGIN = 0x00020000, LENGTH = 38400
}

SECTIONS
{
    .text 0x00000400 : {
        *(.text*)
        *(.rodata*)
    } > RAM

    .bss (NOLOAD) : {
        *(.bss*)                /* buffers and variables go here */
    } > RAM

    .fb (NOLOAD) : {
        _fb_start = .;
        *(.fb*)
        _fb_end = .;
    } > FB

    _fb_len_words = LENGTH(FB) / 2;
}
