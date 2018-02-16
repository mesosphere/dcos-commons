#!/usr/bin/env python3

import base64
import collections
import difflib
import json
import logging
import os
import os.path
import pprint
import re
import sys
import tempfile
import universe
import urllib.request

log = logging.getLogger(__name__)
logging.basicConfig(level=logging.DEBUG, format="%(message)s")

# Prefix to be included at the start of stub universe URLs.
# Points to a translator service which automatically converts the package definition to match the requesting cluster's version.
universe_converter_url_prefix = 'https://universe-converter.mesosphere.com/transform?url='


class UniverseReleaseBuilder(object):

    @staticmethod
    def get_package_name(stub_universe_url: str) -> str:

        package_name = os.environ.get('PACKAGE_NAME', '')

        log.info("Read PACKAGE_NAME=%s", package_name)

        if not package_name:
            log.info("Getting package name from stub universe URL")

            name_match = re.match('.+/stub-universe-(.+).(json)$', stub_universe_url)
            if not name_match:
                raise Exception('Unable to extract package name from stub universe URL. '
                                'Expected filename of form "stub-universe-[pkgname].json"')

            package_name = name_match.group(1)
            log.info("Got package name %s from stub universe URL")

        return package_name

    @staticmethod
    def apply_beta_prefix(package_name: str, is_beta: bool) -> str:
        stripped_name = left_trim(package_name, 'beta-')
        if is_beta:
            log.info('Applying beta- prefix to %s', stripped_name)
            return 'beta-{}'.format(stripped_name)

        log.info('Using non-beta package name %s', stripped_name)
        return stripped_name

    @staticmethod
    def apply_beta_version(package_version: str, is_beta: bool) -> str:
        if is_beta:
            stripped_version = right_trim(package_version, '-beta')
            log.info('Applying -beta sufix to %s', stripped_version)
            return '{}-beta'.format(stripped_version)
        else:
            # complain if version has '-beta' suffix but BETA mode was disabled:
            if package_version.endswith('-beta'):
                raise Exception(
                    'Requested package version {} ends with "-beta", but BETA mode is disabled. '
                    'Either remove the "-beta" suffix, or enable BETA mode.'.format(package_version))

        return stripped_version

    def __init__(
            self,
            package_version,
            stub_universe_url,
            http_release_server=os.environ.get('HTTP_RELEASE_SERVER', 'https://downloads.mesosphere.com'),
            s3_release_bucket=os.environ.get('S3_RELEASE_BUCKET', 'downloads.mesosphere.io'),
            release_docker_image=os.environ.get('RELEASE_DOCKER_IMAGE'),
            release_dir_path=os.environ.get('RELEASE_DIR_PATH', ''),
            beta_release=os.environ.get('BETA', 'False'),
            upgrades_from=os.environ.get('UPGRADES_FROM')):
        self._dry_run = os.environ.get('DRY_RUN', '')
        self._force_upload = os.environ.get('FORCE_ARTIFACT_UPLOAD', '').lower() == 'true'
        self._beta_release = beta_release.lower() == 'true'

        self._stub_universe_pkg_name = self.get_package_name(stub_universe_url)
        self._pkg_name = self.apply_beta_prefix(self._stub_universe_pkg_name, self._beta_release)
        self._pkg_version = self.apply_beta_version(package_version, self._beta_release)

        # universe converter will return an HTTP 400 error because we aren't a DC/OS cluster. get the raw file instead.
        self._stub_universe_url = left_trim(stub_universe_url, universe_converter_url_prefix)

        if not release_dir_path:
            # determine release artifact directory based on (adjusted) package name
            # assets for beta-foo should always be uploaded to a 'foo' directory (with a '-beta' version)
            release_dir_path = left_trim(self._pkg_name, 'beta-') + '/assets'
            log.info("Uploading assets for %s to %s", self._pkg_name, release_dir_path)

        s3_directory_url = 's3://{}/{}/{}'.format(
            s3_release_bucket, release_dir_path, self._pkg_version)
        self._uploader = universe.S3Uploader(self._pkg_name, s3_directory_url, self._dry_run)
        self._pkg_manager = universe.PackageManager()

        self._http_directory_url = '{}/{}/{}'.format(
            http_release_server, release_dir_path, self._pkg_version)

        self._release_docker_image = release_docker_image or None
        self._upgrades_from = upgrades_from

        log.info('''###
Source URL:      {}
Package name:    {}
Package version: {}
Artifact output: {}
Upgrades from:   {}
###'''.format(self._stub_universe_url, self._pkg_name, self._pkg_version, self._http_directory_url, self._upgrades_from))

    def _run_cmd(self, cmd, exit_on_fail=True, dry_run_return=0):
        if self._dry_run:
            log.info('[DRY RUN] {}'.format(cmd))
            return dry_run_return
        else:
            log.info(cmd)
            ret = os.system(cmd)
            if ret != 0 and exit_on_fail:
                raise Exception("{} return non-zero exit status: {}".format(cmd, ret))
            return ret

    def _fetch_stub_universe(self):
        _, stub_universe_extension = os.path.splitext(self._stub_universe_url)
        if not stub_universe_extension == '.json':
            raise Exception('Expected .json extension for stub universe: {}'.format(
                self._stub_universe_url))

        with urllib.request.urlopen(self._stub_universe_url) as response:
            stub_universe_json = json.loads(
                response.read().decode(response.info().get_param('charset') or 'utf-8'),
                object_pairs_hook=collections.OrderedDict)
        return stub_universe_json

    def _unpack_stub_universe(self, stub_universe_json, scratchdir):
        '''Downloads a stub-universe.json URL and unpacks the content into a temporary directory.
        Returns the directory where the resulting files were unpacked.
        '''
        # put package files into a subdir of scratchdir: avoids conflicts with reuse of scratchdir elsewhere
        pkgdir = os.path.join(scratchdir, 'stub-universe-{}'.format(self._pkg_name))
        os.makedirs(pkgdir)

        if 'packages' not in stub_universe_json:
            raise Exception('Expected "packages" key in root of stub universe JSON: {}'.format(
                self._stub_universe_url))
        if len(stub_universe_json['packages']) != 1:
            raise Exception('Expected single "packages" entry in stub universe JSON: {}'.format(
                self._stub_universe_url))

        # note: we delete elements from package_json they're unpacked, as package_json is itself written to a file
        package_json = stub_universe_json['packages'][0]

        def extract_json_file(package_dict, name):
            file_dict = package_dict.get(name)
            if file_dict is not None:
                del package_dict[name]
                # ensure that the file has a trailing newline (json.dump() doesn't!)
                with open(os.path.join(pkgdir, name + '.json'), 'w') as fileref:
                    content = json.dumps(file_dict, indent=2)
                    fileref.write(content)
                    if not content.endswith('\n'):
                        fileref.write('\n')
        extract_json_file(package_json, 'command')
        extract_json_file(package_json, 'config')
        extract_json_file(package_json, 'resource')

        marathon_json = package_json.get('marathon', {}).get('v2AppMustacheTemplate')
        if marathon_json is not None:
            del package_json['marathon']
            with open(os.path.join(pkgdir, 'marathon.json.mustache'), 'w') as marathon_file:
                marathon_file.write(base64.standard_b64decode(marathon_json).decode())

        if 'releaseVersion' in package_json:
            del package_json['releaseVersion']

        with open(os.path.join(pkgdir, 'package.json'), 'w') as package_file:
            json.dump(package_json, package_file, indent=2)

        return pkgdir

    def _update_package_json(self, package_json):
        '''Updates the package.json definition to contain the desired version string,
        and updates the package to reflect any beta or non-beta status as necessary.
        '''
        orig_package_json = package_json.copy()

        # For beta releases, always clear 'selected'
        if self._beta_release:
            package_json['selected'] = False

        # Update package's name to reflect any changes due to BETA=on/off
        package_json['name'] = self._pkg_name
        # Update package's version to reflect the user's input
        package_json['version'] = self._pkg_version

        if self._upgrades_from is not None:
            package_json['upgradesFrom'] = [self._upgrades_from]
            package_json['downgradesTo'] = [self._upgrades_from]
        elif self._stub_universe_pkg_name != self._pkg_name and \
            (package_json.get('upgradesFrom', ['*']) != ['*'] or
             package_json.get('downgradesTo', ['*']) != ['*']):
            # Update package's upgradesFrom/downgradesTo to reflect any package name changes
            # due to enabling or disabling a beta bit.
            last_release = self._pkg_manager.get_latest(self._pkg_name)
            if last_release is None:
                # nothing to upgrade from
                package_json['upgradesFrom'] = []
                package_json['downgradesTo'] = []
            else:
                last_release_version = last_release.get_version().package_version
                package_json['upgradesFrom'] = [last_release_version]
                package_json['downgradesTo'] = [last_release_version]

        log.info('Updated package.json:')
        log.info('\n'.join(difflib.unified_diff(
            json.dumps(orig_package_json, indent=2).split('\n'),
            json.dumps(package_json, indent=2).split('\n'),
            lineterm='')))

    def _update_marathon_json(self, package_json):
        '''Updates the marathon.json definition to contain the desired name and version strings.
        '''
        # note: the file isn't valid JSON, so we edit the raw content instead
        marathon_encoded = package_json.get('marathon', {}).get('v2AppMustacheTemplate')
        orig_marathon_lines = base64.standard_b64decode(marathon_encoded).decode().split('\n')

        marathon_lines = []
        for line in orig_marathon_lines:
            name_match = re.match(r'^ *"PACKAGE_NAME": ?"(.*)",?$', line.rstrip('\n'))
            version_match = re.match(r'^ *"PACKAGE_VERSION": ?"(.*)",?$', line.rstrip('\n'))
            if name_match:
                line = line.replace(name_match.group(1), self._pkg_name)
            elif version_match:
                line = line.replace(version_match.group(1), self._pkg_version)
            marathon_lines.append(line)

        log.info('Updated marathon.json.mustache:')
        log.info('\n'.join(difflib.unified_diff(orig_marathon_lines, marathon_lines, lineterm='')))

        # Update parent package object with changes:
        package_json['marathon']['v2AppMustacheTemplate'] = base64.standard_b64encode(
            '\n'.join(marathon_lines).encode('utf-8')).decode()

    def _update_resource_json(self, package_json):
        '''Rewrites all artifact urls in pkgdir to self.release_artifact_http_dir.
        Returns the original urls.
        '''
        if 'resource' not in package_json:
            return []

        # we expect the artifacts to share the same directory prefix as the stub universe file itself:
        original_artifact_prefix = '/'.join(self._stub_universe_url.split('/')[:-1])
        log.info('Replacing artifact prefix {} with {}'.format(
            original_artifact_prefix, self._http_directory_url))
        # find all URLs in resource.json which match the directory of the stub universe file.
        # update those URLs to point to the new artifact path.
        orig_content = json.dumps(package_json['resource'], indent=2)
        original_artifact_urls = re.findall('({}/.+)\"'.format(original_artifact_prefix), orig_content)
        new_content = orig_content.replace(original_artifact_prefix, self._http_directory_url)
        package_json['resource'] = json.loads(new_content, object_pairs_hook=collections.OrderedDict)

        if self._release_docker_image:
            # Find the current docker image name in resource.json, update it to the new name:
            try:
                docker_dict = package_json['resource']['assets']['container']['docker']
                assert len(docker_dict) == 1
                orig_docker_image = list(docker_dict.values())[0]
                docker_dict[list(docker_dict.keys())[0]] = self._release_docker_image
            except KeyError:
                raise Exception('Release to docker specified, but no docker image found in resource.json')

            # Download/reupload docker image to target name:
            log.info('Downloading docker image {}'.format(orig_docker_image))
            self._run_cmd('docker pull {}'.format(orig_docker_image))
            self._run_cmd('docker tag {} {}'.format(orig_docker_image, self._release_docker_image))
            self._run_cmd('docker push {}'.format(self._release_docker_image))

        log.info('Updated resource.json:')
        log.info('\n'.join(difflib.unified_diff(
            orig_content.split('\n'),
            json.dumps(package_json['resource'], indent=2).split('\n'),
            lineterm='')))

        return original_artifact_urls

    def _update_package_get_artifacts(self, package_json):
        '''Updates the provided package JSON representation.

        Returns the list of original artifact URLs that were built with the package,
        and which would need to be reuploaded in a move or release.
        '''
        self._update_package_json(package_json)
        self._update_marathon_json(package_json)
        return self._update_resource_json(package_json)

    def _copy_artifacts_s3(self, scratchdir, original_artifact_urls):
        # Before we do anything else, verify that the upload directory doesn't already exist, to
        # avoid automatically stomping on a previous release. If you *want* to overwrite an existing
        # upload, you must manually delete the destination yourself, or set force=True when running
        # this tool.

        # NOTE: trailing slash needed to avoid false positives between e.g. '1.2.3' vs '1.2.3-beta'
        cmd = 'aws s3 ls --recursive {}/ 1>&2'.format(self._uploader.get_s3_directory())
        ret = self._run_cmd(cmd, False, 1)
        if ret == 0:
            if self._force_upload:
                log.info('Destination {} exists but force upload is configured, proceeding...'.format(
                    self._uploader.get_s3_directory()))
            else:
                raise Exception('Release artifact destination already exists. ' +
                                'Refusing to continue until destination has been manually removed:\n' +
                                'Do this: aws s3 rm --dryrun --recursive {}'.format(self._uploader.get_s3_directory()))
        elif ret > 256:
            raise Exception('Failed to check artifact destination presence (code {}). Bad AWS credentials? Exiting early.'.format(ret))
        else:
            log.info('Destination {} doesnt exist, proceeding...'.format(self._uploader.get_s3_directory()))

        for i in range(len(original_artifact_urls)):
            progress = '[{}/{}] '.format(i + 1, len(original_artifact_urls))
            src_url = original_artifact_urls[i]
            filename = src_url.split('/')[-1]

            local_path = os.path.join(scratchdir, filename)

            # download the artifact (dev s3, via http)
            if self._dry_run:
                # create stub file to make 'aws s3 cp --dryrun' happy:
                log.info('[DRY RUN] {}Downloading {} to {}'.format(progress, src_url, local_path))
                with open(local_path, 'w') as stub:
                    stub.write('stub')
            else:
                # download the artifact (http url referenced in package)
                log.info('{}Downloading {} to {}'.format(progress, src_url, local_path))
                urllib.request.URLopener().retrieve(src_url, local_path)

            # re-upload the artifact (prod s3, via awscli)
            log.info('{}Uploading {} to new location'.format(progress, local_path))
            self._uploader.upload(local_path)

            # delete the local temp copy
            os.unlink(local_path)

    def move_package(self):
        '''Updates package, puts artifacts in target location, and uploads updated stub-universe.json to target location.'''
        # Download stub universe:
        scratchdir = tempfile.mkdtemp(prefix='stub-universe-tmp')
        stub_universe_json = self._fetch_stub_universe()
        package_json = stub_universe_json['packages'][0]

        # Update stub universe:
        original_artifact_urls = self._update_package_get_artifacts(package_json)

        # Copy artifacts to new S3 location:
        self._copy_artifacts_s3(scratchdir, original_artifact_urls)

        # Upload updated stub-universe to new S3 location:
        stub_universe_filename = os.path.basename(self._stub_universe_url)
        updated_stub_universe_path = os.path.join(scratchdir, stub_universe_filename)
        with open(updated_stub_universe_path, 'w') as stub_universe_file:
            json.dump({'packages': [package_json]}, stub_universe_file, indent=2)
        self._uploader.upload(
            updated_stub_universe_path,
            content_type='application/vnd.dcos.universe.repo+json;charset=utf-8')
        return universe_converter_url_prefix + os.path.join(self._http_directory_url, stub_universe_filename)

    def release_package(self, commit_desc=''):
        '''Updates package, puts artifacts in target location, and creates Universe PR.'''

        # automatically include source universe URL in commit description:
        if commit_desc:
            commit_desc = '{}\n\nSource URL: {}'.format(commit_desc.rstrip('\n'), self._stub_universe_url)
        else:
            commit_desc = 'Source URL: {}'.format(self._stub_universe_url)

        # Create the publisher now to complain early about any missing envvars,
        # and avoid uploading a bunch of stuff to prod just to error out later:
        publisher = universe.UniversePackagePublisher(
            self._pkg_name,
            self._pkg_version,
            commit_desc,
            self._beta_release,
            dry_run=self._dry_run)

        scratchdir = tempfile.mkdtemp(prefix='stub-universe-tmp')

        # Download stub universe:
        stub_universe_json = self._fetch_stub_universe()
        package_json = stub_universe_json['packages'][0]

        # Update stub universe:
        original_artifact_urls = self._update_package_get_artifacts(package_json)

        # Copy artifacts to new S3 location:
        self._copy_artifacts_s3(scratchdir, original_artifact_urls)

        pkgdir = self._unpack_stub_universe(stub_universe_json, scratchdir)
        try:
            return publisher.publish(scratchdir, pkgdir)
        except:
            log.error(
                'Failed to create PR. '
                'Note that any release artifacts were already uploaded to {}, which must be manually deleted before retrying.'.format(self._uploader.get_s3_directory()))
            raise


def left_trim(string: str, prefix: str) -> str:
    """Left trim a prefix (if present) from a string."""
    if string.startswith(prefix):
        return string[len(prefix):]

    return string


def right_trim(string: str, suffix: str) -> str:
    """Right trim a suffix (if present) from a string."""
    if string.endswith(suffix):
        return string[:-len(suffix)]

    return string



def print_help(argv):
    log.info('Syntax: {} move|release <package-version> <stub-universe-url> [commit message]'.format(argv[0]))
    log.info('  Example: $ {} 1.2.3-4.5.6 https://example.com/path/to/stub-universe-kafka.json'.format(argv[0]))
    log.info('Required credentials in env:')
    log.info('- AWS S3: AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY')
    log.info('- Github (Personal Access Token): GITHUB_TOKEN (only required for release)')
    log.info('Optional params in env:')
    log.info('- BETA: true/false')
    log.info('- RELEASE_INDEX: a specific Universe package index, for hotfixes (else autodetected)')
    log.info('- DRY_RUN: true/false')
    log.info('- FORCE_ARTIFACT_UPLOAD: true/false')
    log.info('- HTTP_RELEASE_SERVER: https://downloads.mesosphere.com')
    log.info('- S3_RELEASE_BUCKET: downloads.mesosphere.io')
    log.info('- RELEASE_DIR_PATH: <package-name>/assets')
    log.info('Required CLI programs:')
    log.info('- aws')
    log.info('- git (for release)')


def main(argv):
    if len(argv) < 4:
        print_help(argv)
        return 1
    operation = argv[1].strip()
    # the package version:
    package_version = argv[2].strip()
    # url where the stub universe is located:
    stub_universe_url = argv[3].strip().rstrip('/')
    # commit comment, if any:
    commit_desc = ' '.join(argv[4:])

    builder = UniverseReleaseBuilder(package_version, stub_universe_url)
    if operation == 'release':
        response = builder.release_package(commit_desc)
        if not response:
            # print the PR location as stdout for use upstream (the rest is all stderr):
            print('[DRY RUN] The pull request URL would appear here.')
            return 0
        if response.status < 200 or response.status >= 300:
            log.error('Got {} response to PR creation request:'.format(response.status))
            log.error('Response:')
            log.error(pprint.pformat(response.read()))
            log.error('You will need to manually create the PR against the branch that was pushed above.')
            return -1
        log.info('---')
        log.info('Created pull request for version {} (PTAL):'.format(package_version))
        # print the PR location as stdout for use upstream (the rest is all stderr):
        response_content = response.read().decode(response.info().get_param('charset') or 'utf-8')
        print(json.loads(response_content)['html_url'])
    elif operation == 'move':
        new_stub_url = builder.move_package()
        print('Package moved to: {}'.format(new_stub_url))
    else:
        print_help(argv)
        return 1
    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv))
