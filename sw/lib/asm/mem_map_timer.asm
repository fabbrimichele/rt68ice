; Programmable timer (68000 autovector interrupt level 5)
TIMER_CONTROL       equ $00F1C000   ; bit 0 enable, bit 1 auto-reload, bit 2 IRQ enable, bit 3 reload command
TIMER_STATUS        equ $00F1C002   ; bit 0 IRQ pending (write one to clear), bit 1 running
TIMER_DIVIDER_HI    equ $00F1C004   ; divider bits 31-16
TIMER_DIVIDER_LO    equ $00F1C006   ; divider bits 15-0
TIMER_RELOAD_HI     equ $00F1C008   ; reload value bits 31-16
TIMER_RELOAD_LO     equ $00F1C00A   ; reload value bits 15-0
TIMER_VALUE_HI      equ $00F1C00C   ; current value bits 31-16; latches VALUE_LO
TIMER_VALUE_LO      equ $00F1C00E   ; latched current value bits 15-0

TIMER_ENABLE        equ $0001
TIMER_AUTO_RELOAD   equ $0002
TIMER_IRQ_ENABLE    equ $0004
TIMER_RELOAD        equ $0008
TIMER_IRQ_PENDING   equ $0001
