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
    move.l  #uart_isr,VT_INT_4  ; Set interrupt handler
    move.b  #$01,UART_IER	    ; Enable interrupt on receive holding register
    and.w   #$f8ff,sr           ; Enable all interrupts on 68000 (Clear mask bits)
.loop:
    cmp.b   #'Q',char
    bne     .loop
    move.b  #$00,UART_IER	    ; Disable interrupt on receive holding register
    and.w   #$ffff,sr           ; Disable all interrupts on 68000 (Clear mask bits)
    trap    #14

uart_isr:
    movem.l d0,-(sp)            ; Save d0
    move.w  UART_IIR,d0         ; Read interrupt status register (and ack interrupt)
    cmp.w   #4,d0               ; Received Data Ready (0100)
    beq     .read
    cmp.w   #2,d0               ; Transmitter Holding Register Ready (0010)
    beq     .write
    bne     .ret
.read:
    move.w  UART_RBR,d0         ; Read character to d0
    move.w  d0,LEDS
    move.b  d0,char
    bne     .ret
.write:
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


