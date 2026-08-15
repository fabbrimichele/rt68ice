    section .text, code

; Compatibility note: the USB host assumes byte-aligned signed 8-bit X/Y
; values. Mice that use packed 12-bit axes in report protocol will produce
; incorrect movement unless their HID reports are decoded differently.

; ===========================
; Program code
; ===========================
start:
    or.w    #$0700,SR               ; Mask interrupts during initialization
    move.l  #usb_isr,VT_INT_6

    clr.b   mouse_updated
    clr.b   have_sample
    move.w  #(SCREEN_WIDTH/2),cursor_x
    move.w  #(SCREEN_HEIGHT/2),cursor_y

    move.w  #2,VIDEO_CTRL           ; 640x480, two bitplanes
    lea     VIDEO_PLTE,a0
    move.l  #$00000000,(a0)         ; Palette 0: black
    move.l  #$00FFFFFF,4(a0)        ; Palette 1: drawing
    move.l  #$00FFFFFF,8(a0)        ; Palette 2: cursor
    move.l  #$00FFFFFF,12(a0)       ; Palette 3: drawing + cursor
    bsr     clear_screen

    moveq   #CURSOR_COLOR,d2
    bsr     draw_cursor

    and.w   #$F8FF,SR               ; Enable all interrupt levels

.wait:
    tst.b   mouse_updated
    beq     .wait

    ; Copy the ISR snapshot atomically. Reports arriving while masked remain
    ; pending and will be processed after interrupts are enabled again.
    or.w    #$0700,SR
    moveq   #0,d2
    move.b  mouse_acc_x,d2
    moveq   #0,d3
    move.b  mouse_acc_y,d3
    moveq   #0,d1
    move.b  mouse_raw_buttons,d1
    clr.b   mouse_updated
    and.w   #$F8FF,SR

    btst    #2,d1                   ; Middle button exits the program
    bne     .exit

    tst.b   have_sample
    bne     .calculate_delta

    ; The hardware registers are wrapping accumulators. Use the first report
    ; only as the reference point so the cursor does not jump at startup.
    move.b  d2,last_acc_x
    move.b  d3,last_acc_y
    move.b  #1,have_sample
    bra     .apply_buttons

.calculate_delta:
    moveq   #0,d0
    move.b  d2,d0
    sub.b   last_acc_x,d0
    move.b  d2,last_acc_x
    ext.w   d0
    move.w  d0,d4                  ; D4.W = signed X delta

    moveq   #0,d0
    move.b  d3,d0
    sub.b   last_acc_y,d0
    move.b  d3,last_acc_y
    ext.w   d0
    move.w  d0,d5                  ; D5.W = signed Y delta

    ; Mice normally send periodic reports even when they have not moved.
    ; Do not erase and redraw the cursor for a zero-delta report: updating the
    ; live framebuffer unnecessarily makes the cursor flicker on the display.
    tst.w   d4
    bne     .move_cursor
    tst.w   d5
    beq     .apply_buttons

.move_cursor:
    move.w  cursor_x,d6             ; Preserve the old position
    move.w  cursor_y,d7

    add.w   d4,cursor_x
    add.w   d5,cursor_y
    bsr     clamp_cursor

    ; Clamping can also turn a non-zero report into no movement at an edge.
    cmp.w   cursor_x,d6
    bne     .redraw_cursor
    cmp.w   cursor_y,d7
    beq     .apply_buttons

.redraw_cursor:
    move.w  cursor_x,d4             ; Preserve the new position
    move.w  cursor_y,d5
    move.w  d6,cursor_x
    move.w  d7,cursor_y

    moveq   #0,d2                   ; Erase the cursor at its old position
    bsr     draw_cursor

    move.w  d4,cursor_x
    move.w  d5,cursor_y
    moveq   #CURSOR_COLOR,d2
    bsr     draw_cursor
    bra     .apply_buttons

.apply_buttons:
    btst    #0,d1                   ; Left button draws a persistent pixel
    bne     .draw_at_cursor
    btst    #1,d1                   ; Right button clears a persistent pixel
    bne     .clear_at_cursor
    bra     .wait

.draw_at_cursor:
    moveq   #DRAW_COLOR,d2
    bra     .update_pixel

.clear_at_cursor:
    moveq   #0,d2

.update_pixel:
    move.w  cursor_x,d0
    move.w  cursor_y,d1
    moveq   #DRAW_PLANE_MASK,d4
    bsr     draw_pixel
    bra     .wait

.exit:
    trap    #14

; ===========================
; USB interrupt handler
; ===========================
usb_isr:
    movem.l d0-d1,-(sp)
    move.w  USB_IRQ_STATUS,d1

    ; Host 1 shares the interrupt line. Acknowledge it even though this demo
    ; uses only the mouse attached to Host 2.
    btst    #0,d1
    beq     .host2
    move.w  USB1_STATUS,d0

.host2:
    btst    #1,d1
    beq     .done
    move.w  USB2_STATUS,d0          ; Acknowledge Host 2
    andi.w  #$0003,d0
    cmpi.w  #2,d0                   ; Device type 2 is a mouse
    bne     .done

    move.w  USB2_MOUSE_BTN,d0
    move.b  d0,mouse_raw_buttons
    move.w  USB2_MOUSE_DX,d0
    move.b  d0,mouse_acc_x
    move.w  USB2_MOUSE_DY,d0
    move.b  d0,mouse_acc_y
    move.b  #1,mouse_updated        ; Publish complete snapshot last

.done:
    movem.l (sp)+,d0-d1
    rte

; ===========================
; Cursor helpers
; ===========================
clamp_cursor:
    cmpi.w  #CURSOR_RADIUS,cursor_x
    bge     .check_x_max
    move.w  #CURSOR_RADIUS,cursor_x
.check_x_max:
    cmpi.w  #CURSOR_MAX_X,cursor_x
    ble     .check_y_min
    move.w  #CURSOR_MAX_X,cursor_x
.check_y_min:
    cmpi.w  #CURSOR_RADIUS,cursor_y
    bge     .check_y_max
    move.w  #CURSOR_RADIUS,cursor_y
.check_y_max:
    cmpi.w  #CURSOR_MAX_Y,cursor_y
    ble     .done
    move.w  #CURSOR_MAX_Y,cursor_y
.done:
    rts

; Draw a cross centered at cursor_x/cursor_y.
; Input: D2.B = palette color
draw_cursor:
    movem.l d0-d4,-(sp)
    moveq   #CURSOR_PLANE_MASK,d4

    move.w  #(-CURSOR_RADIUS),d3
.horizontal:
    move.w  cursor_x,d0
    add.w   d3,d0
    move.w  cursor_y,d1
    bsr     draw_pixel
    addq.w  #1,d3
    cmpi.w  #(CURSOR_RADIUS+1),d3
    blt     .horizontal

    move.w  #(-CURSOR_RADIUS),d3
.vertical:
    move.w  cursor_x,d0
    move.w  cursor_y,d1
    add.w   d3,d1
    bsr     draw_pixel
    addq.w  #1,d3
    cmpi.w  #(CURSOR_RADIUS+1),d3
    blt     .vertical

    movem.l (sp)+,d0-d4
    rts

; Draw one pixel in the 640x480 two-bitplane framebuffer.
; Input: D0.W = X, D1.W = Y, D2.B = palette color (0-3)
;        D4.B = bitplane update mask (bit 0: drawing, bit 1: cursor)
draw_pixel:
    movem.l d0-d7/a0,-(sp)
    move.b  d4,d7

    ; Address = framebuffer + Y*160 + (X/16)*4.
    moveq   #0,d3
    move.w  d1,d3
    move.l  d3,d4
    lsl.l   #7,d3                  ; Y * 128
    lsl.l   #5,d4                  ; Y * 32
    add.l   d4,d3                  ; Y * 160

    moveq   #0,d5
    move.w  d0,d5
    andi.w  #$FFF0,d5
    lsr.w   #2,d5                  ; (X / 16) * 4
    add.l   d5,d3

    lea     _fb_start,a0
    adda.l  d3,a0

    ; Pixels are stored MSB-first in each 16-pixel bitplane word.
    moveq   #0,d5
    move.w  d0,d5
    andi.w  #$000F,d5
    move.w  #$8000,d4
    lsr.w   d5,d4
    move.w  d4,d6
    not.w   d6

    btst    #0,d7
    beq     .plane1
    btst    #0,d2
    beq     .clear_plane0
    or.w    d4,(a0)
    bra     .plane1
.clear_plane0:
    and.w   d6,(a0)

.plane1:
    btst    #1,d7
    beq     .done
    btst    #1,d2
    beq     .clear_plane1
    or.w    d4,2(a0)
    bra     .done
.clear_plane1:
    and.w   d6,2(a0)

.done:
    movem.l (sp)+,d0-d7/a0
    rts

clear_screen:
    lea     _fb_start,a0
    move.w  #(_fb_len_words-1),d0
.loop:
    clr.w   (a0)+
    dbra    d0,.loop
    rts

; ===========================
; Value constants
; ===========================
SCREEN_WIDTH    equ     640
SCREEN_HEIGHT   equ     480
LINE_WIDTH_B    equ     160
CURSOR_RADIUS   equ     5
CURSOR_MAX_X    equ     SCREEN_WIDTH-CURSOR_RADIUS-1
CURSOR_MAX_Y    equ     SCREEN_HEIGHT-CURSOR_RADIUS-1
DRAW_COLOR      equ     1
DRAW_PLANE_MASK equ     1
CURSOR_COLOR    equ     2
CURSOR_PLANE_MASK equ   2

; ===========================
; Include files
; ===========================
    include '../../lib/asm/mem_map_usb.asm'
    include '../../lib/asm/mem_map_video.asm'
    include '../../lib/asm/isr_vector.asm'

; ===========================
; RAM data
; ===========================
    section .bss
mouse_acc_x:
    ds.b    1
mouse_acc_y:
    ds.b    1
mouse_raw_buttons:
    ds.b    1
mouse_updated:
    ds.b    1
have_sample:
    ds.b    1
last_acc_x:
    ds.b    1
last_acc_y:
    ds.b    1
    even
cursor_x:
    ds.w    1
cursor_y:
    ds.w    1
