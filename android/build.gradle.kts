plugins {
    id("com.android.library")
}

android {
    namespace = "dev.maia.reader"
    compileSdk = 36
    defaultConfig {
        minSdk = 34
        consumerProguardFiles("consumer-rules.pro")
    }
}
