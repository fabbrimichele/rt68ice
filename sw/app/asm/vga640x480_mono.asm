    section .text, code

; 640x480 one-bitplane smoke test. Pixels select palette entry 0 or 1.
start:
    move.w  #VIDEO_MODE_640X480_1BPP,VIDEO_CTRL

    lea     VIDEO_PLTE,a0
    move.l  #$00000000,(a0)         ; Palette 0: black
    move.l  #$00FFFFFF,4(a0)        ; Palette 1: white

    bsr     draw_checkerboard
    bsr     draw_border
    trap    #14

; Fill the screen with alternating 16-pixel blocks. Inverting the pattern on
; every scanline also makes an incorrect line stride immediately visible.
draw_checkerboard:
    lea     _fb_start,a0
    move.w  #(SCREEN_HEIGHT-1),d1
    move.w  #$AAAA,d2
.line:
    move.w  #(WORDS_PER_LINE-1),d0
.word:
    move.w  d2,(a0)+
    not.w   d2
    dbra    d0,.word
    not.w   d2
    dbra    d1,.line
    rts

draw_border:
    ; Top and bottom rows.
    lea     _fb_start,a0
    bsr     hline
    lea     (_fb_start+((SCREEN_HEIGHT-1)*LINE_WIDTH_B)),a0
    bsr     hline

    ; Leftmost and rightmost pixels of every row.
    lea     _fb_start,a0
    move.w  #(SCREEN_HEIGHT-1),d0
.vertical:
    or.w    #$8000,(a0)
    or.w    #$0001,(LINE_WIDTH_B-2)(a0)
    adda.l  #LINE_WIDTH_B,a0
    dbra    d0,.vertical
    rts

hline:
    move.w  #(WORDS_PER_LINE-1),d0
.loop:
    move.w  #$FFFF,(a0)+
    dbra    d0,.loop
    rts

SCREEN_HEIGHT  equ 480
WORDS_PER_LINE equ 40
LINE_WIDTH_B   equ WORDS_PER_LINE*2

    include '../../lib/asm/mem_map_video.asm'

    section .bss
