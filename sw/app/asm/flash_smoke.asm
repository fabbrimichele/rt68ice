; Read-only onboard W25Q256JV smoke test, run from the ROM monitor.
; No write-enable, program, erase, reset or address-mode-changing commands.
    section .text, code

PRINT MACRO
    lea     \1,a0
    bsr     put_str
    ENDM

start:
    move.w  sr,d7
    ori.w   #$0700,sr              ; The SD and flash share one controller.
    PRINT   msg_banner
    bsr     spi_wait_ready
    tst.b   d1
    bne     fail_timeout
    move.b  #$00,SPI_CDST          ; Deselect both devices.
    move.b  #$0e,SPI_CONF          ; 8-bit, 25 MHz / 128 (~195 kHz).

    lea     cmd_id,a0
    moveq   #1,d2
    lea     flash_id,a1
    moveq   #3,d3
    bsr     flash_read
    tst.b   d1
    bne     fail_timeout
    PRINT   msg_id
    lea     flash_id,a1
    moveq   #2,d3
.print_id:
    move.b  (a1)+,d0
    bsr     put_hex_byte
    dbra    d3,.print_id
    PRINT   msg_newline
    ; Accept the two W25Q256JV memory-type variants, not a floating bus.
    cmpi.b  #$ef,flash_id
    bne     fail_id
    cmpi.b  #$19,flash_id+2
    bne     fail_id
    cmpi.b  #$40,flash_id+1
    beq     .id_ok
    cmpi.b  #$70,flash_id+1
    bne     fail_id
.id_ok:
    lea     cmd_status,a0
    moveq   #1,d2
    lea     flash_status,a1
    moveq   #1,d3
    bsr     flash_read
    tst.b   d1
    bne     fail_timeout
    PRINT   msg_status
    move.b  flash_status,d0
    bsr     put_hex_byte
    PRINT   msg_newline
    btst    #0,flash_status        ; Do not read an actively programming chip.
    bne     fail_busy

    lea     cmd_read_zero,a0
    moveq   #5,d2
    lea     first_read,a1
    moveq   #32,d3
    bsr     flash_read
    tst.b   d1
    bne     fail_timeout
    lea     cmd_read_zero,a0
    moveq   #5,d2
    lea     second_read,a1
    moveq   #32,d3
    bsr     flash_read
    tst.b   d1
    bne     fail_timeout

    PRINT   msg_data
    lea     first_read,a1
    moveq   #31,d3
.dump:
    move.b  (a1)+,d0
    bsr     put_hex_byte
    moveq   #' ',d0
    bsr     put_chr
    dbra    d3,.dump
    PRINT   msg_newline
    lea     first_read,a0
    lea     second_read,a1
    moveq   #31,d3
.compare:
    cmpm.b  (a0)+,(a1)+
    bne     fail_mismatch
    dbra    d3,.compare
    PRINT   msg_ok
    bra     done

fail_timeout:
    PRINT   msg_timeout
    bra     done
fail_id:
    PRINT   msg_bad_id
    bra     done
fail_busy:
    PRINT   msg_busy
    bra     done
fail_mismatch:
    PRINT   msg_mismatch
done:
    move.b  #$00,SPI_CDST          ; Release flash clock and both chip selects.
    move.w  d7,sr
    trap    #14

; a0 = command/address bytes, d2 = their count (>0),
; a1 = destination, d3 = read count (>0). Returns d1 = 0 success / 1 timeout.
; CS stays low throughout, and is released even on failure.
flash_read:
    movem.l d2-d4/a0-a1,-(sp)
    move.b  #$12,SPI_CDST          ; Select port 1, leave SD deselected.
    move.w  d2,d4
    subq.w  #1,d4
.command:
    move.b  (a0)+,d0
    bsr     spi_transfer
    tst.b   d1
    bne     .done
    dbra    d4,.command
    move.w  d3,d4
    subq.w  #1,d4
.read:
    move.b  #$ff,d0
    bsr     spi_transfer
    tst.b   d1
    bne     .done
    move.b  d0,(a1)+
    dbra    d4,.read
.done:
    move.b  #$10,SPI_CDST
    movem.l (sp)+,d2-d4/a0-a1
    rts

; d0 = outgoing byte, returns incoming byte in d0, timeout flag in d1.
spi_transfer:
    move.b  d0,SPI_DTLW
    move.b  #$13,SPI_CDST          ; START must also keep port 1 selected.
    ; Observe BUSY before waiting for completion (START is latched).
    move.l  d2,-(sp)
    move.w  #$ffff,d2
.started:
    btst    #0,SPI_CDST
    bne     .wait
    dbra    d2,.started
    moveq   #1,d1
    bra     .done
.wait:
    bsr     spi_wait_ready
    tst.b   d1
    bne     .done
    move.b  SPI_DTLW,d0
.done:
    move.l  (sp)+,d2
    rts

spi_wait_ready:
    move.l  d2,-(sp)
    move.w  #$ffff,d2
.wait:
    btst    #0,SPI_CDST
    beq     .ready
    dbra    d2,.wait
    moveq   #1,d1
    bra     .done
.ready:
    moveq   #0,d1
.done:
    move.l  (sp)+,d2
    rts

put_hex_byte:
    movem.l d0-d2,-(sp)
    move.b  d0,d2
    lsr.b   #4,d0
    bsr     .nibble
    move.b  d2,d0
    andi.b  #$0f,d0
    bsr     .nibble
    movem.l (sp)+,d0-d2
    rts
.nibble:
    addi.b  #'0',d0
    cmpi.b  #'9',d0
    bls     .emit
    addq.b  #7,d0
.emit:
    bra     put_chr

SPI_DTLW equ $00f20001
SPI_CDST equ $00f20005
SPI_CONF equ $00f20007
cmd_id:        dc.b $9f
cmd_status:    dc.b $05
; Dedicated 4-byte read works regardless of the current address mode.
cmd_read_zero: dc.b $13,$00,$00,$00,$00
msg_banner:    dc.b 'Onboard flash read-only test',CR,LF,NUL
msg_id:        dc.b 'JEDEC ID: ',NUL
msg_status:    dc.b 'Status: ',NUL
msg_data:      dc.b 'Flash offset 00000000 (32 bytes):',CR,LF,NUL
msg_newline:   dc.b CR,LF,NUL
msg_ok:        dc.b 'PASS: expected flash ID, two reads match (no writes)',CR,LF,NUL
msg_timeout:   dc.b 'FAIL: SPI timeout',CR,LF,NUL
msg_bad_id:    dc.b 'FAIL: expected EF4019 or EF7019; check FPGA/pins/flash',CR,LF,NUL
msg_busy:      dc.b 'FAIL: flash is busy programming/erasing',CR,LF,NUL
msg_mismatch:  dc.b 'FAIL: repeated reads differ',CR,LF,NUL

    even
    include '../../lib/asm/console_io_uart.asm'
    section .bss
flash_id:      ds.b 3
flash_status:  ds.b 1
first_read:    ds.b 32
second_read:   ds.b 32
