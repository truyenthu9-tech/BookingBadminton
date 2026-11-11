plugins {
    alias(libs.plugins.android.application)
}

// Địa chỉ backend, ghi đè bằng: gradlew assembleDebug -PapiBaseUrl=http://10.0.2.2:8000/api/
val apiBaseUrl = (project.findProperty("apiBaseUrl") as String?)
    ?: "https://resemble-recovery-bloomers.ngrok-free.dev/api/"

android {
    namespace = "com.example.myapplication"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.myapplication"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)

    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.navigation:navigation-fragment:2.7.7")
    implementation("androidx.navigation:navigation-ui:2.7.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel:2.8.4")
    implementation("androidx.lifecycle:lifecycle-livedata:2.8.4")
    implementation("com.google.zxing:core:3.5.3")
}
