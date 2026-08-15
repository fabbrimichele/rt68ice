; ---------------------------------------------------------------------------
; USB Controller Memory Map
; All registers are word-aligned (16-bit access).
; For 8-bit values, data is assumed to be on the lower byte (bits 7-0).
; ---------------------------------------------------------------------------

; --- USB Port 1 ---
USB1_STATUS     equ $00024000   ; 16-bit. Bit 7: conErr (1=Error). Bits 1-0: Device Type (0=None, 1=KB, 2=Mouse, 3=Pad).
USB1_MOUSE_BTN  equ $00024002   ; 16-bit. Mouse buttons. Bit 0: Left, Bit 1: Right, Bit 2: Middle.
USB1_MOUSE_DX   equ $00024004   ; 16-bit. X-axis delta. Lower 8 bits contain 2's complement signed accumulator.
USB1_MOUSE_DY   equ $00024006   ; 16-bit. Y-axis delta. Lower 8 bits contain 2's complement signed accumulator.
USB1_GAMEPAD    equ $00024008   ; 16-bit. Packed inputs. Bits 9-0: U, D, L, R, A, B, X, Y, Start, Select.
USB_IRQ_STATUS  equ $0002400A   ; 16-bit, read-only and non-clearing. Bit 0: USB1 pending. Bit 1: USB2 pending.
USB1_MOUSE_RAW_DX equ $0002400C ; 16-bit. Lower 8 bits: signed X delta from the most recent mouse report.
USB1_MOUSE_RAW_DY equ $0002400E ; 16-bit. Lower 8 bits: signed Y delta from the most recent mouse report.

; --- USB Port 2 ---
USB2_STATUS     equ $00024010   ; 16-bit. Bit 7: conErr (1=Error). Bits 1-0: Device Type (0=None, 1=KB, 2=Mouse, 3=Pad).
USB2_MOUSE_BTN  equ $00024012   ; 16-bit. Mouse buttons. Bit 0: Left, Bit 1: Right, Bit 2: Middle.
USB2_MOUSE_DX   equ $00024014   ; 16-bit. X-axis delta. Lower 8 bits contain 2's complement signed accumulator.
USB2_MOUSE_DY   equ $00024016   ; 16-bit. Y-axis delta. Lower 8 bits contain 2's complement signed accumulator.
USB2_GAMEPAD    equ $00024018   ; 16-bit. Packed inputs. Bits 9-0: U, D, L, R, A, B, X, Y, Start, Select.
USB2_HID_REPORT_01 equ $0002401A ; 16-bit. Last unparsed report: byte 1 in bits 15-8, byte 0 in bits 7-0.
USB2_HID_REPORT_23 equ $0002401C ; 16-bit. Last unparsed report: byte 3 in bits 15-8, byte 2 in bits 7-0.
USB2_MOUSE_RAW     equ $0002401E ; 16-bit. Last parsed report: signed DY in bits 15-8, signed DX in bits 7-0.
