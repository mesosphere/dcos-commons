#!/usr/bin/env python3

import base64
import collections
import difflib
import hashlib
import json
import logging
import os
import os.path
import re
import tempfile
import time
import urllib.request

logger = logging.getLogger(__name__)
logging.basicConfig(level=logging.DEBUG, format="%(message)s")

_jre_url = "https://downloads.mesosphere.com/java/openjdk-jre-8u212b03-hotspot-linux-x64.tar.gz"
_libmesos_bundle_url = (
    "https://downloads.mesosphere.com/libmesos-bundle/libmesos-bundle-1.12.0.tar.gz"
)
_docs_root = "https://docs.mesosphere.com"

_config_json_filename = "config.json"
_marathon_json_filename = "marathon.json.mustache"
_package_json_filename = "package.json"
_resource_json_filename = "resource.json"
_expected_package_filenames = [
    _config_json_filename,
    _marathon_json_filename,
    _package_json_filename,
    _resource_json_filename,
]


class UniversePackageBuilder(object):
    def __init__(
        self,
        package,
        package_manager,
        input_dir_path,
        upload_dir_uri,
        artifact_paths,
        dry_run=False,
    ):

        self._dry_run = dry_run
        self._package = package
        self._package_manager = package_manager
        self._upload_dir_uri = upload_dir_uri

        self.set_input_dir_path(input_dir_path)

        self._artifact_file_paths = {}
        for artifact_path in artifact_paths:
            if not os.path.isfile(artifact_path):
                raise Exception(
                    "Provided package path is not a file: {} (full list: {})".format(
                        artifact_path, artifact_paths
                    )
                )
            prior_path = self._artifact_file_paths.get(os.path.basename(artifact_path), "")
            if prior_path:
                raise Exception(
                    'Duplicate filename between "{}" and "{}". Artifact filenames must be unique.'.format(
                        prior_path, artifact_path
                    )
                )
            self._artifact_file_paths[os.path.basename(artifact_path)] = artifact_path

    def set_input_dir_path(self, input_dir_path):
        """Validate and set the input directory path"""
        if not os.path.isdir(input_dir_path):
            raise Exception("Provided package path is not a directory: {}".format(input_dir_path))

        if not os.path.isfile(os.path.join(input_dir_path, _package_json_filename)):
            raise Exception(
                "Provided package path does not contain the expected package files: {}".format(
                    input_dir_path
                )
            )

        self._input_dir_path = input_dir_path

    def _iterate_package_files(self):
        for package_filename in os.listdir(self._input_dir_path):
            package_filepath = os.path.join(self._input_dir_path, package_filename)
            if os.stat(package_filepath).st_size > (1024 * 1024):
                logger.warning("Ignoring package file larger than 1MB: {}".format(package_filepath))
                continue
            if package_filename not in _expected_package_filenames:
                logger.warning(
                    "Ignoring unrecognized package file: {} (expected one of: {})".format(
                        package_filepath, ", ".join(_expected_package_filenames)
                    )
                )
                continue
            yield package_filename, open(package_filepath).read()

    def _fetch_sha256_from_manifest(self, manifest_url, filename):
        logger.info("Fetching manifest for %s from %s", filename, manifest_url)

        if self._dry_run:
            logger.info("(dryrun) Generating hash for DRY_RUN")
            hasher = hashlib.sha256()
            hasher.update(manifest_url.encode("utf-8"))
            hasher.update(filename.encode("utf-8"))
            return hasher.hexdigest()

        with urllib.request.urlopen(manifest_url) as manifest_file:
            manifest_content = manifest_file.read(10240).decode("utf-8").strip()
        for manifest_row in manifest_content.split("\n"):
            cols = manifest_row.split()
            if len(cols) != 2:
                logger.warning(
                    "Expected manifest entry to have 2 columns: {} => {}".format(manifest_row, cols)
                )
                continue
            # filenames may have a '*' prefix to indicate a binary:
            if cols[1] == filename or cols[1] == "*{}".format(filename):
                return cols[0]
        raise Exception(
            "No entry found for {} in manifest at {}:\n{}".format(
                filename, manifest_url, manifest_content
            )
        )

    def _calculate_sha256(self, filepath):
        BLOCKSIZE = 65536
        hasher = hashlib.sha256()
        with open(filepath, "rb") as fd:
            buf = fd.read(BLOCKSIZE)
            while len(buf) > 0:
                hasher.update(buf)
                buf = fd.read(BLOCKSIZE)
        return hasher.hexdigest()

    def _get_documentation_path(self):
        documentation_path = "{}/service-docs/{}/".format(_docs_root, self._package.get_name())
        package_version = str(self._package.get_version())
        if package_version != "stub-universe":
            documentation_path = "{}v{}/".format(documentation_path, package_version)

        return documentation_path

    def _get_issues_path(self):
        return "{}/support/".format(_docs_root)

    def _get_upgrades_from(self):
        latest_package = self._package_manager.get_latest(self._package)

        if latest_package is None:
            return "*"

        return str(latest_package.get_version())

    def _get_downgrades_to(self):
        return self._get_upgrades_from()

    def _get_template_mapping_for_content(self, orig_content):
        """Returns a template mapping (dict) for the following cases:
        - Default params like '{{package-version}}' and '{{artifact-dir}}'
        - SHA256 params like '{{sha256:artifact.zip}}' (requires user-provided paths to artifact files)
        - Custom environment params like 'TEMPLATE_SOME_PARAM' which maps to '{{some-param}}'
        """
        # default template values (may be overridden via eg TEMPLATE_PACKAGE_VERSION envvars):
        now = time.time()
        template_mapping = {
            "package-name": self._package.get_name(),
            "package-version": str(self._package.get_version()),
            "package-build-time-epoch-ms": str(int(round(now * 1000))),
            "package-build-time-str": time.strftime("%a %b %d %Y %H:%M:%S +0000", time.gmtime(now)),
            "upgrades-from": self._get_upgrades_from(),
            "downgrades-to": self._get_downgrades_to(),
            "artifact-dir": self._upload_dir_uri,
            "documentation-path": self._get_documentation_path(),
            "issues-path": self._get_issues_path(),
            "jre-url": _jre_url,
            "libmesos-bundle-url": _libmesos_bundle_url
        }

        # import any custom "TEMPLATE_SOME_PARAM" environment variables as "some-param":
        for env_key, env_val in os.environ.items():
            if env_key.startswith("TEMPLATE_"):
                # 'TEMPLATE_SOME_KEY' => 'some-key'
                template_mapping[env_key[len("TEMPLATE_") :].lower().replace("_", "-")] = env_val

        sha_template_maps = self._get_sha_template_mapping(orig_content, template_mapping)

        for key, value in sha_template_maps.items():
            template_mapping[key] = value

        return template_mapping

    def _get_sha_template_mapping(self, content: str, template_mapping: dict) -> dict:
        """
        Look for any 'sha256:filename' or 'sha256:filename@url' template params, and get shas for those.
            - "sha256:filename": generate SHA256 of local file which was specified as an artifact
            - "sha256:filename@manifesturl": download checksum manifest at URL, and use sha listed in there for filename
        """
        sha_template_maps = {}
        url_matcher = re.compile("^(.+?)@(.+?)$")  # filename@url
        for sha_param in re.findall('"{{sha256:(.+?)}}"', content):
            sha_param_templated = self._apply_template_to_string(sha_param, template_mapping)
            url_match = url_matcher.match(sha_param_templated)
            if url_match:
                # fetch remote manifest at URL, get sha256 from manifest
                target_filename = url_match.group(1)
                manifest_url = url_match.group(2)
                sha_value = self._fetch_sha256_from_manifest(manifest_url, target_filename)
            else:
                # find local file with specified name, get sha256 for that file
                target_file_path = self._artifact_file_paths.get(sha_param_templated, "")
                if not target_file_path:
                    raise Exception(
                        "Missing path for artifact file named '{}' (to calculate sha256). ".format(
                            sha_param_templated
                        )
                        + "Please provide the full path to this artifact (known artifacts: {}), ".format(
                            self._artifact_file_paths
                        )
                        + "or specify a manifest URL (SHA256SUMS) with '{}@<manifestURL>'".format(
                            sha_param_templated
                        )
                    )
                sha_value = self._calculate_sha256(target_file_path)

            # We add both the templated and non-templated sha patterns to the maps.
            sha_template_maps["sha256:{}".format(sha_param_templated)] = sha_value
            sha_template_maps["sha256:{}".format(sha_param)] = sha_value

        return sha_template_maps

    @staticmethod
    def _apply_template_to_string(content: str, template_mapping: dict) -> str:
        new_content = content
        prior_content = None
        while prior_content != new_content:
            prior_content = new_content
            for template_key, template_val in template_mapping.items():
                new_content = new_content.replace("{{%s}}" % template_key, template_val)

        return new_content

    def _apply_templating_to_file(self, filename, orig_content):
        template_mapping = self._get_template_mapping_for_content(orig_content)
        new_content = self._apply_template_to_string(orig_content, template_mapping)

        if orig_content == new_content:
            logger.info("")
            logger.info("No templating detected in {}, leaving file as-is".format(filename))
            return orig_content
        logger.info("")
        logger.info("Applied templating changes to {}:".format(filename))
        logger.info("Template params used:")
        template_keys = list(template_mapping.keys())
        template_keys.sort()
        for key in template_keys:
            logger.info("  {{%s}} => %s" % (key, template_mapping[key]))
        logger.info("Resulting diff:")
        logger.info(
            "\n".join(
                difflib.unified_diff(orig_content.split("\n"), new_content.split("\n"), lineterm="")
            )
        )
        return new_content

    def _generate_packages_dict(self, package_files):
        package_json = json.loads(
            package_files[_package_json_filename], object_pairs_hook=collections.OrderedDict
        )
        package_json["releaseVersion"] = 0

        config_json = package_files.get(_config_json_filename)
        if config_json is not None:
            package_json["config"] = json.loads(
                config_json, object_pairs_hook=collections.OrderedDict
            )

        marathon_json = package_files.get(_marathon_json_filename)
        if marathon_json is not None:
            package_json["marathon"] = {
                "v2AppMustacheTemplate": base64.standard_b64encode(
                    bytearray(marathon_json, "utf-8")
                ).decode()
            }

        resource_json = package_files.get(_resource_json_filename)
        if resource_json is not None:
            package_json["resource"] = json.loads(
                resource_json, object_pairs_hook=collections.OrderedDict
            )

        return {"packages": [package_json]}

    def build_package_files(self):
        """builds package files and returns a dict containing them"""
        # read files into memory and apply templating to files:
        updated_package_files = {}
        for filename, content in self._iterate_package_files():
            updated_package_files[filename] = self._apply_templating_to_file(filename, content)
        return updated_package_files

    def build_package(self):
        """builds a stub universe json package and returns its location on disk"""

        jsonpath = os.path.join(
            tempfile.mkdtemp(prefix="stub-universe-tmp"),
            "stub-universe-{}.json".format(self._package.get_name()),
        )
        with open(jsonpath, "w") as jsonfile:
            json.dump(self._generate_packages_dict(self.build_package_files()), jsonfile, indent=2)
        return jsonpath
