import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("com.google.gms.google-services")
}

val properties = Properties().apply {
    load(rootProject.file("local.properties").inputStream())
}

android {
    namespace = "com.novikon.pace"
    compileSdk = 36

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    defaultConfig {
        applicationId = "com.novikon.pace"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        buildConfigField("String", "GEMINI_API_KEY", "\"${properties["GEMINI_API_KEY"]}\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "SUPABASE_URL", "\"https://nveliyinwnrcagsykxtz.supabase.co\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Im52ZWxpeWlud25yY2Fnc3lreHR6Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzYxNjg2MDcsImV4cCI6MjA5MTc0NDYwN30.xbn52nb0MS2OpGWbPnxjgG3KTxwEPwIQWsHNGtlHjAo\"")
        buildConfigField("String", "SUPABASE_BUCKET", "\"chat-moments\"")
        buildConfigField("String", "SUPABASE_PROFILE_BUCKET", "\"profile-photos\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)

    implementation(platform("com.google.firebase:firebase-bom:32.7.0"))
    implementation("com.google.firebase:firebase-analytics-ktx")
    implementation("com.google.firebase:firebase-database-ktx")
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.android.gms:play-services-auth:20.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.drawerlayout:drawerlayout:1.2.0")
    implementation("androidx.work:work-runtime-ktx:2.9.0")
    implementation("com.github.yalantis:ucrop:2.2.10")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.firebase:firebase-storage-ktx")
    implementation("com.github.bumptech.glide:glide:4.16.0")
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")
    implementation("com.itextpdf:itext7-core:7.2.5")
    implementation("com.google.firebase:firebase-messaging-ktx")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}