# Retrace crash reports after R8 renames classes and methods.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Ktor's no-arg HttpClient() loads the engine through ServiceLoader
# (META-INF/services/io.ktor.client.HttpClientEngineContainer → OkHttp).
-keep class io.ktor.client.HttpClientEngineContainer
-keep class * implements io.ktor.client.HttpClientEngineContainer

# Generated Compose Resources accessors and collectors: the resource loader walks the
# generated maps, and R8 full mode has dropped those helpers before.
-keep class mailtice.shared.generated.resources.** { *; }

# Jakarta Mail / Angus: providers and data handlers are found by reflection and ServiceLoader
# (META-INF/javamail.providers, mailcap). Keep them whole.
-keep class org.eclipse.angus.** { *; }
-keep class jakarta.mail.** { *; }
-keep class jakarta.activation.** { *; }
-dontwarn org.eclipse.angus.**
-dontwarn jakarta.mail.**
-dontwarn jakarta.activation.**
-dontwarn java.awt.**
-dontwarn javax.security.sasl.**

# SQLDelight generated database.
-keep class co.abaye.mailtice.db.** { *; }

# WorkManager instantiates workers by class name.
-keep class co.abaye.mailtice.sync.SyncWorker { *; }
