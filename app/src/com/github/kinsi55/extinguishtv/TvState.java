package com.github.kinsi55.extinguishtv;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/** Package state read from the TV over ADB. */
final class TvState {
    final Set<String> installed = new HashSet<>();
    final Set<String> disabled = new HashSet<>();
    final Set<String> persistent = new HashSet<>();

    static TvState read(AdbClient adb) throws IOException {
        TvState state = new TvState();
        parsePackages(adb.run("shell:pm list packages"), state.installed);
        parsePackages(adb.run("shell:pm list packages -d"), state.disabled);
        // Persistent system apps run at oom_score_adj -800; disabling them makes Android restart-loop them.
        String persistent = adb.run("shell:for d in /proc/[0-9]*; do "
                + "[ \"$(cat $d/oom_score_adj 2>/dev/null)\" = -800 ] && tr '\\0' '\\n' < $d/cmdline | head -n 1; done");
        for (String line : persistent.split("\n")) {
            String name = line.trim();
            int colon = name.indexOf(':');
            if (colon > 0) name = name.substring(0, colon);
            if (!name.isEmpty()) state.persistent.add(name);
        }
        return state;
    }

    private static void parsePackages(String output, Set<String> into) {
        for (String line : output.split("\n")) {
            line = line.trim();
            if (line.startsWith("package:")) into.add(line.substring("package:".length()));
        }
    }
}
