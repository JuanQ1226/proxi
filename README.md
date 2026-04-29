# Proxi

Proxi is a USB Android-to-Linux proxy utility for TCP-focused internet access through an Android device.

It includes:

- An Android app that runs local SOCKS5 and HTTP CONNECT proxy servers
- A Linux CLI that can use those proxies directly
- A Linux transparent mode that routes normal TCP app traffic through the phone

## Version

`v0.1.0-alpha`

## Architecture

```text
Linux apps
  → iptables
  → redsocks
  → ADB forward
  → Android Proxi
  → phone internet

DNS
  → dnscrypt-proxy
  → Proxi HTTP proxy
  → Android Proxi
  → phone internet
```

## Features

- Android foreground proxy service
- SOCKS5 proxy on port `1080`
- HTTP CONNECT proxy on port `8080`
- Linux explicit proxy mode
- Linux transparent TCP mode
- DNS through `dnscrypt-proxy`
- `doctor`, `repair`, `status`, and `logs` commands
- Android dashboard with proxy status and traffic stats

## Requirements

### Android

- Android phone with USB debugging enabled
- Proxi Android app installed
- USB cable

### Linux

Tested target environment:

- Arch Linux / EndeavourOS / Omarchy

Required tools:

```bash
sudo pacman -S android-tools dnscrypt-proxy bind iptables
paru -S redsocks
```

If you do not have `paru`:

```bash
sudo pacman -S --needed base-devel git
cd /tmp
git clone https://aur.archlinux.org/paru.git
cd paru
makepkg -si
```

## Android Setup

1. Install and open the Proxi Android app.
2. Tap **Start Proxy**.
3. Keep the phone connected by USB.

The Android app exposes:

```text
SOCKS5:       127.0.0.1:1080
HTTP CONNECT: 127.0.0.1:8080
```

These are accessed from Linux through ADB port forwarding.

## Linux Setup

Install the Linux client:

```bash
./linux/install.sh
```

Then verify:

```bash
proxi doctor
```

## Normal Workflow

### 1. Connect phone by USB

Check that ADB sees the device:

```bash
adb devices
```

Expected:

```text
<device-id>    device
```

### 2. Open Proxi on Android

```bash
proxi open-android
```

Tap **Start Proxy** on the phone.

### 3. Start transparent mode

```bash
proxi transparent-start
```

Expected output:

```text
Transparent mode is working.
```

### 4. Use TCP-based internet normally

Examples:

```bash
curl -4 https://ifconfig.me
git ls-remote https://github.com/git/git.git | head
npm view react version
python -m pip index versions requests | head
```

### 5. Stop and restore networking

```bash
proxi transparent-stop
```

## Emergency Repair

If networking gets stuck or something fails halfway:

```bash
proxi repair
```

This attempts to restore:

- `iptables` rules
- `redsocks`
- dummy route
- `/etc/resolv.conf`
- ADB forwards

## Explicit Proxy Mode

Transparent mode is the PdaNet-like workflow, but explicit proxy mode is also available.

Start ADB forwards:

```bash
proxi start
```

Apply proxy variables in the current shell:

```bash
eval "$(proxi env)"
```

Test:

```bash
curl https://ifconfig.me
```

Stop explicit proxy mode:

```bash
eval "$(proxi unset)"
proxi stop
```

## Commands

```bash
proxi start
proxi stop
proxi status
proxi test
proxi env
proxi unset

proxi transparent-start
proxi transparent-stop
proxi transparent-restart
proxi transparent-status

proxi doctor
proxi repair
proxi open-android
proxi logs
```

## Firefox Notes

In transparent mode, Firefox should use:

```text
Network Settings → No proxy
```

Recommended `about:config` values for the current MVP:

```text
network.dns.disableIPv6 = true
network.http.http3.enable = false
network.trr.mode = 5
```

Then restart Firefox.

## Known Limitations

This MVP is TCP-focused.

Expected to work:

- Web browsing over TCP
- `curl`
- `wget`
- Git over HTTPS
- `npm` / `pnpm`
- `pip`
- many API-based apps
- many Electron apps

May not work or may be unreliable:

- UDP traffic
- ICMP / `ping`
- HTTP/3 / QUIC
- IPv6-heavy apps
- online games
- Discord voice
- Zoom / Google Meet media streams
- WebRTC
- some Docker or root-owned services
- some Flatpak apps

## Troubleshooting

Start with:

```bash
proxi doctor
```

Check current transparent mode state:

```bash
proxi transparent-status
```

Force repair:

```bash
proxi repair
```

Check Android logs:

```bash
proxi logs
```

Check explicit proxy path:

```bash
proxi test
```

Check DNS:

```bash
dig @127.0.0.1 ifconfig.me
cat /etc/resolv.conf
sudo systemctl status dnscrypt-proxy
```

Check routes and iptables:

```bash
ip route
sudo iptables -t nat -S OUTPUT
sudo iptables -t nat -S PROXI
```

## Safety and Usage Disclaimer

Use Proxi only with networks, devices, and plans where you are authorized to route or share traffic.

Do not market or use this project as a way to bypass carrier policies, network restrictions, or service terms.

Proxi is intended as a technical USB proxy utility for development, testing, and authorized personal device routing.
