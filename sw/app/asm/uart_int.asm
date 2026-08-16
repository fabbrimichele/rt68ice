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

    clr.b   char
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

; ===========================
; Include files
; ===========================
    INCLUDE '../../lib/asm/isr_vector.asm'
    INCLUDE '../../lib/asm/mem_map_uart.asm'
    INCLUDE '../../lib/asm/mem_map_leds.asm'

; ===========================
; Data Constants
; Must be after code to avoid alignment issues
; ===========================


; ===========================
; RAM Data Section
; ===========================
    section .bss
char        DS.B    1

