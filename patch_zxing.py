with open("gradle/libs.versions.toml", "r") as f:
    content = f.read()

content = content.replace('[versions]\n', '[versions]\nzxingCore = "3.5.3"\n')
content = content.replace('[libraries]\n', '[libraries]\nzxing-core = { group = "com.google.zxing", name = "core", version.ref = "zxingCore" }\n')

with open("gradle/libs.versions.toml", "w") as f:
    f.write(content)

with open("app/build.gradle.kts", "r") as f:
    build = f.read()

build = build.replace('dependencies {\n', 'dependencies {\n    implementation(libs.zxing.core)\n')

with open("app/build.gradle.kts", "w") as f:
    f.write(build)
