/*
 * j2me-deck: a tiny native (static) launcher, so Steam Game Mode has a real executable to start.
 *
 * Why not just add run.sh to Steam?  Steam starts non-Steam games with its own runtime libraries (LD_LIBRARY_PATH)
 * and its overlay (LD_PRELOAD) injected, which breaks Java and even bash; a script also needs the executable bit
 * and is not offered by Steam's file picker. This program
 *   1. finds its own folder (works through symlinks and from any working directory),
 *   2. restores the system library path and drops the overlay preload,
 *   3. hands over to `bash run.sh`, which finds Java, writes ~/.local/share/j2me-deck/launch.log and starts the app.
 * All arguments are passed through (e.g. j2me-deck --fullscreen game.jar).
 */
#define _GNU_SOURCE
#include <limits.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

static void restore_environment(void) {
    const char *sys = getenv("SYSTEM_LD_LIBRARY_PATH");     /* Steam keeps the original value here */
    if (sys && *sys) setenv("LD_LIBRARY_PATH", sys, 1); else unsetenv("LD_LIBRARY_PATH");
    unsetenv("LD_PRELOAD");                                   /* gameoverlayrenderer.so crashes or stalls Java */
    unsetenv("LD_AUDIT");
    if (!getenv("HOME") || !*getenv("HOME")) setenv("HOME", "/home/deck", 0);
}

int main(int argc, char **argv) {
    char exe[PATH_MAX], dir[PATH_MAX], script[PATH_MAX + 16];
    ssize_t n = readlink("/proc/self/exe", exe, sizeof exe - 1);
    if (n <= 0) { fprintf(stderr, "j2me-deck: cannot locate itself (/proc not mounted?)\n"); return 127; }
    exe[n] = '\0';
    char *slash = strrchr(exe, '/');
    if (!slash) { fprintf(stderr, "j2me-deck: unexpected path %s\n", exe); return 127; }
    size_t len = (size_t)(slash - exe);
    memcpy(dir, exe, len); dir[len] = '\0';
    snprintf(script, sizeof script, "%s/run.sh", dir);
    if (access(script, R_OK) != 0) { fprintf(stderr, "j2me-deck: %s not found - keep this file next to run.sh\n", script); return 127; }

    restore_environment();

    char **args = calloc((size_t)argc + 3, sizeof *args);
    if (!args) return 127;
    args[0] = "bash"; args[1] = script;                       /* run through bash: works even if run.sh lost its +x bit */
    for (int i = 1; i < argc; i++) args[i + 1] = argv[i];
    args[argc + 1] = NULL;

    static const char *shells[] = {"/usr/bin/bash", "/bin/bash", "/run/host/usr/bin/bash", NULL};
    for (int i = 0; shells[i]; i++) execv(shells[i], args);
    fprintf(stderr, "j2me-deck: bash not found (needed to run %s)\n", script);
    return 127;
}
