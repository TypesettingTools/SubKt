"""Exercise the actual launchers with the JDK selected by JAVA_HOME (or PATH).

Run once per JDK, e.g. python3 tests/wrapper_compatibility.py 11 8.14.6.
Only the Python standard library is needed; Gradle downloads through the wrapper.
"""

import hashlib
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile


ROOT = Path(__file__).resolve().parents[1]
WRAPPER_FILES = [
    "gradlew",
    "gradlew.bat",
    "gradle/wrapper/gradle-wrapper.jar",
    "gradle/wrapper/gradle-wrapper.properties",
    "gradle/wrapper/gradle-wrapper-legacy.jar",
    "gradle/wrapper/gradle-wrapper-legacy.properties",
]
STOCK_JAR_SHA256 = "3b8a25775a69158b5ad2b1d17a80a88dc7b40a352a73eb27d06c612a7ce68e98"


def check_wrappers():
    for name in WRAPPER_FILES:
        data = (ROOT / name).read_bytes()
        assert data == (ROOT / "template" / name).read_bytes(), name
        if name.endswith(".jar"):
            assert hashlib.sha256(data).hexdigest() == STOCK_JAR_SHA256, name


def run(project, env, *arguments, success=True):
    launcher = project / ("gradlew.bat" if os.name == "nt" else "gradlew")
    result = subprocess.run(
        [str(launcher), "--project-dir", str(project), "--console=plain", *arguments],
        cwd=project.parent,  # Invocation outside the project must also work.
        env=env,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        text=True,
    )
    print(result.stdout, flush=True)
    assert (result.returncode == 0) == success, result.returncode
    return result.stdout


def main():
    java_version, gradle_version = sys.argv[1:]
    check_wrappers()
    with tempfile.TemporaryDirectory(prefix="subkt wrapper ") as temporary:
        project = Path(temporary) / "project with spaces"
        for name in WRAPPER_FILES:
            target = project / name
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(ROOT / "template" / name, target)
        original = {name: (project / name).read_bytes() for name in WRAPPER_FILES}
        (project / "settings.gradle").write_text("rootProject.name = 'wrapper-test'\n")
        (project / "build.gradle").write_text("""
tasks.register('verifyWrapper') {
    doLast {
        assert gradle.gradleVersion == providers.gradleProperty('expectedGradle').get()
        assert System.getProperty('java.specification.version') == providers.gradleProperty('expectedJava').get()
        assert providers.gradleProperty('message').get() == 'argument with spaces'
        assert System.getProperty('subkt.wrapper.javaOpt') == 'java-value'
        assert System.getProperty('subkt.wrapper.gradleOpt') == 'gradle-value'
        println 'WRAPPER_VERIFIED'
    }
}
tasks.register('failWrapper') {
    doLast { throw new GradleException('EXPECTED_WRAPPER_FAILURE') }
}
""")
        env = dict(os.environ)
        env["JAVA_OPTS"] = env.get("JAVA_OPTS", "") + " -Dsubkt.wrapper.javaOpt=java-value"
        env["GRADLE_OPTS"] = env.get("GRADLE_OPTS", "") + " -Dsubkt.wrapper.gradleOpt=gradle-value"
        java_home = env.get("JAVA_HOME")
        if java_home:
            # Exercise a JAVA_HOME containing spaces as well as project paths.
            alias = Path(temporary) / "java home"
            if os.name == "nt":
                # Directory junctions do not require Windows symlink privileges.
                subprocess.run(
                    [env["COMSPEC"], "/d", "/c", "mklink", "/J", str(alias), java_home],
                    check=True,
                )
            else:
                alias.symlink_to(java_home, target_is_directory=True)
            env["JAVA_HOME"] = str(alias)
            env["PATH"] = str(alias / "bin") + os.pathsep + env["PATH"]
        arguments = [
            "verifyWrapper",
            "-PexpectedJava=" + java_version,
            "-PexpectedGradle=" + gradle_version,
            "-Pmessage=argument with spaces",
        ]
        assert "WRAPPER_VERIFIED" in run(project, env, *arguments)
        env.pop("JAVA_HOME", None)
        assert "WRAPPER_VERIFIED" in run(project, env, *arguments)
        assert "EXPECTED_WRAPPER_FAILURE" in run(project, env, "failWrapper", success=False)
        for name, data in original.items():
            assert (project / name).read_bytes() == data, name + " was modified"
    print("Wrapper checks passed for Java " + java_version, flush=True)


if __name__ == "__main__":
    main()
