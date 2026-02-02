# Govee Control

## What?

An Android app for controlling Govee BLE smart lights locally over Bluetooth Low Energy.

## Why?

Requiring yet _another_ account for the 
[Govee Home app](https://play.google.com/store/apps/details?id=com.govee.home&hl=en_CA&pli=1) 
irked me. What's even worse is that they added a social media feature into the app, letting you ...
share pictures of your RGB lamp? Let me just turn my damn lights on without needing a password!

## How?

I use Kotlin as my daily driver, but have never created an Android app before. While I'm doing the tutorials
and reading the Android Studio docs, I needed to be able to turn my lamps on and change the 
brightness etc, ASAP. With this time-constraint, I shamelessly used Claude Code (Sonnet 4.5) to 
agentically produce nearly all of the app. With that in mind, I made sure to cite (hopefully) all of
the links it used as resources.

## Build & Install

```bash
./gradlew assembleDebug
~/Library/Android/sdk/platform-tools/adb install -r -t app/build/intermediates/apk/debug/app-debug.apk
```

## BLE Protocol References

The Govee BLE command protocol used in this project was informed by the following reverse engineering efforts:

- [BeauJBurroughs/Govee-H6127-Reverse-Engineering](https://github.com/BeauJBurroughs/Govee-H6127-Reverse-Engineering) - H6127 command structure and keepalive packet format
- [chvolkmann/govee_btled](https://github.com/chvolkmann/govee_btled) - Python BLE controller documenting the `33 05 02 RR GG BB` color command format (non-RGBIC bulbs)
- [Obi2000/Govee-H6199-Reverse-Engineering](https://github.com/Obi2000/Govee-H6199-Reverse-Engineering) - H6199 RGBIC segment color command (`33 05 0b`) with segment bitmask
- [KunaalKumar/Govee-H6072-Reverse-Engineering](https://github.com/KunaalKumar/Govee-H6072-Reverse-Engineering) - H6072 color mode switching
- [egold555/Govee-Reverse-Engineering](https://github.com/egold555/Govee-Reverse-Engineering) - Multi-model Govee protocol documentation
- [Govee BLE protocol discussion (loxforum.com)](https://www.loxforum.com/forum/faqs-tutorials-howto-s/446672-govee-ble-local-api-segmentsteuerung-szenen) - H6076 segment control and scene commands
- [Reverse Engineering Govee Smart Lights (blog.coding.kiwi)](https://blog.coding.kiwi/reverse-engineering-govee-smart-lights/) - General methodology using HCI snoop logs and Wireshark

### Command Format

All commands are 20-byte BLE packets: variable-length command data padded to 19 bytes, followed by a 1-byte XOR checksum.

| Command | Hex Template | Source |
|---|---|---|
| Power On | `33 01 01 00 ...` | [H6127 RE](https://github.com/BeauJBurroughs/Govee-H6127-Reverse-Engineering), [loxforum](https://www.loxforum.com/forum/faqs-tutorials-howto-s/446672-govee-ble-local-api-segmentsteuerung-szenen) |
| Power Off | `33 01 00 00 ...` | [H6127 RE](https://github.com/BeauJBurroughs/Govee-H6127-Reverse-Engineering), [loxforum](https://www.loxforum.com/forum/faqs-tutorials-howto-s/446672-govee-ble-local-api-segmentsteuerung-szenen) |
| Brightness | `33 04 XX 00 ...` (XX = 0x00-0xFF) | [H6127 RE](https://github.com/BeauJBurroughs/Govee-H6127-Reverse-Engineering), [loxforum](https://www.loxforum.com/forum/faqs-tutorials-howto-s/446672-govee-ble-local-api-segmentsteuerung-szenen) |
| Color (RGBIC) | `33 05 0b RR GG BB 00 00 FF 7F ...` | [H6199 RE](https://github.com/Obi2000/Govee-H6199-Reverse-Engineering), [loxforum](https://www.loxforum.com/forum/faqs-tutorials-howto-s/446672-govee-ble-local-api-segmentsteuerung-szenen) |
| Color (simple bulbs) | `33 05 02 RR GG BB 00 ...` | [govee_btled](https://github.com/chvolkmann/govee_btled), [H6127 RE](https://github.com/BeauJBurroughs/Govee-H6127-Reverse-Engineering) |
| Keepalive | `AA 01 00 ...` (sent every 2s) | [H6127 RE](https://github.com/BeauJBurroughs/Govee-H6127-Reverse-Engineering) |

### H6076 Color/Color Temperature Command

The H6076 uses `33 05 15 01` for both RGB color and color temperature, with a 7-segment bitmask at byte 12. Confirmed via BLE HCI snoop log from the official Govee app.

#### Full byte layout

```
33 05 15 01 RR GG BB KK KK ?? ?? ?? 7F 00 00 00 00 00 00 XOR
            |color | |temp|          |segment bitmask (7 segments)
```

- **Bytes 4–6** (`RR GG BB`): RGB color values
- **Bytes 7–8** (`KK KK`): Color temperature in Kelvin, big-endian 16-bit integer (range 2000–9000)
- **Byte 12** (`7F`): Segment bitmask — `0x7F` = `0111 1111` = all 7 segments

#### Color temperature behavior (from Govee LAN API docs)

Per [Govee WLAN guide](https://app-h5.govee.com/user-manual/wlan-guide), the `colorTemInKelvin` field (mapped to bytes 7–8 over BLE) behaves as follows:
- When Kelvin **!= 0**: The device converts the Kelvin value to warm/cool white LED segment levels internally, ignoring RGB values
- When Kelvin **== 0**: The device uses the raw RGB values instead

#### Snoop log examples

| Action | Hex | Notes |
|---|---|---|
| Warm white | `33 05 15 01 FF FF FF 08 98 FF 98 29 7F ...` | Kelvin bytes = `08 98` = **2200K** |
| Cool white | `33 05 15 01 FF FF FF 19 64 FF F9 FB 7F ...` | Kelvin bytes = `19 64` = **6500K** |
| Red | `33 05 15 01 FF 00 00 00 00 00 00 7F ...` | Kelvin = `00 00` = pure RGB mode |

#### Kelvin bytes and RGB mode

Sending Kelvin = `00 00` in the `15 01` color command does **not** kill the white LED segments. It simply means "use raw RGB mode" — the white segments retain whatever state they were last set to. RGB colors work fine with `00 00` in the Kelvin position.

Bytes 9–11 (`FF 98 29` / `FF F9 FB` in the examples above) are not yet fully understood — they may encode additional segment mixing or fine-tuning parameters.

#### `15 02` sub-command (segment control) — DANGER

The `33 05 15 02 XX YY ...` sub-command directly writes to the warm/cool white LED segment hardware registers. **This state is persistent** — values written here stick and affect all subsequent commands regardless of mode.

- **Byte 4** (`XX`): White segment brightness. Setting to `00` kills brightness across the entire lamp.
- **Byte 5** (`YY`): Minimal observable effect, purpose unclear.
- Setting both to `00` via `15 02` makes the lamp appear very dim (only RGB LEDs active, white segments off).
- The only way to recover is to send a non-zero value via `15 02`, or use the Govee app to reset.

**This sub-command should not be exposed to users as a slider without a minimum value guard.** The app currently uses hardcoded raw presets (Warm White / Cool White from snoop captures) instead, which use the safer `15 01` command with Kelvin bytes.

**Commands that did NOT work (for reference):**

| Sub-command | Format | Why it failed |
|---|---|---|
| `15 02` (segment control) | `33 05 15 02 XX YY 00 ...` | Directly writes to white LED segment hardware registers. State is persistent — zeroing out kills brightness for all subsequent commands. Not suitable for color temperature control. |
| `15 01` without bitmask | `33 05 15 01 RR GG BB 00 ...` | Missing `0x7F` at byte 12 — no segments addressed |
| `02` (manual) | `33 05 02 RR GG BB 00 ...` | Wrong sub-command for H6076 |
| `0b` (RGBIC bitmask) | `33 05 0b RR GG BB 00 00 FF 7F ...` | H6199 format, not compatible |
| `04` (scene) | `33 05 04 RR GG BB ...` | Interpreted RGB bytes as scene ID |
| `0a 20 03` (init) | Sent before color command | Triggered a multicolor scene |

### TODO
- [ ] Add devices via Bluetooth scanning in-app (instead of hardcoded config)
- [ ] Persist device/config changes with local storage
- [ ] Dynamically generate groups from `devices[].name` prefix (e.g. "Living Room Lamp L" and "Living Room Lamp R" → "Living Room Lamp" group)
