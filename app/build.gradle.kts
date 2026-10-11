plugins { alias(libs.plugins.android.application); alias(libs.plugins.kotlin.android); alias(libs.plugins.kotlin.compose) }
android {
    namespace="local.readapp"; compileSdk=36
    defaultConfig {
        applicationId="com.luminavault.reader"; minSdk=26; targetSdk=36; versionCode=100; versionName="1.0.0"
        testInstrumentationRunner="local.readapp.test.P4Instrumentation"
    }
    signingConfigs { create("localTest") { storeFile=rootProject.file(".tools/p0.keystore"); storePassword="android"; keyAlias="p0"; keyPassword="android" } }
    buildTypes { release {
        isMinifyEnabled=true; isShrinkResources=true
        if(rootProject.file(".tools/p0.keystore").exists())signingConfig=signingConfigs.getByName("localTest") else signingConfig=signingConfigs.getByName("debug")
        proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"),"proguard-rules.pro")
    } }
    buildFeatures { compose=true }
    testBuildType="release"
    compileOptions { sourceCompatibility=JavaVersion.VERSION_17; targetCompatibility=JavaVersion.VERSION_17; isCoreLibraryDesugaringEnabled=true }
    kotlinOptions { jvmTarget="17" }
    sourceSets.getByName("androidTest").assets.srcDir(rootProject.file("fixtures/generated"))
}
dependencies {
    implementation(project(":engine-epub"))
    implementation(project(":core")); implementation(project(":data")); implementation(project(":engine-txt")); implementation(project(":feature"))
    implementation(libs.activity.compose); implementation(libs.coroutines.android)
    implementation(libs.compose.ui); implementation(libs.compose.foundation); implementation(libs.compose.material3)
    coreLibraryDesugaring(libs.desugar)
}
