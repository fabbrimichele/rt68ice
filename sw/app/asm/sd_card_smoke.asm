; Minimal SD-card SPI smoke test for the shared spi_master core.
;
; It initializes an SDHC/SDXC card, reads LBA 0, and checks the MBR signature
; ($55AA).  The controller has no card-detect or TX-ready flags: status bit 0
; is BUSY, and every byte transfer requires a DATA_LO write followed by START.

    section .text, code

PRINT MACRO
    lea     \1,a0
    bsr     put_str
    ENDM

start:
    move.w  #$0000,LEDS
    PRINT   msg_banner

    ; 8 bits per transfer, system clock / 128 = 195.3125 kHz.
    ; The rt68f driver uses /64 with a 16 MHz clock (250 kHz); /128 is the
    ; conservative equivalent on this 25 MHz system and meets SD's <=400 kHz
    ; initialization limit.
    move.b  #SPI_CONFIG_SLOW,SPI_CONF

    ; Force the shared core into a known deselected state, then provide at
    ; least 80 clocks with CS high.
    moveq   #1,d0
    bsr     spi_set_cs
    tst.b   d0
    bne     fail_timeout

    moveq   #9,d2
.initial_clocks:
    move.b  #$ff,d0
    moveq   #SPI_START_DESELECTED,d1
    bsr     spi_transfer
    tst.b   d1
    bne     fail_timeout
    dbra    d2,.initial_clocks

    moveq   #0,d0
    bsr     spi_set_cs
    tst.b   d0
    bne     fail_timeout

    ; The known-working rt68f driver clocks one byte after asserting CS.
    move.b  #$ff,d0
    moveq   #SPI_START_SELECTED,d1
    bsr     spi_transfer
    tst.b   d1
    bne     fail_timeout

    PRINT   msg_init
    bsr     sd_initialize
    tst.b   d1
    bne     fail_init

    ; Once initialization is complete, use the maximum rate this 25 MHz
    ; system-clock SPI core can generate: 25 MHz / 2 = 12.5 MHz.
    move.b  #SPI_CONFIG_FAST,SPI_CONF

    PRINT   msg_read
    bsr     sd_read_lba0
    tst.b   d1
    bne     fail_read

    cmpi.b  #$55,sector_buffer+510
    bne     fail_signature
    cmpi.b  #$aa,sector_buffer+511
    bne     fail_signature

    PRINT   msg_ok
    bra     done

fail_timeout:
    PRINT   msg_timeout
    bra     fail
fail_init:
    PRINT   msg_init_fail
    bra     fail
fail_read:
    PRINT   msg_read_fail
    bra     fail
fail_signature:
    PRINT   msg_signature_fail
fail:
    moveq   #1,d0
    bsr     spi_set_cs
done:
    trap    #14

; ---------------------------------------------------------------------------
; sd_initialize
; Output: d1.b = 0 success, 1 failure
; ---------------------------------------------------------------------------
sd_initialize:
    movem.l d2-d4/a0,-(sp)

    ; CMD0: enter idle state; expected R1 = $01.
    move.b  #'0',d0
    bsr     put_chr
    lea     cmd0,a0
    bsr     sd_command
    tst.b   d1
    bne     .error
    cmpi.b  #$01,d0
    beq     .cmd0_ok
    move.w  d0,LEDS                 ; Diagnostic: show unexpected CMD0 R1.
    bra     .error
.cmd0_ok:

    ; CMD8: verify a version-2 card; expected R1 = $01 and R7[3] = $AA.
    move.b  #'8',d0
    bsr     put_chr
    lea     cmd8,a0
    bsr     sd_command
    tst.b   d1
    bne     .error
    cmpi.b  #$01,d0
    bne     .error
    moveq   #3,d2
.read_r7:
    move.b  #$ff,d0
    moveq   #SPI_START_SELECTED,d1
    bsr     spi_transfer
    tst.b   d1
    bne     .error
    dbra    d2,.read_r7
    cmpi.b  #$aa,d0
    bne     .error

    ; Repeated CMD55 + ACMD41(HCS) until the card leaves idle state.
    move.b  #'I',d0
    bsr     put_chr
    move.w  #$03ff,d4
.acmd41:
    lea     cmd55,a0
    bsr     sd_command
    tst.b   d1
    bne     .error
    cmpi.b  #$01,d0
    bne     .error

    lea     acmd41,a0
    bsr     sd_command
    tst.b   d1
    bne     .error
    beq     .success
    cmpi.b  #$01,d0
    bne     .error
    dbra    d4,.acmd41
    bra     .error
.error:
    moveq   #1,d1
    bra     .done
.success:
    moveq   #0,d1
.done:
    movem.l (sp)+,d2-d4/a0
    rts

; ---------------------------------------------------------------------------
; sd_read_lba0
; Output: d1.b = 0 success, 1 failure
; ---------------------------------------------------------------------------
sd_read_lba0:
    movem.l d2-d4/a0-a1,-(sp)

    lea     cmd17_lba0,a0
    bsr     sd_command
    tst.b   d1
    bne     .error
    tst.b   d0                       ; CMD17 expected R1 = $00
    bne     .error

    ; Wait for the data-start token, with a finite timeout.
    move.w  #$ffff,d4
.wait_token:
    move.b  #$ff,d0
    moveq   #SPI_START_SELECTED,d1
    bsr     spi_transfer
    tst.b   d1
    bne     .error
    cmpi.b  #$fe,d0
    beq     .token_received
    dbra    d4,.wait_token
    bra     .error

.token_received:
    lea     sector_buffer,a1
    move.w  #511,d3
.read_byte:
    move.b  #$ff,d0
    moveq   #SPI_START_SELECTED,d1
    bsr     spi_transfer
    tst.b   d1
    bne     .error
    move.b  d0,(a1)+
    dbra    d3,.read_byte

    ; Consume the two CRC bytes. CRC is not checked in this smoke test.
    moveq   #1,d2
.read_crc:
    move.b  #$ff,d0
    moveq   #SPI_START_SELECTED,d1
    bsr     spi_transfer
    tst.b   d1
    bne     .error
    dbra    d2,.read_crc

    moveq   #0,d1
    bra     .finish
.error:
    moveq   #1,d1
.finish:
    ; CMD17 is complete; release CS regardless of the outcome.
    moveq   #1,d0
    bsr     spi_set_cs
    movem.l (sp)+,d2-d4/a0-a1
    rts

; ---------------------------------------------------------------------------
; sd_command -- send the six bytes at a0, then return its R1 response.
; Output: d0.b = R1, d1.b = 0 success / 1 timeout
; ---------------------------------------------------------------------------
sd_command:
    movem.l d2/a0,-(sp)
    moveq   #5,d2
.send:
    move.b  (a0)+,d0
    moveq   #SPI_START_SELECTED,d1
    bsr     spi_transfer
    tst.b   d1
    bne     .done
    dbra    d2,.send
    bsr     sd_wait_r1
.done:
    movem.l (sp)+,d2/a0
    rts

; ---------------------------------------------------------------------------
; sd_wait_r1 -- clock until the card supplies a non-$FF R1 byte.
; Output: d0.b = R1, d1.b = 0 success / 1 timeout
; ---------------------------------------------------------------------------
sd_wait_r1:
    movem.l d2,-(sp)
    move.w  #$ffff,d2
.wait:
    move.b  #$ff,d0
    moveq   #SPI_START_SELECTED,d1
    bsr     spi_transfer
    tst.b   d1
    bne     .done
    cmpi.b  #$ff,d0
    bne     .done
    dbra    d2,.wait
    moveq   #1,d1
.done:
    movem.l (sp)+,d2
    rts

; ---------------------------------------------------------------------------
; spi_transfer
; Inputs: d0.b = byte to transmit; d1.b = command value ($01 or $03).
; Output: d0.b = received byte; d1.b = 0 success / 1 timeout.
; ---------------------------------------------------------------------------
spi_transfer:
    movem.l d2,-(sp)
    move.b  d0,d2
    bsr     spi_wait_ready
    tst.b   d0
    bne     .timeout
    move.b  d2,SPI_DTLW
    move.b  d1,SPI_CDST
    bsr     spi_wait_ready
    tst.b   d0
    bne     .timeout
    move.b  SPI_DTLW,d0
    moveq   #0,d1
    bra     .done
.timeout:
    moveq   #1,d1
.done:
    movem.l (sp)+,d2
    rts

; ---------------------------------------------------------------------------
; spi_wait_ready -- wait until status BUSY (bit 0) is clear.
; Output: d0.b = 0 ready / 1 timeout.
; ---------------------------------------------------------------------------
spi_wait_ready:
    movem.l d1-d2,-(sp)
    move.w  #$ffff,d2
.wait:
    move.b  SPI_CDST,d1
    btst    #0,d1
    beq     .ready
    dbra    d2,.wait
    moveq   #1,d0
    bra     .done
.ready:
    moveq   #0,d0
.done:
    movem.l (sp)+,d1-d2
    rts

; ---------------------------------------------------------------------------
; spi_set_cs
; Input: d0.b = 0 select device 0, nonzero deselect all devices.
; Output: d0.b = 0 ready / 1 timeout.
; ---------------------------------------------------------------------------
spi_set_cs:
    tst.b   d0
    bne     .deselect
    move.b  #SPI_SELECT_DEVICE0,SPI_CDST
    bra     spi_wait_ready
.deselect:
    move.b  #SPI_DESELECT_ALL,SPI_CDST
    bra     spi_wait_ready

; Shared spi_master registers: byte accesses must use the odd address.
SPI_BASE                 equ     $00f20000
SPI_DTLW                 equ     SPI_BASE+$1
SPI_DTHI                 equ     SPI_BASE+$3
SPI_CDST                 equ     SPI_BASE+$5
SPI_CONF                 equ     SPI_BASE+$7

SPI_DESELECT_ALL         equ     $00
SPI_SELECT_DEVICE0       equ     $02
SPI_START_DESELECTED     equ     $01
SPI_START_SELECTED       equ     $03
SPI_CONFIG_SLOW          equ     $0e    ; 8-bit, clk/128 (195.3125 kHz)
SPI_CONFIG_FAST          equ     $08    ; 8-bit, clk/2

cmd0:        dc.b    $40,$00,$00,$00,$00,$95
cmd8:        dc.b    $48,$00,$00,$01,$aa,$87
cmd55:       dc.b    $77,$00,$00,$00,$00,$ff
acmd41:      dc.b    $69,$40,$00,$00,$00,$ff
cmd17_lba0:  dc.b    $51,$00,$00,$00,$00,$ff

msg_banner:          dc.b    'SD SPI smoke test',LF,NUL
msg_init:            dc.b    'Initializing card... ',NUL
msg_read:            dc.b    'Reading LBA 0... ',NUL
msg_ok:              dc.b    'OK: MBR signature $55AA',LF,NUL
msg_timeout:         dc.b    'SPI timeout',LF,NUL
msg_init_fail:       dc.b    'SD initialization failed',LF,NUL
msg_read_fail:       dc.b    'CMD17/read failed',LF,NUL
msg_signature_fail:  dc.b    'Read completed, but MBR signature is not $55AA',LF,NUL

    include '../../lib/asm/mem_map_leds.asm'
    include '../../lib/asm/console_io_uart.asm'

    section .bss
sector_buffer: ds.b 512
