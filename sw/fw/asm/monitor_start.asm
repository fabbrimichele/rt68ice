; Minimal assembly boundary for the C monitor.
; C owns monitor behaviour; this file only provides reset/exception entry and
; performs the non-returning jump requested by the RUN command.

    xdef    monitor_reset
    xdef    monitor_trap14_entry
    xdef    monitor_bus_error_entry
    xdef    monitor_jump

    xref    _stack_top
    xref    monitor_main
    xref    monitor_bus_error

    section .text,code

    dc.l    _stack_top
    dc.l    monitor_reset

monitor_reset:
    move.w  #$2700,sr       ; Keep all IRQs masked until C quiesces devices.
    jmp     monitor_main

; trap #14 is the application-to-monitor hand-off.  Discard its exception
; frame and start with a clean monitor stack and interrupt mask.
monitor_trap14_entry:
    move.w  #$2700,sr
    move.l  #_stack_top,sp
    jmp     monitor_main

; The C handler reinitialises UART before emitting the error message.
monitor_bus_error_entry:
    move.w  #$2700,sr
    move.l  #_stack_top,sp
    jmp     monitor_bus_error

; C calls this with a 32-bit address after the return address on the stack.
; Preserve the old monitor's JMP semantics: applications do not return here.
monitor_jump:
    move.l  4(sp),a0
    jmp     (a0)
