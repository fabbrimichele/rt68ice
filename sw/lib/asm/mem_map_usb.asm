; ---------------------------------------------------------------------------
; USB Controller Memory Map
; All registers are word-aligned (16-bit access) and values are in bits 15-0.
; Host blocks reserve 16 words each so new HID device types can be added
; without changing the map.
; ---------------------------------------------------------------------------

; --- Global interrupt registers ---
USB_IRQ_STATUS  equ $02118000   ; Read-only pending bits: bit 0 Host 1, bit 1 Host 2, bit 2 Host 3, bit 3 Host 4. Does not acknowledge a host.
USB_IRQ_ENABLE  equ $02118002   ; Read/write mask bits: bit 0 Host 1, bit 1 Host 2, bit 2 Host 3, bit 3 Host 4. Reset value $0000 disables USB CPU interrupts.

; --- USB Host 1 (word offsets 8-23; registers 18-23 reserved) ---
USB1_STATUS     equ $02118010   ; Read acknowledges Host 1. Bit 7: conErr (1=Error). Bits 1-0: type (0=None, 1=KB, 2=Mouse, 3=Pad).
USB1_MOUSE_BTN  equ $02118012   ; Bits 2-0: middle, right, left buttons.
USB1_MOUSE_DX   equ $02118014   ; Signed 16-bit X accumulator.
USB1_MOUSE_DY   equ $02118016   ; Signed 16-bit Y accumulator.
USB1_GAMEPAD    equ $02118018   ; Bits 9-0: U, D, L, R, A, B, X, Y, Start, Select.
USB1_KEY_MODS   equ $0211801A   ; USB HID modifier bitmap: bits 7-0 are RGUI, RALT, RSHIFT, RCTRL, LGUI, LALT, LSHIFT, LCTRL.
USB1_KEY1       equ $0211801C   ; First USB HID boot-keyboard usage ID; zero means no key.
USB1_KEY2       equ $0211801E   ; Second USB HID boot-keyboard usage ID; zero means no key.
USB1_KEY3       equ $02118020   ; Third USB HID boot-keyboard usage ID; zero means no key.
USB1_KEY4       equ $02118022   ; Fourth USB HID boot-keyboard usage ID; zero means no key.

; --- USB Host 2 (word offsets 24-39; registers 34-39 reserved) ---
USB2_STATUS     equ $02118030   ; Read acknowledges Host 2. Bit 7: conErr (1=Error). Bits 1-0: type (0=None, 1=KB, 2=Mouse, 3=Pad).
USB2_MOUSE_BTN  equ $02118032   ; Bits 2-0: middle, right, left buttons.
USB2_MOUSE_DX   equ $02118034   ; Signed 16-bit X accumulator.
USB2_MOUSE_DY   equ $02118036   ; Signed 16-bit Y accumulator.
USB2_GAMEPAD    equ $02118038   ; Bits 9-0: U, D, L, R, A, B, X, Y, Start, Select.
USB2_KEY_MODS   equ $0211803A   ; USB HID modifier bitmap: bits 7-0 are RGUI, RALT, RSHIFT, RCTRL, LGUI, LALT, LSHIFT, LCTRL.
USB2_KEY1       equ $0211803C   ; First USB HID boot-keyboard usage ID; zero means no key.
USB2_KEY2       equ $0211803E   ; Second USB HID boot-keyboard usage ID; zero means no key.
USB2_KEY3       equ $02118040   ; Third USB HID boot-keyboard usage ID; zero means no key.
USB2_KEY4       equ $02118042   ; Fourth USB HID boot-keyboard usage ID; zero means no key.

; --- USB Host 3 (word offsets 40-55; registers 50-55 reserved) ---
USB3_STATUS     equ $02118050   ; Read acknowledges Host 3. Bit 7: conErr (1=Error). Bits 1-0: type (0=None, 1=KB, 2=Mouse, 3=Pad).
USB3_MOUSE_BTN  equ $02118052   ; Bits 2-0: middle, right, left buttons.
USB3_MOUSE_DX   equ $02118054   ; Signed 16-bit X accumulator.
USB3_MOUSE_DY   equ $02118056   ; Signed 16-bit Y accumulator.
USB3_GAMEPAD    equ $02118058   ; Bits 9-0: U, D, L, R, A, B, X, Y, Start, Select.
USB3_KEY_MODS   equ $0211805A   ; USB HID modifier bitmap: bits 7-0 are RGUI, RALT, RSHIFT, RCTRL, LGUI, LALT, LSHIFT, LCTRL.
USB3_KEY1       equ $0211805C   ; First USB HID boot-keyboard usage ID; zero means no key.
USB3_KEY2       equ $0211805E   ; Second USB HID boot-keyboard usage ID; zero means no key.
USB3_KEY3       equ $02118060   ; Third USB HID boot-keyboard usage ID; zero means no key.
USB3_KEY4       equ $02118062   ; Fourth USB HID boot-keyboard usage ID; zero means no key.

; --- USB Host 4 (word offsets 56-71; registers 66-71 reserved) ---
USB4_STATUS     equ $02118070   ; Read acknowledges Host 4. Bit 7: conErr (1=Error). Bits 1-0: type (0=None, 1=KB, 2=Mouse, 3=Pad).
USB4_MOUSE_BTN  equ $02118072   ; Bits 2-0: middle, right, left buttons.
USB4_MOUSE_DX   equ $02118074   ; Signed 16-bit X accumulator.
USB4_MOUSE_DY   equ $02118076   ; Signed 16-bit Y accumulator.
USB4_GAMEPAD    equ $02118078   ; Bits 9-0: U, D, L, R, A, B, X, Y, Start, Select.
USB4_KEY_MODS   equ $0211807A   ; USB HID modifier bitmap: bits 7-0 are RGUI, RALT, RSHIFT, RCTRL, LGUI, LALT, LSHIFT, LCTRL.
USB4_KEY1       equ $0211807C   ; First USB HID boot-keyboard usage ID; zero means no key.
USB4_KEY2       equ $0211807E   ; Second USB HID boot-keyboard usage ID; zero means no key.
USB4_KEY3       equ $02118080   ; Third USB HID boot-keyboard usage ID; zero means no key.
USB4_KEY4       equ $02118082   ; Fourth USB HID boot-keyboard usage ID; zero means no key.
