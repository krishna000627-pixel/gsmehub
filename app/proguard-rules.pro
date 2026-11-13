-keepattributes JavascriptInterface,Signature,*Annotation*
-keepclassmembers class com.krishna.webhub.web.WebHubBridge {
    @android.webkit.JavascriptInterface <methods>;
}
-keep class com.krishna.webhub.data.AppEntity { *; }
