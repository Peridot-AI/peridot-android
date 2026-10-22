# kotlinx.serialization and OkHttp ship their own R8 rules.
# Keep the names of the API models so error messages and crash reports stay readable.
-keepattributes *Annotation*, InnerClasses
-keep,includedescriptorclasses class ai.peridot.app.data.**$$serializer { *; }
-keepclassmembers class ai.peridot.app.data.** {
    *** Companion;
}
