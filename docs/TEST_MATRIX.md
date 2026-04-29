# Proxi Test Matrix

Use this checklist before tagging a release.

## Environment

- [ ] Android phone connected over USB
- [ ] USB debugging enabled
- [ ] Android Proxi app installed
- [ ] Linux CLI installed as `proxi`
- [ ] WiFi off test completed
- [ ] WiFi on test completed

## Android App

- [ ] App opens without crashing
- [ ] Start Proxy button starts foreground service
- [ ] Stop Proxy button stops foreground service
- [ ] Foreground notification appears while running
- [ ] Notification Stop action stops the proxy
- [ ] UI status changes between Running and Stopped
- [ ] SOCKS5 port shows `1080`
- [ ] HTTP port shows `8080`
- [ ] Stats update after traffic
- [ ] Reset Stats clears counters
- [ ] Setup action opens Linux setup dialog

## Explicit Proxy Mode

Start Android Proxi first, then:

```bash
proxi repair
proxi start
proxi test
```

- [ ] `proxi start` creates ADB forward for `1080`
- [ ] `proxi start` creates ADB forward for `8080`
- [ ] `curl -x http://127.0.0.1:8080 https://ifconfig.me` works
- [ ] `curl --socks5-hostname 127.0.0.1:1080 https://ifconfig.me` works
- [ ] `proxi test` reports HTTP proxy works
- [ ] `proxi test` reports SOCKS5 proxy works
- [ ] `proxi stop` removes ADB forwards

## Transparent Mode

Start Android Proxi first, then:

```bash
proxi repair
proxi transparent-start
proxi doctor
```

- [ ] `proxi transparent-start` completes successfully
- [ ] `proxi doctor` reports all OK
- [ ] ADB forwards are active
- [ ] `dnscrypt-proxy` is active
- [ ] `redsocks` is running
- [ ] dummy route `proxi-dummy` exists
- [ ] `/etc/resolv.conf` points to `127.0.0.1`
- [ ] `iptables` PROXI chain exists
- [ ] transparent curl works

Core tests:

```bash
curl -4 https://ifconfig.me
git ls-remote https://github.com/git/git.git | head
npm view react version
python -m pip index versions requests | head
```

- [ ] `curl -4 https://ifconfig.me` works
- [ ] `git ls-remote` works
- [ ] `npm view react version` works
- [ ] `pip index versions requests` works

## Browser Tests

Firefox transparent mode settings:

```text
Network Settings: No proxy
network.dns.disableIPv6 = true
network.http.http3.enable = false
network.trr.mode = 5
```

- [ ] Firefox opens `https://ifconfig.me`
- [ ] Firefox opens `https://github.com`
- [ ] Firefox opens `https://archlinux.org`
- [ ] Firefox still works after restart
- [ ] Firefox fails gracefully or clearly if HTTP/3/IPv6 settings are not changed

Optional Chromium test:

```bash
chromium --disable-quic --disable-ipv6
```

- [ ] Chromium opens `https://ifconfig.me`
- [ ] Chromium opens `https://github.com`

## WiFi Off Test

Turn WiFi off on the Linux machine. Keep Android connected by USB and Proxi running.

```bash
proxi repair
proxi transparent-start
proxi doctor
curl -4 https://ifconfig.me
```

- [ ] Explicit HTTP proxy works with WiFi off
- [ ] Local DNS works with WiFi off
- [ ] Transparent mode works with WiFi off
- [ ] `curl -4 https://ifconfig.me` works with WiFi off
- [ ] Browser works with WiFi off

## Stop / Restore Tests

```bash
proxi transparent-stop
```

- [ ] ADB forwards are removed
- [ ] `redsocks` is stopped
- [ ] dummy route is removed
- [ ] `iptables` PROXI chain is removed
- [ ] `/etc/resolv.conf` is restored
- [ ] Normal WiFi internet works again after reconnecting

Check:

```bash
adb forward --list
pgrep redsocks
ip route
cat /etc/resolv.conf
sudo iptables -t nat -S OUTPUT
```

## Repair Tests

Simulate a messy state by starting transparent mode, then run:

```bash
proxi repair
```

- [ ] `proxi repair` completes without error
- [ ] ADB forwards are removed
- [ ] `redsocks` is stopped
- [ ] dummy route is removed
- [ ] `iptables` PROXI chain is removed
- [ ] `/etc/resolv.conf` is restored
- [ ] System networking is not left broken

## Known Expected Failures

These are not v1 blockers:

- [ ] `ping` may fail
- [ ] UDP apps may fail
- [ ] HTTP/3 / QUIC may fail
- [ ] Discord voice may fail
- [ ] Zoom / Google Meet media may fail
- [ ] Online games may fail
- [ ] IPv6-only tests may fail
- [ ] Docker daemon traffic may not be routed
- [ ] Some Flatpak apps may not use the route correctly

## Release Criteria

v0.1.0-alpha is shippable when:

- [ ] Android explicit proxy works
- [ ] Linux transparent TCP mode works
- [ ] DNS works through `dnscrypt-proxy`
- [ ] WiFi off test passes
- [ ] `proxi doctor` reports all OK
- [ ] `proxi transparent-stop` restores networking
- [ ] `proxi repair` restores networking
- [ ] README documents limitations clearly
