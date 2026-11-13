-keepattributes JavascriptInterface,Signature,*Annotation*
-keepclassmembers class com.krishna.gamehub.web.GamehubBridge {
    @android.webkit.JavascriptInterface <methods>;
}
-keep class com.krishna.gamehub.data.AppEntity { *; }
