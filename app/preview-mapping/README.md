# Preview build mapping

`mapping.txt` is R8's rename table for the **preview** APK. The tester has no
computer, so a crash arrives as text pasted from the app's own crash dialog —
this file is what turns `a.b.c(SourceFile:41)` back into a real frame:

```
retrace app/preview-mapping/mapping.txt crash.txt
```

It only decodes the build it came from. Replace it whenever a new preview APK
is handed out, in the same commit, or traces from that build will retrace to
nonsense rather than failing loudly.
