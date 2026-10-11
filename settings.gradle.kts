pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "DadGames"

// Shared look and widgets for every game.
include(":ui")
include(":cards")   // standard 52-card deck model (Solitaire, Blackjack)

// Each game: pure-Kotlin rules in :<game>:core, Compose app in :<game>:app.
for (game in listOf("euchre", "blocks", "solitaire", "blackjack", "sudoku")) {
    if (file("$game/core/build.gradle.kts").exists()) include(":$game:core")
    if (file("$game/app/build.gradle.kts").exists()) include(":$game:app")
}
