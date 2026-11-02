dependencies {
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
}

// Use an integer for version numbers
version = 1

cloudstream {
    description = "PelisPlusHD provider for Cloudstream"
    authors = listOf("Cloudstream Plugin Template")

    status = 1
    tvTypes = listOf("Movie", "TV")
    requiresResources = true
    language = "es"
    iconUrl = "https://pelisplushd.mx/wp-content/uploads/2023/08/logo-pelisplus.png"
}

android {
    buildFeatures {
        buildConfig = true
        viewBinding = true
    }
}
