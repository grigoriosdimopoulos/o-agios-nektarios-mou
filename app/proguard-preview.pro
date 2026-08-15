# Extra rules for the `preview` variant only.
#
# Preview builds are handed to a tester who has no computer and therefore no
# logcat, so the app reports its own crashes (see CrashReporter). That argues
# for keeping original names — but leaving renaming off cost several megabytes
# of dex on a build whose entire purpose is being small enough to reach a phone
# over a chat window, and a build that will not download is worth less than a
# stack trace that needs one extra step to read.
#
# So renaming stays on, and the mapping is kept instead: every preview build
# writes app/build/outputs/mapping/preview/mapping.txt, and `retrace` turns an
# obfuscated trace back into a readable one. Keep the mapping that matches the
# APK the tester is actually running.
#
# Line numbers are preserved regardless, so a retraced frame keeps its file and
# line rather than degrading to a bare method name.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
