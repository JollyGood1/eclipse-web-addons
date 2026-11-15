#!/usr/bin/env bash
# Runs inside the container (see Dockerfile). Installs the plug-ins from the update site into the
# Eclipse of the image, then runs the checks: Apply from the command line, and the UI tests of
# tools/uitest and tools/iconstest on a virtual screen. Results and pictures go to /out.
set -u
SITE=${SITE:-https://jollygood1.github.io/eclipse-web-addons/}
TESTS=${TESTS:-themes java icons}
ECLIPSE=/opt/eclipse
SRC=/src
OUT=/out
WORK=/work
INFO=$ECLIPSE/configuration/org.eclipse.equinox.simpleconfigurator/bundles.info
mkdir -p "$OUT" "$WORK"

# Eclipse on a virtual screen with a window manager
on_screen() {
    timeout "${TIMEOUT:-600}" xvfb-run -a -s "-screen 0 1920x1200x24" \
        bash -c 'openbox >/dev/null 2>&1 & sleep 1; exec "$@"' _ "$@"
}

echo "=== system"
. /etc/os-release && echo "$PRETTY_NAME"
ls "$ECLIPSE/plugins" | grep -c . | sed 's/^/plug-ins in the Eclipse of the image: /'

echo "=== install from $SITE"
on_screen "$ECLIPSE/eclipse" -nosplash -application org.eclipse.equinox.p2.director -repository "$SITE" \
    -installIU io.github.jollygood1.syntaxthemes.feature.feature.group,io.github.jollygood1.fileicons.feature.feature.group \
    > "$OUT/install.log" 2>&1
echo "installer exit code $?"
grep -E 'nstall|Operation|annot|rror' "$OUT/install.log" | head -20
grep jollygood1 "$INFO" || { echo "the plug-ins are not installed, stopping"; exit 1; }

echo "=== apply Tomorrow Night from the command line"
on_screen "$ECLIPSE/eclipse" -nosplash -data "$WORK/ws-headless" \
    -application io.github.jollygood1.syntaxthemes.apply -theme "Tomorrow Night" -all > "$OUT/apply.log" 2>&1
echo "apply exit code $?"
grep -E '^Applied|^Skipped|^Unknown' "$OUT/apply.log" | cut -c1-400
on_screen "$ECLIPSE/eclipse" -nosplash -data "$WORK/ws-installed" \
    -application io.github.jollygood1.syntaxthemes.apply -theme "Tomorrow Night" > "$OUT/apply-installed.log" 2>&1
grep -E '^Applied|^Skipped' "$OUT/apply-installed.log" | sed 's/^/editors installed here only: /' | cut -c1-400
if [ -d /kit ]; then
    python3 -I "$SRC/tools/compare_prefs.py" /kit "$WORK/ws-headless" | tail -n 14
else
    echo "no /kit folder mounted: the values were not compared"
fi

# a second colour theme (white background, red keywords) and an image for the tests to import
mkdir -p "$WORK/import"
THEMES=$SRC/io.github.jollygood1.syntaxthemes/themes
sed -e 's/name="Tomorrow Night"/name="Import Test"/' \
    -e 's/<background color="#[0-9A-Fa-f]\{6\}"/<background color="#FFFFFF"/' \
    -e 's/<foreground color="#[0-9A-Fa-f]\{6\}"/<foreground color="#000000"/' \
    -e 's/<keyword color="#[0-9A-Fa-f]\{6\}"/<keyword color="#FF0000"/' \
    "$THEMES/tomorrow-night.xml" > "$WORK/import/Import Test.xml"
cp "$THEMES/tomorrow-night.tmtheme.json" "$WORK/import/Import Test.tmtheme.json"
IMAGE=$SRC/io.github.jollygood1.fileicons/icons/web/html5@2x.png

# ui_test <name> <source folder under tools> <line prefix> <window theme> <-D options...>
ui_test() {
    local name=$1 src=$SRC/tools/$2 prefix=$3 theme=$4
    shift 4
    echo "=== UI test: $name"
    local classes=$WORK/classes-$name jar=$WORK/test-$name.jar ws=$WORK/ws-$name out=$OUT/$name
    rm -rf "$classes" "$ws" "$out"
    mkdir -p "$classes" "$out"
    if ! javac --release 21 -encoding UTF-8 -nowarn -d "$classes" -cp "$ECLIPSE/plugins/*" \
            $(find "$src/src" -name '*.java'); then
        echo "javac failed for $name"
        return 1
    fi
    jar --create --file "$jar" --manifest "$src/META-INF/MANIFEST.MF" -C "$classes" . -C "$src" plugin.xml
    local id version
    id=$(sed -n 's/^Bundle-SymbolicName: \([^;[:space:]]*\).*/\1/p' "$src/META-INF/MANIFEST.MF" | tr -d '\r')
    version=$(sed -n 's/^Bundle-Version: \([^[:space:]]*\).*/\1/p' "$src/META-INF/MANIFEST.MF" | tr -d '\r')
    # the test plug-in is added to the install's bundle list for this run only
    grep -v 'uitest,' "$INFO" > "$WORK/bundles.info" && cp "$WORK/bundles.info" "$INFO"
    echo "$id,$version,file:$jar,4,false" >> "$INFO"
    local settings=$ws/.metadata/.plugins/org.eclipse.core.runtime/.settings
    mkdir -p "$settings"
    printf 'eclipse.preferences.version=1\nthemeid=org.eclipse.e4.ui.css.theme.%s\n' "$theme" \
        > "$settings/org.eclipse.e4.ui.css.swt.theme.prefs"
    printf 'eclipse.preferences.version=1\nEXIT_PROMPT_ON_CLOSE_LAST_WINDOW=false\n' > "$settings/org.eclipse.ui.ide.prefs"
    on_screen "$ECLIPSE/eclipse" -clean -nosplash -data "$ws" -vmargs "-Duitest.out=$out" "$@" > "$out/eclipse-output.txt" 2>&1
    echo "Eclipse exit code $? (124 = stopped after the time limit)"
    grep "^$prefix" "$out/eclipse-output.txt" | cut -c1-600
    if [ -f "$ws/.metadata/.log" ]; then
        grep -n 'jollygood1' "$ws/.metadata/.log" | grep -v 'arguments:' | head -10 | sed 's/^/workspace log: /'
        cp "$ws/.metadata/.log" "$out/workspace.log"
    fi
    ls "$out" | grep '\.png$' | sed 's/^/  /'
    grep -v 'uitest,' "$INFO" > "$WORK/bundles.info" && cp "$WORK/bundles.info" "$INFO"
}

for t in $TESTS; do
    case $t in
    themes) ui_test themes uitest UITEST e4_dark "-Duitest.import=$WORK/import/Import Test.xml" ;;
    java) ui_test java uitest UITEST e4_default -Duitest.java=true ;;
    icons) ui_test icons iconstest ICONTEST e4_dark "-Duitest.import=$IMAGE" ;;
    *) echo "unknown test: $t" ;;
    esac
done
echo "=== done"
