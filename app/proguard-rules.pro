# Keep the JavaScript bridge methods (called from index.html)
-keepclassmembers class com.rssolanki.hisabkitab.MainActivity$Bridge {
    @android.webkit.JavascriptInterface <methods>;
}
