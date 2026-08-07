import re

with open('app/src/main/java/com/example/ui/MainApp.kt', 'r') as f:
    content = f.read()

# Add transition imports
imports = """import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
"""

if "import androidx.compose.animation.fadeIn" not in content:
    content = content.replace("import androidx.compose.runtime.Composable", imports + "import androidx.compose.runtime.Composable")

# Replace NavHost to add enter/exit transitions
nav_host_old = 'NavHost(navController = navController, startDestination = "browser") {'
nav_host_new = """NavHost(
        navController = navController,
        startDestination = "browser",
        enterTransition = { slideInHorizontally(initialOffsetX = { 1000 }, animationSpec = tween(400)) + fadeIn(animationSpec = tween(400)) },
        exitTransition = { slideOutHorizontally(targetOffsetX = { -1000 }, animationSpec = tween(400)) + fadeOut(animationSpec = tween(400)) },
        popEnterTransition = { slideInHorizontally(initialOffsetX = { -1000 }, animationSpec = tween(400)) + fadeIn(animationSpec = tween(400)) },
        popExitTransition = { slideOutHorizontally(targetOffsetX = { 1000 }, animationSpec = tween(400)) + fadeOut(animationSpec = tween(400)) }
    ) {"""

content = content.replace(nav_host_old, nav_host_new)

with open('app/src/main/java/com/example/ui/MainApp.kt', 'w') as f:
    f.write(content)

