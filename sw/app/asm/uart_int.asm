    section .text, code

; ===========================
; Program code
; ===========================
; UART_IER:
; B7-4 : unused
; BIT-3: modem receive interrupt
; BIT-2: receive line status interrupt
; BIT-1: transmit holding register
; BIT-0: receive holding register

start:
    or.w    #$0700,sr           ; Mask interrupts while installing the vector
    move.l  #uart_isr,VT_INT_4  ; Set interrupt handler
    move.l  #usb_isr,VT_INT_6   ; USB has higher priority than the UART

    clr.b   char
    ; A connected mouse can leave a USB report pending.  Acknowledge both
    ; hosts before interrupts are enabled so level 6 cannot starve UART level 4.
    move.w  USB1_STATUS,d0
    move.w  USB2_STATUS,d0
    move.b  #$01,UART_IER	    ; Enable interrupt on receive holding register
    and.w   #$f8ff,sr           ; Enable all interrupts on 68000 (Clear mask bits)
.loop:
    cmp.b   #'Q',char
    bne     .loop

    or.w    #$0700,sr           ; Prevent a final ISR while shutting down
    move.b  #$00,UART_IER	    ; Disable interrupt on receive holding register
    trap    #14

uart_isr:
    movem.l d0,-(sp)            ; Save d0

    ; The UART is wired to the upper byte of the 16-bit bus, so all UART
    ; registers must be accessed with move.b.  A word read would see IIR $04
    ; as $0400 and leave RBR unread, keeping the interrupt asserted.
    move.b  UART_IIR,d0
    andi.b  #$0f,d0             ; Keep the interrupt identification bits
    cmpi.b  #4,d0               ; Received Data Ready (0100)
    bne     .ret

    moveq   #0,d0
    move.b  UART_RBR,d0         ; Read character and acknowledge RX interrupt
    move.w  d0,LEDS
    move.b  d0,char
.ret:
    movem.l (sp)+,d0            ; Restore D0
    rte                         ; Return from int

; USB has interrupt priority over the UART.  This demo does not consume USB
; reports, but it must acknowledge them or a pending level-6 interrupt would
; prevent the level-4 UART interrupt from being serviced.
usb_isr:
    movem.l d0,-(sp)
    move.w  USB1_STATUS,d0
    move.w  USB2_STATUS,d0
    movem.l (sp)+,d0
    rte

; ===========================
; Include files
; ===========================
    INCLUDE '../../lib/asm/isr_vector.asm'
    INCLUDE '../../lib/asm/mem_map_uart.asm'
    INCLUDE '../../lib/asm/mem_map_leds.asm'
    INCLUDE '../../lib/asm/mem_map_usb.asm'

; ===========================
; Data Constants
; Must be after code to avoid alignment issues
; ===========================


; ===========================
; RAM Data Section
; ===========================
    section .bss
char        DS.B    1
