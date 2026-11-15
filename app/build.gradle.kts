plugins {
  alias(libs.plugins.android.application)
}

fun readAppSecret(key: String): String {
  val envVal = System.getenv(key)?.trim()
  if (!envVal.isNullOrEmpty()) return envVal
  val dotEnvFile = rootProject.file(".env")
  if (dotEnvFile.exists()) {
    dotEnvFile.readLines().forEach { rawLine ->
      val line = rawLine.trim()
      if (line.isNotEmpty() && !line.startsWith("#") && line.contains("=")) {
        val idx = line.indexOf('=')
        val k = line.substring(0, idx).trim()
        if (k == key) {
          return line.substring(idx + 1).trim().removeSurrounding("\"").removeSurrounding("'")
        }
      }
    }
  }
  return ""
}

val admobAppIdSecret = readAppSecret("ADMOB_APP_ID")
val admobInterstitialSecret = readAppSecret("ADMOB_INTERSTITIAL_AD_UNIT_ID")
val admobRewardedSecret = readAppSecret("ADMOB_REWARDED_AD_UNIT_ID")
val admobSurveyRewardsSecret = readAppSecret("ADMOB_SURVEY_REWARDS_AD_UNIT_ID")

android {
  namespace = "com.tutor.earn.pro"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.tutor.earn.pro"
    minSdk = 23
    targetSdk = 36
    versionCode = 2
    versionName = "1.0.2"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  // Do not remove or modify these signingConfigs. They are necessary for building, installing,
  // and updating Android apps in AI Studio.
  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      storeFile = file(keystorePath)
      storePassword = System.getenv("STORE_PASSWORD")
      keyAlias = "upload"
      keyPassword = System.getenv("KEY_PASSWORD")
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      val effectiveReleaseInterstitial = admobInterstitialSecret.ifEmpty {
        "ca-app-pub-8972616814126419/2306700688"
      }
      val effectiveReleaseSurveyRewards = admobSurveyRewardsSecret.ifEmpty {
        "ca-app-pub-8972616814126419/5563489220"
      }
      manifestPlaceholders["admobAppId"] = admobAppIdSecret
      buildConfigField("String", "ADMOB_INTERSTITIAL_AD_UNIT_ID", "\"$effectiveReleaseInterstitial\"")
      buildConfigField("String", "ADMOB_REWARDED_AD_UNIT_ID", "\"$admobRewardedSecret\"")
      buildConfigField("String", "ADMOB_SURVEY_REWARDS_AD_UNIT_ID", "\"$effectiveReleaseSurveyRewards\"")
      buildConfigField("boolean", "IS_TEST_AD_MODE", "false")
      // Do not remove or modify this signingConfig assignment. It is necessary for Android apps in
      // AI Studio.
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
      // Official Google AdMob Test App ID & fallback Rewarded ID for development; explicit Ad Unit IDs for Prime Survey & Survey Rewards
      manifestPlaceholders["admobAppId"] = "ca-app-pub-3940256099942544~3347511713"
      buildConfigField("String", "ADMOB_INTERSTITIAL_AD_UNIT_ID", "\"ca-app-pub-8972616814126419/2306700688\"")
      buildConfigField("String", "ADMOB_REWARDED_AD_UNIT_ID", "\"ca-app-pub-3940256099942544/5224354917\"")
      buildConfigField("String", "ADMOB_SURVEY_REWARDS_AD_UNIT_ID", "\"ca-app-pub-8972616814126419/5563489220\"")
      buildConfigField("boolean", "IS_TEST_AD_MODE", "true")
      // Do not remove or modify this signingConfig assignment. It is necessary for Android apps in
      // AI Studio.
      signingConfig = signingConfigs.getByName("debugConfig")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    viewBinding = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

// Some unused template dependencies are commented out below instead of being removed
// to keep APK size small and build times fast for this Java + XML project.
dependencies {
  implementation(libs.androidx.appcompat)
  implementation(libs.material)
  implementation(libs.androidx.constraintlayout)
  implementation(libs.androidx.activity)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.viewpager2)
  implementation(libs.play.services.ads)

  // Firebase Auth & Realtime Database (activates automatically when google-services.json is configured)
  implementation(platform(libs.firebase.bom))
  implementation(libs.firebase.auth)
  implementation(libs.firebase.database)

  // Unused template dependencies commented out:
  // implementation(platform(libs.androidx.compose.bom))
  // implementation(libs.androidx.activity.compose)
  // implementation(libs.androidx.compose.material.icons.core)
  // implementation(libs.androidx.compose.material.icons.extended)
  // implementation(libs.androidx.compose.material3)
  // implementation(libs.androidx.compose.ui)
  // implementation(libs.androidx.compose.ui.graphics)
  // implementation(libs.androidx.compose.ui.tooling.preview)
  // implementation(libs.androidx.lifecycle.runtime.compose)
  // implementation(libs.androidx.lifecycle.runtime.ktx)
  // implementation(libs.androidx.lifecycle.viewmodel.compose)
  // implementation(libs.androidx.room.ktx)
  // implementation(libs.androidx.room.runtime)
  // implementation(libs.converter.moshi)
  // implementation(libs.firebase.ai)
  // implementation(libs.firebase.appcheck.recaptcha)
  // implementation(libs.firebase.appcheck.debug)
  // implementation(libs.kotlinx.coroutines.android)
  // implementation(libs.kotlinx.coroutines.core)
  // implementation(libs.logging.interceptor)
  // implementation(libs.moshi.kotlin)
  // implementation(libs.okhttp)
  // implementation(libs.retrofit)

  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.robolectric)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
}

val googleServicesFile = file("google-services.json")
if (googleServicesFile.exists() && googleServicesFile.readText().contains("com.tutor.earn.pro")) {
  apply(plugin = "com.google.gms.google-services")
}
