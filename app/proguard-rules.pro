# kotlinx.serialization and OkHttp ship their own R8 rules.
# Keep the names of the API models so error messages and crash reports stay readable.
-keepattributes *Annotation*, InnerClasses
-keep,includedescriptorclasses class ai.peridot.app.data.**$$serializer { *; }
-keepclassmembers class ai.peridot.app.data.** {
    *** Companion;
}

# Credential Manager finds its Google Play services provider by reflection
-if class androidx.credentials.CredentialManager
-keep class androidx.credentials.playservices.** {
  *;
}
