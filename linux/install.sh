#!/usr/bin/env bash
set -euo pipefail

INSTALL_DIR="$HOME/.local/bin"

mkdir -p "$INSTALL_DIR"
cp "$(dirname "$0")/proxi" "$INSTALL_DIR/proxi"
chmod +x "$INSTALL_DIR/proxi"

echo "Installed proxi to $INSTALL_DIR/proxi"

case ":$PATH:" in
  *":$INSTALL_DIR:"*) ;;
  *)
    echo
    echo "Add this to your shell config:"
    echo "export PATH=\"\$HOME/.local/bin:\$PATH\""
    ;;
esac

echo
echo "Check dependencies:"
for cmd in adb curl dig dnscrypt-proxy redsocks iptables ip ss; do
  if command -v "$cmd" >/dev/null 2>&1; then
    echo "OK: $cmd"
  else
    echo "MISSING: $cmd"
  fi
done

echo
echo "Arch/EndeavourOS dependencies:"
echo "sudo pacman -S android-tools dnscrypt-proxy bind iptables"
echo "paru -S redsocks"
