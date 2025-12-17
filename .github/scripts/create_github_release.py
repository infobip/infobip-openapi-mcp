#!/usr/bin/env python3
import json
import os
import re
import subprocess
import sys
import urllib.error
import urllib.request

def get_latest_tag():
    """Get the latest git tag."""
    try:
        result = subprocess.run(
            ["git", "describe", "--tags", "--abbrev=0"],
            capture_output=True,
            text=True,
            check=True,
        )
        tag = result.stdout.strip()
        if not tag:
            raise ValueError("No git tags found")
        return tag
    except subprocess.CalledProcessError as e:
        print(f"Error getting latest git tag: {e}", file=sys.stderr)
        print("Make sure the repository has at least one tag", file=sys.stderr)
        sys.exit(1)
    except Exception as e:
        print(f"Error getting latest git tag: {e}", file=sys.stderr)
        sys.exit(1)


def parse_version_from_tag(tag):
    """Parse version from tag in format 'infobip-openapi-mcp-<version>'."""
    prefix = "infobip-openapi-mcp-"
    if tag.startswith(prefix):
        version = tag[len(prefix) :]
        return version
    else:
        raise ValueError(
            f"Tag '{tag}' does not match expected format '{prefix}<version>'"
        )


def read_release_notes(file_path):
    """Read release notes from the specified file."""
    try:
        with open(file_path, "r", encoding="utf-8") as f:
            return f.read()
    except FileNotFoundError:
        print(f"Error: Release notes file not found: {file_path}", file=sys.stderr)
        sys.exit(1)
    except Exception as e:
        print(f"Error reading release notes: {e}", file=sys.stderr)
        sys.exit(1)


def create_github_release(token, owner, repo, tag_name, release_name, body):
    """Create a GitHub release using the GitHub API."""
    url = f"https://api.github.com/repos/{owner}/{repo}/releases"

    headers = {
        "Authorization": f"Bearer {token}",
        "Accept": "application/vnd.github.v3+json",
        "Content-Type": "application/json",
        "X-GitHub-Api-Version": "2022-11-28",
    }

    data = {
        "tag_name": tag_name,
        "name": release_name,
        "body": body,
        "draft": False,
        "prerelease": False,
    }

    try:
        req = urllib.request.Request(
            url, data=json.dumps(data).encode("utf-8"), headers=headers, method="POST"
        )

        with urllib.request.urlopen(req) as response:
            response_data = json.loads(response.read().decode("utf-8"))
            print(f"Successfully created release: {response_data['name']}")
            print(f"Release URL: {response_data['html_url']}")
            return response_data

    except urllib.error.HTTPError as e:
        error_body = e.read().decode("utf-8")
        print(f"HTTP Error {e.code}: {e.reason}", file=sys.stderr)
        print(f"Response: {error_body}", file=sys.stderr)
        sys.exit(1)
    except Exception as e:
        print(f"Error creating release: {e}", file=sys.stderr)
        sys.exit(1)


def main():
    # Get required environment variables
    github_token = os.getenv("GITHUB_TOKEN")

    if not github_token:
        print("Error: GITHUB_TOKEN environment variable is not set", file=sys.stderr)
        sys.exit(1)

    # Get repository information
    owner = "infobip"
    repo = "infobip-openapi-mcp"
    print(f"Repository: {owner}/{repo}")

    # Get the latest tag and extract version
    tag_name = get_latest_tag()
    print(f"Latest git tag: {tag_name}")

    version = parse_version_from_tag(tag_name)
    print(f"Release version: {version}")

    # Read release notes
    release_notes_path = "./release_notes.md"
    release_notes = read_release_notes(release_notes_path)
    print(f"Read release notes from {release_notes_path}")

    # Create the release
    release_name = f"Release {version}"

    create_github_release(
        token=github_token,
        owner=owner,
        repo=repo,
        tag_name=tag_name,
        release_name=release_name,
        body=release_notes,
    )


if __name__ == "__main__":
    main()
