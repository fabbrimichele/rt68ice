; "Twinkle, Twinkle, Little Star" on YM2149 channel A (both audio outputs).
; Load with the ROM monitor. Plays once, silences the PSG, then TRAP #14.
    section .text, code

PSG_WRITE MACRO
    move.w  #\1,PSG_ADDRESS
    move.w  #\2,PSG_DATA
    ENDM

; Each entry contains a 12-bit tone period and a duration in 25 MHz ticks.
NOTE MACRO
    dc.w    \1
    dc.l    \2*BEAT_TICKS
    ENDM

; PSG tone frequency = 2,000,000 / (16 * period).
C4          equ     478
D4          equ     426
E4          equ     379
F4          equ     358
G4          equ     319
A4          equ     284
BEAT_TICKS  equ     7500000     ; 300 ms per beat (200 beats/minute).
GAP_TICKS   equ     625000      ; 25 ms of silence separates repeated notes.

start:
    PSG_WRITE 8,0
    PSG_WRITE 9,0
    PSG_WRITE 10,0
    PSG_WRITE 7,$3e             ; Tone A only, all noise disabled.
    lea     melody,a0

.next_note:
    move.w  (a0)+,d2
    beq     .finished          ; Zero period marks the end of the tune.
    move.l  (a0)+,d3

    move.w  #0,PSG_ADDRESS
    move.w  d2,PSG_DATA         ; Low eight bits of period.
    lsr.w   #8,d2
    move.w  #1,PSG_ADDRESS
    move.w  d2,PSG_DATA         ; High four bits of period.
    PSG_WRITE 8,12             ; Fixed volume; envelope disabled.

    sub.l   #GAP_TICKS,d3
    bsr     wait_ticks
    PSG_WRITE 8,0
    move.l  #GAP_TICKS,d3
    bsr     wait_ticks
    bra     .next_note

.finished:
    PSG_WRITE 8,0
    PSG_WRITE 7,$3f
    trap    #14

; D3 = duration in system-clock ticks. Clobbers D0/D1, preserves A0.
; Unsigned elapsed-time subtraction handles the hardware counter wrapping.
wait_ticks:
    move.l  COUNTER,d0
.wait:
    move.l  COUNTER,d1
    sub.l   d0,d1
    cmp.l   d3,d1
    bcs     .wait
    rts

    include '../../lib/asm/mem_map_psg.asm'
    include '../../lib/asm/mem_map_counter.asm'

melody:
    ; Twinkle, twinkle, little star.
    NOTE C4,1
    NOTE C4,1
    NOTE G4,1
    NOTE G4,1
    NOTE A4,1
    NOTE A4,1
    NOTE G4,2
    ; How I wonder what you are.
    NOTE F4,1
    NOTE F4,1
    NOTE E4,1
    NOTE E4,1
    NOTE D4,1
    NOTE D4,1
    NOTE C4,2
    ; Up above the world so high.
    NOTE G4,1
    NOTE G4,1
    NOTE F4,1
    NOTE F4,1
    NOTE E4,1
    NOTE E4,1
    NOTE D4,2
    ; Like a diamond in the sky.
    NOTE G4,1
    NOTE G4,1
    NOTE F4,1
    NOTE F4,1
    NOTE E4,1
    NOTE E4,1
    NOTE D4,2
    ; Twinkle, twinkle, little star.
    NOTE C4,1
    NOTE C4,1
    NOTE G4,1
    NOTE G4,1
    NOTE A4,1
    NOTE A4,1
    NOTE G4,2
    ; How I wonder what you are.
    NOTE F4,1
    NOTE F4,1
    NOTE E4,1
    NOTE E4,1
    NOTE D4,1
    NOTE D4,1
    NOTE C4,2
    dc.w    0
