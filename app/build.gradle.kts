plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.example.rythm"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.rythm"
        minSdk = 29
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
    buildFeatures {
        compose = true
    }
}

dependencies {

    // These should have been included by the template
    implementation("androidx.core:core-ktx:1.13.1") // Or your version
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.3") // Or your version
    implementation("androidx.activity:activity-compose:1.9.0") // Or your version
    implementation(platform("androidx.compose:compose-bom:2024.06.00")) // Or your version
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    testImplementation("junit:junit:4.13.2") // Or your version
    androidTestImplementation("androidx.test.ext:junit:1.2.1") // Or your version
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1") // Or yourversion
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.06.00")) // Or your version
    androidTestImplementation("androidx.compose.ui:ui-test-junit4") // Or your version
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    // This is the Compose "Bill of Materials" - it manages versions
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))

    // THIS LINE is for the 'ui' error
    implementation("androidx.compose.ui:ui")

    // You also need these for a basic Compose app
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.9.0")

    // --- THESE ARE THE LIBRARIES YOU ARE LIKELY MISSING ---

    // For Permissions (from Lesson 2)
    implementation("com.google.accompanist:accompanist-permissions:0.34.0")

    // For the Splash Screen (from Lesson 2)
    implementation("androidx.core:core-splashscreen:1.0.1")

    // For the Icons (like MusicNote, from Lesson 3)
    implementation("androidx.compose.material:material-icons-extended:1.6.8") // Or your version
}