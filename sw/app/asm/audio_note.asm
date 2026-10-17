; Play approximately 440 Hz on both audio outputs for one second, then mute.
; Run from the ROM monitor; TRAP #14 returns to its prompt.
    section .text, code

PSG_WRITE MACRO
    move.w  #\1,PSG_ADDRESS
    move.w  #\2,PSG_DATA
    ENDM

start:
    ; Silence all channels before programming the tone.
    PSG_WRITE 8,0
    PSG_WRITE 9,0
    PSG_WRITE 10,0
    PSG_WRITE 7,$3f             ; Disable all tone and noise sources.

    ; 4 MHz enable / 2 = 2 MHz PSG clock.
    ; Frequency = 2,000,000 / (16 * 284) = approximately 440.14 Hz.
    PSG_WRITE 0,$1c             ; Channel A period low byte: 284 = $011c.
    PSG_WRITE 1,$01             ; Channel A period high nibble.
    PSG_WRITE 7,$3e             ; Enable tone A only; disable all noise.
    PSG_WRITE 8,$0f             ; Channel A maximum fixed volume, no envelope.

    ; Use elapsed hardware ticks rather than a CPU instruction delay.
    ; Unsigned subtraction also handles the 32-bit counter wrapping.
    move.l  COUNTER,d0
.wait:
    move.l  COUNTER,d1
    sub.l   d0,d1
    cmpi.l  #25000000,d1        ; One second at the 25 MHz system clock.
    bcs     .wait

    PSG_WRITE 8,0              ; Mute channel A.
    PSG_WRITE 7,$3f             ; Disable all tone and noise sources.
    trap    #14

    include '../../lib/asm/mem_map_psg.asm'
    include '../../lib/asm/mem_map_counter.asm'
