# Extra rules for the `preview` variant only.
#
# Preview builds are handed to testers who have no computer and therefore no
# logcat — the app reports its own crashes (see CrashReporter). A stack trace
# full of a.b.c() names would make that report useless, so shrinking stays on
# and renaming does not. Costs a little size, buys a readable crash.
-dontobfuscate
