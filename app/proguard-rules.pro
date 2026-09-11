-keep class com.adblocker.ytmusic.service.AdBlockVpnService { *; }

# El hook JS llama a estos metodos por nombre desde la pagina: R8 no puede renombrarlos.
-keepclassmembers class com.adblocker.ytmusic.data.web.PlayerHookBridge {
    @android.webkit.JavascriptInterface <methods>;
}

-keepattributes SourceFile,LineNumberTable
