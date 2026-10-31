; Print the RTC's date/time over the monitor UART, then return with TRAP #14.
; Build: make target/app/rtc_get.bin
    section .text, code

start:
    move.w  sr,-(sp)
    ori.w   #$0700,sr
    lea     datetime,a1
    bsr     rtc_read_datetime
    tst.l   d0
    bne     .error
    bsr     rtc_print_datetime
    tst.b   d5
    beq     .done
    lea     msg_halted,a0
    bsr     put_str
    bra     .done
.error:
    lea     msg_invalid,a0
    cmpi.l  #2,d0
    bne     .print_error
    lea     msg_unstable,a0
.print_error:
    bsr     put_str
.done:
    move.w  (sp)+,sr
    trap    #14

    include '../../lib/asm/rtc_datetime.asm'
    include '../../lib/asm/console_io_uart.asm'

msg_halted:
    dc.b    'Clock halted (CH=1). Run rtc_set to start it.',CR,LF,NUL
msg_invalid:
    dc.b    'RTC date/time invalid or unsupported. Run rtc_set (2000-2099, 24-hour).',CR,LF,NUL
msg_unstable:
    dc.b    'Could not read a stable RTC date/time.',CR,LF,NUL

    section .bss
datetime:
    ds.b    DT_SIZE
