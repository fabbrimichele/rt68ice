; Locate RTC stalls on hardware without changing the FPGA or shared reader.
; Build: make target/app/rtc_probe.bin
    section .text, code

; This works but rtc_get.asm doesn't, it gets stuck

start:
    move.w  sr,-(sp)
    ori.w   #$0700,sr
    lea     msg_spaced,a0
    bsr     put_str
    movea.l #RTC_SECONDS,a1
    lea     msg_address_loaded,a0
    bsr     put_str
    moveq   #7,d6
.read:
    move.l  a1,d0
    bsr     bin_to_hex
    moveq   #':',d0
    bsr     put_chr
    moveq   #' ',d0
    bsr     put_chr
    ; Exactly the monitor's register-read instruction.
    move.w  (a1)+,d0
    bsr     bin_to_hex_w
    bsr     newline
    dbra    d6,.read

    lea     msg_tight,a0
    bsr     put_str
    movea.l #RTC_SECONDS,a1
    ; No UART output or snapshot writes between these eight reads.
    move.w  (a1)+,d0
    move.w  (a1)+,d1
    move.w  (a1)+,d2
    move.w  (a1)+,d3
    move.w  (a1)+,d4
    move.w  (a1)+,d5
    move.w  (a1)+,d6
    move.w  (a1)+,d7
    lea     msg_done,a0
    bsr     put_str
    move.w  (sp)+,sr
    trap    #14

newline:
    moveq   #CR,d0
    bsr     put_chr
    moveq   #LF,d0
    bra     put_chr

    include '../../lib/asm/conv_hex.asm'
    include '../../lib/asm/console_io_uart.asm'
    include '../../lib/asm/mem_map_ds1302.asm'

msg_spaced:
    dc.b    'RTC probe v3: before immediate RTC address load',CR,LF,NUL
msg_address_loaded:
    dc.b    'RTC address loaded; starting spaced reads and address formatting',CR,LF,NUL
msg_tight:
    dc.b    'RTC probe: consecutive word reads...',CR,LF,NUL
msg_done:
    dc.b    'RTC probe: all reads completed.',CR,LF,NUL
