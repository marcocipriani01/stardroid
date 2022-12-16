plugins {
    id("com.android.application")
    id("com.google.android.libraries.mapsplatform.secrets-gradle-plugin")
}

android {
    compileSdk = 33
    buildToolsVersion = "33.0.0"

    defaultConfig {
        applicationId = "io.github.marcocipriani01.telescopetouch"
        minSdk = 21
        targetSdk = 33
        versionCode = 38
        versionName = "1.8.8"
        multiDexEnabled = true
    }
    buildTypes {
        named("release") {
            isMinifyEnabled = false
            setProguardFiles(listOf(getDefaultProguardFile("proguard-android.txt"), "proguard.cfg"))
        }
    }
    packagingOptions {
        resources {
            excludes += listOf("META-INF/DEPENDENCIES", "META-INF/LICENSE", "META-INF/LICENSE.txt", "META-INF/LICENSE.md", "META-INF/license.txt", "META-INF/NOTICE", "META-INF/NOTICE.txt", "META-INF/NOTICE.md", "META-INF/notice.txt", "META-INF/ASL2.0", "META-INF/*.kotlin_module")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    lint {
        abortOnError = false
    }
    namespace = "io.github.marcocipriani01.telescopetouch"
}

dependencies {
    implementation(project(path = ":datamodel"))
    implementation("com.github.INDIForJava:INDIForJava-client:2.1.1")
    implementation("com.github.marcocipriani01:SimpleSocket:1.2.3")
    implementation("com.github.marcocipriani01:GraphView:5.0.1")
    implementation("com.github.marcocipriani01:PhotoView:3.0.4")

    implementation("androidx.appcompat:appcompat:1.5.1")
    implementation("androidx.preference:preference:1.2.0")
    implementation("androidx.activity:activity:1.6.1")
    implementation("androidx.fragment:fragment:1.5.5")
    implementation("androidx.viewpager2:viewpager2:1.0.0")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")

    implementation("com.google.android.material:material:1.7.0")

    implementation("com.google.dagger:dagger:2.42")
    implementation("com.google.dagger:dagger-android-support:2.42")
    annotationProcessor("com.google.dagger:dagger-android-processor:2.42")
    annotationProcessor("com.google.dagger:dagger-compiler:2.42")

    implementation("com.github.woxthebox:draglistview:1.7.2")
    implementation("com.github.myinnos:AlphabetIndex-Fast-Scroll-RecyclerView:1.0.95")

    implementation("org.jmdns:jmdns:3.5.7")
    implementation("com.google.code.gson:gson:2.9.0")
    implementation("com.google.protobuf:protobuf-javalite:3.15.6")
    implementation("com.jcraft:jsch:0.1.55")
    implementation("com.google.android.gms:play-services-oss-licenses:17.0.0")

    implementation("com.google.android.gms:play-services-maps:18.1.0")
    implementation("com.google.android.gms:play-services-location:21.0.1")
    implementation("com.google.android.libraries.places:places:3.0.0")
}

apply(plugin = "com.google.android.gms.oss-licenses-plugin")
