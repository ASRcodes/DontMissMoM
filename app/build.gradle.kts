plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.google.gms.google.services)
}

android {
    namespace = "com.example.dontmissmom"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.dontmissmom"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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

    // --- Core Android ---
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)

    // --- UI ---
    // You have 'libs.material' AND a hardcoded material dependency.
    // Usually 'libs.material' is enough, but I left both to be safe.
    implementation(libs.material)
    implementation("com.google.android.material:material:1.11.0")

    // --- Firebase Setup ---
    // Import the BoM to manage versions (prevents crashes between Auth/Firestore/Messaging)
    implementation(platform("com.google.firebase:firebase-bom:32.7.0"))

    // When using BoM, you don't strictly need version numbers for these,
    // but using your 'libs' references is fine.
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation("com.google.firebase:firebase-messaging") // Removed hardcoded version to let BoM handle it

    // --- CRITICAL FOR YOUR LOGIN FLOW ---
    // This is required for GoogleSignInOptions to work
    implementation("com.google.android.gms:play-services-auth:20.7.0")

    // --- Image Loading ---
    // You need this to load the Google User's profile photo URL
    implementation("com.github.bumptech.glide:glide:4.16.0")
    implementation(libs.play.services.location)

    // --- Testing ---
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}