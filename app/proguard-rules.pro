# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile
# 릴리스 크래시 스택 트레이스의 라인 번호 보존
-keepattributes SourceFile,LineNumberTable

# 7-Zip-JBinding — 네이티브(JNI) 코드가 클래스·멤버를 이름으로 조회하므로 축소·난독화 대상에서 제외
-keep class net.sf.sevenzipjbinding.** { *; }

# commons-net — FTP 목록 파서를 이름 기반으로 생성할 수 있으므로 파서 클래스 보존
-keep class org.apache.commons.net.ftp.parser.** { *; }

# OkHttp(Coil 의존) — 런타임에 존재할 때만 쓰는 선택적 TLS 프로바이더
-dontwarn org.bouncycastle.jsse.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
