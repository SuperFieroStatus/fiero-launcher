plugins {
    id("com.android.application")
}

android {
    namespace = "dev.mike.fierolauncher"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.mike.fierolauncher"
        minSdk = 30
        targetSdk = 30
        versionCode = 1
        versionName = "0.1.0-m0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Signed with the debug key on purpose: installs over debug builds and shares FlipSpot's
            // signature (needed if the two apps ever talk through a signature permission).
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        // targetSdk 30 is deliberate: the TCL Flip 2 runs Android 11 and this is never shipped to Play.
        disable += listOf("ExpiredTargetSdkVersion", "OldTargetApi")
    }
}

// No dependencies: framework Views and APIs only.
