/* Bare-metal 68000 program for M68KUpperAddressSim. No OS or cache required. */
        .equ LOW_DATA,  0x00010000
        .equ HIGH_DATA, 0x01010000
        .equ TOP_DATA,  0x01fffff0
        .equ RESULT,    0x0000f100
        .text
        .long 0x00008000       /* Reset stack pointer. */
        .long 0x00000100       /* Reset PC. */
        .org 0x100
        .globl _start
_start:
        move.w #0x1111,LOW_DATA
        move.w #0x2222,HIGH_DATA
        cmpi.w #0x1111,LOW_DATA
        bne fail
        cmpi.w #0x2222,HIGH_DATA
        bne fail
        move.w #0x3333,TOP_DATA
        cmpi.w #0x3333,TOP_DATA
        bne fail

        /* Exercise each byte strobe without changing the other byte. */
        move.b #0xa5,LOW_DATA
        move.b #0xc7,LOW_DATA+1
        move.b #0xb6,HIGH_DATA
        move.b #0xd8,HIGH_DATA+1
        cmpi.w #0xa5c7,LOW_DATA
        bne fail
        cmpi.w #0xb6d8,HIGH_DATA
        bne fail

        /* Longwords use two 16-bit bus transfers in 68000 mode. */
        move.l #0x11223344,LOW_DATA+4
        move.l #0x55667788,HIGH_DATA+4
        move.l #0x99aabbcc,TOP_DATA+4
        cmpi.l #0x11223344,LOW_DATA+4
        bne fail
        cmpi.l #0x55667788,HIGH_DATA+4
        bne fail
        cmpi.l #0x99aabbcc,TOP_DATA+4
        bne fail

        /* Carry into bit 24 between the two halves of one longword. */
        movea.l #0x00fffffe,a0
        move.l #0xabcd0123,(a0)
        cmpi.l #0xabcd0123,(a0)
        bne fail
        move.w #0x5a3c,0x01fffffe
        cmpi.w #0x5a3c,0x01fffffe
        bne fail

        /* Fetch real instructions above 16 MiB before reporting success. */
        jmp high_entry+0x01000000
high_entry:
        move.w #0x600d,RESULT
        stop #0x2700
fail:
        move.w #0xdead,RESULT
        bra fail
