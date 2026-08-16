; ---------------------------------------------------------------------------
; USB Controller Memory Map
; All registers are word-aligned (16-bit access) and values are in bits 15-0.
; Host blocks reserve 16 words each so new HID device types can be added
; without changing the map.
; ---------------------------------------------------------------------------

; --- Global interrupt registers ---
USB_IRQ_STATUS  equ $00024000   ; Read-only pending bits: bit 0 Host 1, bit 1 Host 2. Does not acknowledge either host.
USB_IRQ_ENABLE  equ $00024002   ; Read/write mask bits: bit 0 Host 1, bit 1 Host 2. Reset value $0000 disables USB CPU interrupts.

; --- USB Host 1 (word offsets 8-23; registers 13-23 reserved) ---
USB1_STATUS     equ $00024010   ; Bit 7: conErr (1=Error). Bits 1-0: type (0=None, 1=KB, 2=Mouse, 3=Pad).
USB1_MOUSE_BTN  equ $00024012   ; Bits 2-0: middle, right, left buttons.
USB1_MOUSE_DX   equ $00024014   ; Signed 16-bit X accumulator.
USB1_MOUSE_DY   equ $00024016   ; Signed 16-bit Y accumulator.
USB1_GAMEPAD    equ $00024018   ; Bits 9-0: U, D, L, R, A, B, X, Y, Start, Select.

; --- USB Host 2 (word offsets 24-39; registers 29-39 reserved) ---
USB2_STATUS     equ $00024030   ; Bit 7: conErr (1=Error). Bits 1-0: type (0=None, 1=KB, 2=Mouse, 3=Pad).
USB2_MOUSE_BTN  equ $00024032   ; Bits 2-0: middle, right, left buttons.
USB2_MOUSE_DX   equ $00024034   ; Signed 16-bit X accumulator.
USB2_MOUSE_DY   equ $00024036   ; Signed 16-bit Y accumulator.
USB2_GAMEPAD    equ $00024038   ; Bits 9-0: U, D, L, R, A, B, X, Y, Start, Select.
