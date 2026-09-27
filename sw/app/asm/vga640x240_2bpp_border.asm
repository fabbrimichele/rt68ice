    section .text, code

; 640x240 two-bitplane smoke test.
start:
    move.w  #VIDEO_MODE_640X240_2BPP,VIDEO_CTRL
    bsr     clear_screen
    bsr     draw_border
    trap    #14

clear_screen:
    lea     _fb_start,a0
    move.w  #(FRAMEBUFFER_WORDS-1),d0
.loop:
    clr.w   (a0)+
    dbra    d0,.loop
    rts

draw_border:
    lea     _fb_start,a0
    bsr     hline
    lea     (_fb_start+((SCREEN_HEIGHT-1)*LINE_WIDTH_B)),a0
    bsr     hline

    lea     _fb_start,a0
    move.w  #(SCREEN_HEIGHT-1),d0
.vertical:
    or.w    #$8000,(a0)
    or.w    #$8000,2(a0)
    or.w    #$0001,(LINE_WIDTH_B-4)(a0)
    or.w    #$0001,(LINE_WIDTH_B-2)(a0)
    adda.l  #LINE_WIDTH_B,a0
    dbra    d0,.vertical
    rts

hline:
    move.w  #(GROUPS_PER_LINE-1),d0
.loop:
    move.l  #$FFFFFFFF,(a0)+
    dbra    d0,.loop
    rts

SCREEN_HEIGHT     equ 240
GROUPS_PER_LINE   equ 40
WORDS_PER_GROUP   equ 2
LINE_WIDTH_B      equ GROUPS_PER_LINE*WORDS_PER_GROUP*2
FRAMEBUFFER_WORDS equ SCREEN_HEIGHT*GROUPS_PER_LINE*WORDS_PER_GROUP

    include '../../lib/asm/mem_map_video.asm'

    section .bss
