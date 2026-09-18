## Resources:
- [Compact USB HID host FPGA core (nand2mario)](https://github.com/nand2mario/usb_hid_host)

## Keyboard
The component treats a keyboard as a USB HID “boot keyboard.” It does not translate keys into Atari scancodes; it exposes the current USB HID key state in
registers for your firmware to translate.

The flow is:

USB keyboard → USB HID boot report → key_modifiers/key1..key4 → report pulse → CPU interrupt/register reads

When enumeration identifies the interface as:

- HID class (`bInterfaceClass == 3`)
- boot subclass (`bInterfaceSubClass == 1`)
- keyboard protocol (`bInterfaceProtocol == 1`)

it sets `typ` to `1`, meaning keyboard.

For every subsequently received keyboard report, this code decodes these bytes:

```
case (rcvct)
0: key_modifiers <= ukpdat;
2: key1 <= ukpdat;
3: key2 <= ukpdat;
4: key3 <= ukpdat;
5: key4 <= ukpdat;
endcase
```

That corresponds to the standard eight-byte USB HID boot-keyboard input report:

| Report byte | Meaning                  | Component output |
|-------------|--------------------------|------------------|
| 0           | Modifier bitmask         | key_modifiers    |
| 1           | Reserved                 | ignored          |
| 2           | First pressed key usage  | key1             |
| 3           | Second pressed key usage | key2             |
| 4           | Third pressed key usage  | key3             |
| 5           | Fourth pressed key usage | key4             |
| 6           | Fifth pressed key usage  | ignored          |
| 7           | Sixth pressed key usage  | ignored          |

`key1` through `key4` contain USB HID usage IDs, not ASCII and not Atari scancodes. For example, USB HID 0x04 is A, 0x05 is B, etc. A zero slot means no key
occupies that position.

`key_modifiers` is a bitmap:

| Bit   | Modifier   | Bit   | Modifier    |
|-------|------------|-------|-------------|
| bit 0 | left Ctrl  | bit 4 | right Ctrl  |
| bit 1 | left Shift | bit 5 | right Shift |
| bit 2 | left Alt   | bit 6 | right Alt   |
| bit 3 | left GUI   | bit 7 | right GUI   |

Once the entire USB packet has been received (`data_rdy` falls), it pulses `report` for one 12 MHz clock. At that moment the `key_*` outputs represent one
complete keyboard state snapshot. The surrounding RT68ICE logic must latch that pulse into an interrupt-pending condition; a 12 MHz one-clock pulse is far
too short for software to observe directly.

Important limitations of this implementation:

- It only supports the fixed HID boot protocol, not arbitrary HID report descriptors.
- It retains only four simultaneous non-modifier keys, although the boot protocol provides six.
- It emits state snapshots, not key-down/key-up events. EmuTOS must compare this report with the prior one to generate Atari make/break scancodes.
- On disconnect it clears `typ` to zero, but does not explicitly clear `key_modifiers` or `key1..key4`. Software should treat values as invalid whenever the
  status/type says no keyboard is present.

### Note for EmuTOS
So in `rt68ice_usb_key_int()`, the usual design is: read `USB1_KEY_MODS` and `USB1_KEY1..USB1_KEY4`, compare them with saved prior values, and send the needed
Atari key press/release scancodes into `push_ikbdiorec()`.
