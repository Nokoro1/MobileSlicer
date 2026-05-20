# Keep default release rules minimal for the validation app.

# Some transitive libraries package compile-time annotation processor service
# descriptors. Those processors are not used by the Android app at runtime, but
# R8 still inspects their bytecode during release minification.
-dontwarn javax.annotation.processing.**
-dontwarn javax.lang.model.**
