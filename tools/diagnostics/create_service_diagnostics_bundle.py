#!/usr/bin/env python3

# Dependencies:
# - DC/OS CLI

# Notes:
# - When run in the dcos-commons Docker image the DC/OS CLI version is specified either in
# - the image's test_requirements.txt, frozen_requirements.txt or Dockerfile.

from typing import Any
import argparse
import json
import logging
import sys

from full_bundle import FullBundle
import sdk_cmd

log = logging.getLogger(__name__)


def current_cluster_name() -> (bool, str):
    rc, stdout, stderr = sdk_cmd.run_cli("config show cluster.name", print_output=False)

    if rc != 0:
        if "Property 'cluster.name' doesn't exist" in stderr:
            return (
                False,
                "No cluster is set up. Please run `dcos cluster setup`\nstdout: '{}'\nstderr: '{}'".format(
                    stdout, stderr
                ),
            )
        else:
            return (False, "Unexpected error\nstdout: '{}'\nstderr: '{}'".format(stdout, stderr))

    return (True, stdout)


def is_authenticated_to_dcos_cluster() -> (bool, str):
    rc, stdout, stderr = sdk_cmd.run_cli("service", print_output=False)

    if rc != 0:
        (success, cluster_name_or_error) = current_cluster_name()

        if any(s in stderr for s in ("dcos auth login", "Missing required config parameter")):
            if success:
                return (
                    False,
                    "Not authenticated to {}. Please run `dcos auth login`".format(
                        cluster_name_or_error
                    ),
                )
            else:
                return (False, cluster_name_or_error)
        else:
            return (False, "Unexpected error\nstdout: '{}'\nstderr: '{}'".format(stdout, stderr))

    return (True, "Authenticated")


def attached_dcos_cluster() -> (int, Any):
    rc, stdout, stderr = sdk_cmd.run_cli("cluster list --attached --json", print_output=False)

    try:
        attached_clusters = json.loads(stdout)
    except json.JSONDecodeError as e:
        return (1, "Error decoding JSON while getting attached DC/OS cluster: {}".format(e))

    if rc == 0:
        if len(attached_clusters) == 0:
            return (1, "No cluster is attached")
        if len(attached_clusters) > 1:
            return (
                1,
                "More than one attached clusters. This is an invalid DC/OS CLI state\n{}".format(
                    stdout
                ),
            )
    else:
        if "No cluster is attached" in stderr:
            return (rc, stderr)
        else:
            return (False, "Unexpected error\nstdout: '{}'\nstderr: '{}'".format(stdout, stderr))

    # Having more than one attached cluster is an invalid DC/OS CLI state that is handled above.
    # In normal conditions `attached_clusters` is an unary array.
    attached_cluster = attached_clusters[0]

    return (rc, attached_cluster)


def get_marathon_app(service_name: str) -> (int, Any):
    rc, stdout, stderr = sdk_cmd.run_cli(
        "marathon app show {}".format(service_name), print_output=False
    )

    if rc != 0:
        if "does not exist" in stderr:
            return (rc, "Service {} does not exist".format(service_name))
        else:
            return (rc, "Unexpected error\nstdout: '{}'\nstderr: '{}'".format(stdout, stderr))

    try:
        return (rc, json.loads(stdout))
    except json.JSONDecodeError as e:
        return (1, "Error decoding JSON: {}".format(e))


def parse_args() -> dict:
    parser = argparse.ArgumentParser(description="Create an SDK service Diagnostics bundle")

    parser.add_argument(
        "--package-name",
        type=str,
        required=True,
        default=None,
        help="The package name for the service to create the bundle for",
    )

    parser.add_argument(
        "--service-name",
        type=str,
        required=True,
        default=None,
        help="The service name to create the bundle for",
    )

    parser.add_argument(
        "--bundles-directory",
        type=str,
        required=True,
        default=None,
        help="The directory where bundles will be written to",
    )

    parser.add_argument(
        "--yes",
        action="store_true",
        help="Disable interactive mode and assume 'yes' is the answer to all prompts.",
    )

    return parser.parse_args()


def preflight_check() -> (int, dict):
    args = parse_args()
    package_name_given = args.package_name
    service_name = args.service_name
    bundles_directory = args.bundles_directory
    should_prompt_user = not args.yes

    (is_authenticated, message) = is_authenticated_to_dcos_cluster()
    if not is_authenticated:
        log.error(
            "We were unable to verify that you're authenticated to a DC/OS cluster.\nError: %s",
            message,
        )
        return (1, {})

    (rc, cluster_or_error) = attached_dcos_cluster()
    if rc != 0:
        log.error(
            "We were unable to verify the cluster you're attached to.\nError: %s", cluster_or_error
        )
        return (rc, {})

    cluster = cluster_or_error

    (rc, marathon_app_or_error) = get_marathon_app(service_name)
    if rc == 0:
        package_name = marathon_app_or_error.get("labels", {}).get("DCOS_PACKAGE_NAME")
        package_version = marathon_app_or_error.get("labels", {}).get("DCOS_PACKAGE_VERSION")
    else:
        log.info(
            "We were unable to get details about '%s'.\nIssue: %s",
            service_name,
            marathon_app_or_error,
        )
        log.info(
            "Maybe the '%s' scheduler is not running. That's ok, we can still try to fetch any "
            + "artifacts related to it",
            service_name,
        )
        package_name = package_name_given
        package_version = "n/a"

    if package_name_given != package_name:
        log.error(
            "Package name given '%s' is different than actual '%s' package name: '%s'",
            package_name_given,
            service_name,
            package_name,
        )
        log.info("Try '--package-name=%s'", package_name)
        return (1, {})

    return (
        0,
        {
            "package_name": package_name,
            "service_name": service_name,
            "package_version": package_version,
            "cluster_name": cluster["name"],
            "bundles_directory": bundles_directory,
            "dcos_version": cluster["version"],
            "cluster_url": cluster["url"],
            "should_prompt_user": should_prompt_user,
        },
    )


def main(argv) -> int:
    rc, args = preflight_check()
    if rc != 0:
        return rc

    print("\nWill create bundle for:")
    print("  Package:         {}".format(args.get("package_name")))
    print("  Package version: {}".format(args.get("package_version")))
    print("  Service name:    {}".format(args.get("service_name")))
    print("  DC/OS version:   {}".format(args.get("dcos_version")))
    print("  Cluster URL:     {}".format(args.get("cluster_url")))

    if args.get("should_prompt_user"):
        answer = input("\nProceed? [Y/n]: ")
        if answer.strip().lower() not in ["yes", "y", ""]:
            return 0

    rc, _ = FullBundle(
        args.get("package_name"), args.get("service_name"), args.get("bundles_directory")
    ).create()

    return rc


if __name__ == "__main__":
    sys.exit(main(sys.argv))
