    section .text, code

; ===========================
; Program code
; ===========================
start:
    or.w    #$0700,SR           ; Mask interrupts during setup
    move.l  #usb_isr,VT_INT_6   ; Set USB interrupt handler
    clr.b   mouse_updated
    move.w  #$0003,USB_IRQ_ENABLE ; Enable Host 1 and Host 2 USB interrupts

    lea     msg_title,a0
    bsr     put_str

    and.w   #$F8FF,SR           ; Enable all interrupt levels

.wait:
    tst.b   mouse_updated
    beq     .wait

    ; Take an atomic snapshot. New reports remain pending while interrupts are
    ; masked and will be handled after they are enabled again.
    or.w    #$0700,SR

    move.b  mouse_acc_x,d0
    ext.w   d0
    ext.l   d0
    move.l  d0,d2

    move.b  mouse_acc_y,d0
    ext.w   d0
    ext.l   d0
    move.l  d0,d3

    moveq   #0,d4
    move.b  mouse_buttons,d4

    clr.b   mouse_updated

    and.w   #$F8FF,SR

    lea     msg_acc_x,a0
    bsr     put_str
    move.l  d2,d0
    bsr     bin_to_dec_signed

    lea     msg_acc_y,a0
    bsr     put_str
    move.l  d3,d0
    bsr     bin_to_dec_signed

    lea     msg_buttons,a0
    bsr     put_str
    move.l  d4,d0
    bsr     bin_to_dec

    lea     msg_newline,a0
    bsr     put_str
    bra     .wait

usb_isr:
    movem.l d0-d1,-(sp)
    move.w  USB_IRQ_STATUS,d1   ; Snapshot interrupt sources without clearing

    ; This program ignores Host 1 data, but it must acknowledge Host 1 if it
    ; shares the interrupt line and happens to have a pending report.
    btst    #0,d1
    beq     .host2
    move.w  USB1_STATUS,d0

.host2:
    btst    #1,d1
    beq     .done
    move.w  USB2_STATUS,d0      ; Acknowledge Host 2

    move.w  USB2_MOUSE_DX,d0
    move.b  d0,mouse_acc_x
    move.w  USB2_MOUSE_DY,d0
    move.b  d0,mouse_acc_y
    move.w  USB2_MOUSE_BTN,d0
    move.b  d0,mouse_buttons
    move.b  #1,mouse_updated    ; Publish the complete snapshot last

.done:
    movem.l (sp)+,d0-d1
    rte

; ===========================
; Include files
; ===========================
    include '../../lib/asm/mem_map_usb.asm'
    include '../../lib/asm/console_io_uart.asm'
    include '../../lib/asm/conv_dec.asm'
    include '../../lib/asm/isr_vector.asm'

; ===========================
; Data Constants
; Must be after code to avoid alignment issues
; ===========================
msg_title:
    dc.b    CR,LF,"USB Host 2 mouse",CR,LF,NUL

msg_acc_x:
    dc.b    "ACCX=",NUL

msg_acc_y:
    dc.b    " ACCY=",NUL

msg_buttons:
    dc.b    " BTN=",NUL

msg_newline:
    dc.b    CR,LF,NUL

; ===========================
; RAM Data Section
; ===========================
    section .bss
mouse_acc_x:
    ds.b    1
mouse_acc_y:
    ds.b    1
mouse_buttons:
    ds.b    1
mouse_updated:
    ds.b    1
