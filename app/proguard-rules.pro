# Vosk - keep native libraries
-keep class org.vosk.** { *; }
-keep class com.sun.jna.** { *; }
-dontwarn org.vosk.**
-dontwarn com.sun.jna.**

# Java-WebSocket
-keep class org.java_websocket.** { *; }
-dontwarn org.java_websocket.**

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }
-keep class * extends com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# NanoHTTPD
-keep class org.nanohttpd.** { *; }
-dontwarn org.nanohttpd.**

# Keep data model classes used in JSON serialization
-keep class com.accessible.toolkit.engine.model.** { *; }
-keep class com.accessible.toolkit.vosk.ModelManager$ModelInfo { *; }
-keep class com.accessible.toolkit.vosk.ModelManager$ExtractProgress { *; }
-keep class com.accessible.toolkit.bridge.SubtitleWebSocketServer$TranscriptMessage { *; }
-keep class com.accessible.toolkit.bridge.SubtitleWebSocketServer$VadMessage { *; }
-keep class com.accessible.toolkit.bridge.SubtitleWebSocketServer$StatusMessage { *; }
-keep class com.accessible.toolkit.bridge.SubtitleWebSocketServer$HeartbeatMessage { *; }

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
