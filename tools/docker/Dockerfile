# Ubuntu with Eclipse IDE for PHP Developers, a virtual screen and what the UI tests need.
# The plug-ins are not in the image: run-tests.sh installs them from the update site when the container runs.
#
#   docker build -t eclipse-web-addons-test tools/docker
#   docker run --rm -v "<repository folder>:/src:ro" -v "<folder for results>:/out" eclipse-web-addons-test
#
# Optional: -v "<folder with reference .prefs files>:/kit:ro" to compare what Apply writes,
#           -e SITE=<other update site address>, -e TESTS="themes java icons" to choose the tests.
#
# To use this Eclipse by hand, in a browser:
#   docker run -d --name eclipse-web-addons -p 127.0.0.1:6080:6080 eclipse-web-addons-test run-gui.sh
#   then open http://localhost:6080/vnc.html?autoconnect=1&resize=scale
FROM ubuntu:24.04

ARG ECLIPSE_RELEASE=2026-09
ARG ECLIPSE_URL=https://download.eclipse.org/technology/epp/downloads/release/${ECLIPSE_RELEASE}/R/eclipse-php-${ECLIPSE_RELEASE}-R-linux-gtk-x86_64.tar.gz

ENV DEBIAN_FRONTEND=noninteractive
RUN apt-get update && apt-get install -y --no-install-recommends \
        ca-certificates curl git python3 openjdk-21-jdk-headless \
        xvfb xauth openbox dbus-x11 libgtk-3-0t64 libxtst6 fonts-dejavu-core \
    && rm -rf /var/lib/apt/lists/*

# the download is checked against the checksum Eclipse publishes next to it
RUN curl -fsSL -o /tmp/eclipse.tar.gz "$ECLIPSE_URL" \
    && curl -fsSL -o /tmp/eclipse.sha512 "$ECLIPSE_URL.sha512" \
    && echo "$(cut -d' ' -f1 /tmp/eclipse.sha512)  /tmp/eclipse.tar.gz" | sha512sum -c - \
    && tar -xzf /tmp/eclipse.tar.gz -C /opt \
    && rm /tmp/eclipse.tar.gz /tmp/eclipse.sha512

# The PHP package has no Java tooling; the theme tests open a Java file, so add it
RUN xvfb-run -a /opt/eclipse/eclipse -nosplash -application org.eclipse.equinox.p2.director \
        -repository "https://download.eclipse.org/releases/${ECLIPSE_RELEASE}" -installIU org.eclipse.jdt.feature.group \
    && ls /opt/eclipse/plugins | grep -q '^org.eclipse.jdt.launching_'

RUN git config --global user.name "UI test" && git config --global user.email "uitest@example.invalid" \
    && git config --global init.defaultBranch main

# to look at this Eclipse from a browser on the host (run-gui.sh)
RUN apt-get update && apt-get install -y --no-install-recommends x11vnc novnc websockify \
    && rm -rf /var/lib/apt/lists/*

COPY run-tests.sh run-gui.sh /usr/local/bin/
RUN chmod +x /usr/local/bin/run-tests.sh /usr/local/bin/run-gui.sh
EXPOSE 6080
CMD ["/usr/local/bin/run-tests.sh"]
