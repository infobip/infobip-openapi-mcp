#!/usr/bin/env python3

import os
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


def extract_unreleased_section(changelog_path):
    """
    Extract the content under the [Unreleased] section from the changelog.
    Returns the extracted content as a string.
    """
    unreleased_content = []
    inside_unreleased = False
    found_unreleased = False

    with open(changelog_path, "r", encoding="utf-8") as f:
        for line in f:
            # Check if we found the [Unreleased] section
            if line.strip().startswith("## ") and "[Unreleased]" in line:
                inside_unreleased = True
                found_unreleased = True
                continue

            # Check if we hit the next section (another ## heading)
            if inside_unreleased and line.strip().startswith("## "):
                break

            # Collect lines inside the unreleased section
            if inside_unreleased:
                unreleased_content.append(line)

    if not found_unreleased:
        print(
            f'Error: "## [Unreleased]" section not found in "{changelog_path}".',
            file=sys.stderr,
        )
        sys.exit(1)

    return "".join(unreleased_content)


def update_changelog(changelog_path, release_version):
    """
    Update the changelog by adding a new release section under [Unreleased].
    Returns True if the file was modified, False otherwise.
    """
    updated_lines = []
    updated = False

    with open(changelog_path, "r", encoding="utf-8") as f:
        for line in f:
            # Replace the [Unreleased] line with itself plus the new release section
            if not updated and line.strip() == "## [Unreleased]":
                updated_lines.append("## [Unreleased]\n")
                updated_lines.append("\n")
                updated_lines.append(f"## {release_version}\n")
                updated = True
                continue

            updated_lines.append(line)

    if not updated:
        print(
            f'Error: failed to update release heading in "{changelog_path}".',
            file=sys.stderr,
        )
        sys.exit(1)

    # Write the updated content back to the file
    with open(changelog_path, "w", encoding="utf-8") as f:
        f.writelines(updated_lines)

    return True


def commit_changelog_if_changed(changelog_path, release_version):
    """
    Commit the changelog if it has changes.
    """
    try:
        # Check if there are changes
        result = subprocess.run(
            ["git", "diff", "--quiet", "--", changelog_path], capture_output=True
        )

        if result.returncode == 0:
            # No changes
            print(f'No changes detected in "{changelog_path}" to commit.')
        else:
            # Changes detected, commit them
            subprocess.run(["git", "add", changelog_path], check=True)
            subprocess.run(
                [
                    "git",
                    "commit",
                    "-m",
                    f"Update changelog for release {release_version}",
                ],
                check=True,
            )
    except subprocess.CalledProcessError as e:
        print(f"Error: failed to commit changelog changes: {e}", file=sys.stderr)
        sys.exit(1)


def main():
    # Get paths
    changelog_path = sys.argv[1] if len(sys.argv) > 1 else "./CHANGELOG.md"
    release_notes_path = "./release_notes.md"

    # Check if changelog exists
    if not os.path.isfile(changelog_path):
        print(
            f'Error: changelog file not found at "{changelog_path}".', file=sys.stderr
        )
        sys.exit(1)

    # Get release version
    release_version = get_release_version()

    # Create target directory if needed
    os.makedirs(os.path.dirname(release_notes_path), exist_ok=True)

    # Extract unreleased section and save to release notes
    try:
        unreleased_content = extract_unreleased_section(changelog_path)
        with open(release_notes_path, "w", encoding="utf-8") as f:
            f.write(unreleased_content)
    except Exception as e:
        if os.path.exists(release_notes_path):
            os.remove(release_notes_path)
        raise

    # Update the changelog
    try:
        update_changelog(changelog_path, release_version)
    except Exception as e:
        # Clean up on error
        if os.path.exists(release_notes_path):
            os.remove(release_notes_path)
        raise

    # Commit if there are changes
    commit_changelog_if_changed(changelog_path, release_version)


if __name__ == "__main__":
    main()
