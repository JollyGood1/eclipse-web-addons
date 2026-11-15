#!/usr/bin/env bash
# Starts Eclipse on a virtual screen that a web browser on the host can show and control (noVNC).
# Run the container with  -p 127.0.0.1:6080:6080  and open  http://localhost:6080/vnc.html?autoconnect=1&resize=scale
# There is no password: publish the port on 127.0.0.1 only, as above.
set -u
SITE=${SITE:-https://jollygood1.github.io/eclipse-web-addons/}
ECLIPSE=/opt/eclipse
export DISPLAY=:1

Xvfb :1 -screen 0 "${SCREEN:-1600x900x24}" >/dev/null 2>&1 &
sleep 1
openbox >/dev/null 2>&1 &
x11vnc -display :1 -forever -shared -nopw -quiet -bg >/dev/null 2>&1
websockify --web /usr/share/novnc 6080 localhost:5900 >/dev/null 2>&1 &

if ! grep -q jollygood1 "$ECLIPSE/configuration/org.eclipse.equinox.simpleconfigurator/bundles.info"; then
    echo "Installing the plug-ins from $SITE"
    "$ECLIPSE/eclipse" -nosplash -application org.eclipse.equinox.p2.director -repository "$SITE" \
        -installIU io.github.jollygood1.syntaxthemes.feature.feature.group,io.github.jollygood1.fileicons.feature.feature.group \
        2>/dev/null | grep -E 'nstall|Operation|annot'
fi

echo "Open http://localhost:6080/vnc.html?autoconnect=1&resize=scale in a browser. Closing Eclipse stops the container."
exec "$ECLIPSE/eclipse" -data /workspace
