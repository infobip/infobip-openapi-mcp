#!/usr/bin/env python3

import glob
import os
import re
import shutil
import subprocess
import sys


def get_release_version():
    """
    Get the release version from RELEASE_VERSION environment variable or Maven.
    Returns the version string without -SNAPSHOT suffix.
    """
    release_version = os.environ.get("RELEASE_VERSION")

    if release_version:
        return release_version

    # Check if Maven is available
    if not shutil.which("mvn"):
        print("Error: Maven executable not found in PATH.", file=sys.stderr)
        sys.exit(1)

    try:
        # Get Maven version
        result = subprocess.run(
            [
                "mvn",
                "-q",
                "--non-recursive",
                "help:evaluate",
                "-Dexpression=project.version",
                "-DforceStdout",
            ],
            capture_output=True,
            text=True,
            check=True,
        )

        mvn_version = result.stdout.strip().split("\n")[-1]
        release_version = mvn_version.replace("-SNAPSHOT", "")

        if not release_version:
            print("Error: derived release version is empty.", file=sys.stderr)
            sys.exit(1)

        return release_version

    except subprocess.CalledProcessError:
        print("Error: unable to determine project version from Maven.", file=sys.stderr)
        sys.exit(1)


def update_readme_versions(readme_path, release_version):
    """
    Update version references in README.md file.
    Returns True if the file was modified, False otherwise.
    """
    try:
        with open(readme_path, "r", encoding="utf-8") as f:
            content = f.read()
    except FileNotFoundError:
        print(f'Error: README file not found at "{readme_path}".', file=sys.stderr)
        sys.exit(1)

    original_content = content

    # Pattern 1: Maven dependency version
    # Matches: <version>X.Y.Z</version>
    maven_pattern = r"(<artifactId>infobip-openapi-mcp-spring-boot-starter</artifactId>\s*<version>)[^<]+(</version>)"
    content = re.sub(maven_pattern, rf"\g<1>{release_version}\g<2>", content)

    # Pattern 2: Gradle dependency version
    # Matches: implementation("com.infobip.openapi.mcp:infobip-openapi-mcp-spring-boot-starter:X.Y.Z")
    gradle_pattern = r'(implementation\("com\.infobip\.openapi\.mcp:infobip-openapi-mcp-spring-boot-starter:)[^"]+("\))'
    content = re.sub(gradle_pattern, rf"\g<1>{release_version}\g<2>", content)

    # Check if any changes were made
    if content == original_content:
        print(f'No version references found or updated in "{readme_path}".')
        return False

    # Write the updated content back to the file
    with open(readme_path, "w", encoding="utf-8") as f:
        f.write(content)

    print(f'Successfully updated version references to "{release_version}" in README.md')
    return True


def update_pom_version(pom_path, release_version):
    """
    Update version reference for infobip-openapi-mcp-spring-boot-starter in a pom.xml file.
    Returns True if the file was modified, False otherwise.
    """
    try:
        with open(pom_path, "r", encoding="utf-8") as f:
            content = f.read()
    except FileNotFoundError:
        print(f'Error: pom.xml file not found at "{pom_path}".', file=sys.stderr)
        return False

    original_content = content

    # Pattern to match the version in pom.xml
    # Matches the version tag following infobip-openapi-mcp-spring-boot-starter
    pom_pattern = r"(<artifactId>infobip-openapi-mcp-spring-boot-starter</artifactId>\s*<version>)[^<]+(</version>)"
    content = re.sub(pom_pattern, rf"\g<1>{release_version}\g<2>", content)

    # Check if any changes were made
    if content == original_content:
        print(f'No version updates needed in "{pom_path}".')
        return False

    # Write the updated content back to the file
    with open(pom_path, "w", encoding="utf-8") as f:
        f.write(content)

    print(f'Successfully updated version to "{release_version}" in {pom_path}')
    return True


def find_example_pom_files():
    """
    Find all pom.xml files in the examples directory.
    Returns a list of paths to pom.xml files.
    """
    # Look for pom.xml files in examples/*/ directories
    pom_files = glob.glob("./examples/*/pom.xml")
    return pom_files


def commit_files_if_changed(file_paths, release_version):
    """
    Commit the files if they have changes.
    """
    try:
        # Check if there are any changes in the provided files
        result = subprocess.run(
            ["git", "diff", "--quiet", "--"] + file_paths, capture_output=True
        )

        if result.returncode == 0:
            # No changes
            print(f'No changes detected in any files to commit.')
        else:
            # Changes detected, commit them
            subprocess.run(["git", "add"] + file_paths, check=True)
            subprocess.run(
                [
                    "git",
                    "commit",
                    "-m",
                    f"Update version references to {release_version}",
                ],
                check=True,
            )
            print(f'Committed version updates for {release_version}')
    except subprocess.CalledProcessError as e:
        print(f"Error: failed to commit changes: {e}", file=sys.stderr)
        sys.exit(1)


def main():
    # Get paths
    readme_path = sys.argv[1] if len(sys.argv) > 1 else "./README.md"

    # Check if README exists
    if not os.path.isfile(readme_path):
        print(f'Error: README file not found at "{readme_path}".', file=sys.stderr)
        sys.exit(1)

    # Get release version
    release_version = get_release_version()
    print(f'Updating version references to {release_version}')

    # Track all files that need to be committed
    files_to_commit = []

    # Update the README
    print(f'\nUpdating README.md...')
    if update_readme_versions(readme_path, release_version):
        files_to_commit.append(readme_path)

    # Find and update example pom.xml files
    example_poms = find_example_pom_files()
    if example_poms:
        print(f'\nFound {len(example_poms)} example pom.xml file(s)')
        for pom_path in example_poms:
            print(f'Updating {pom_path}...')
            if update_pom_version(pom_path, release_version):
                files_to_commit.append(pom_path)
    else:
        print('No example pom.xml files found in ./examples/*/')

    # Commit all changes together if there are any
    if files_to_commit:
        print(f'\nCommitting {len(files_to_commit)} file(s)...')
        commit_files_if_changed(files_to_commit, release_version)
    else:
        print('\nNo files were modified.')


if __name__ == "__main__":
    main()