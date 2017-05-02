#!/usr/bin/env python3
#
# Hosts artifacts via a local HTTP service.
# Produces a universe, and puts it in a host dir, then runs an HTTP server against that dir.
#
# Env:
#   HTTP_DIR (default: /tmp/dcos-http-<pkgname>/)
#   HTTP_HOST (default: 172.17.0.1, which is the ip of the VM when running dcos-docker)
#   HTTP_PORT (default: 0, for an ephemeral port)

import json
import logging
import os
import os.path
import shutil
import socket
import subprocess
import sys

import github_update
import universe_builder

logger = logging.getLogger(__name__)
logging.basicConfig(level=logging.DEBUG, format="%(message)s")


class HTTPPublisher(object):

    def __init__(
            self,
            package_name,
            input_dir_path,
            artifact_paths,
            package_version = 'stub-universe'):
        self._pkg_name = package_name
        self._pkg_version = package_version
        self._input_dir_path = input_dir_path

        self._http_dir = os.environ.get('HTTP_DIR', '/tmp/dcos-http-{}/'.format(package_name))
        self._http_host = os.environ.get('HTTP_HOST', '172.17.0.1')
        self._http_port = int(os.environ.get('HTTP_PORT', '0'))

        self._github_updater = github_update.GithubStatusUpdater('upload:{}'.format(package_name))

        if not os.path.isdir(input_dir_path):
            err = 'Provided package path is not a directory: {}'.format(input_dir_path)
            self._github_updater.update('error', err)
            raise Exception(err)

        self._artifact_paths = []
        for artifact_path in artifact_paths:
            if not os.path.isfile(artifact_path):
                err = 'Provided package path is not a file: {} (full list: {})'.format(artifact_path, artifact_paths)
                raise Exception(err)
            if artifact_path in self._artifact_paths:
                err = 'Duplicate filename between "{}" and "{}". Artifact filenames must be unique.'.format(prior_path, artifact_path)
                self._github_updater.update('error', err)
                raise Exception(err)
            self._artifact_paths.append(artifact_path)


    def _copy_artifact(self, http_url_root, filepath):
        filename = os.path.basename(filepath)
        destpath = os.path.join(self._http_dir, filename)
        logger.info('- {}'.format(destpath))
        shutil.copyfile(filepath, destpath)
        return '{}/{}'.format(http_url_root, filename)


    def _spam_universe_url(self, universe_url):
        # write jenkins properties file to $WORKSPACE/<pkg_version>.properties:
        jenkins_workspace_path = os.environ.get('WORKSPACE', '')
        if jenkins_workspace_path:
            properties_file = open(os.path.join(jenkins_workspace_path, '{}.properties'.format(self._pkg_version)), 'w')
            properties_file.write('STUB_UNIVERSE_URL={}\n'.format(universe_url))
            properties_file.write('STUB_UNIVERSE_S3_DIR={}\n'.format(self._s3_directory))
            properties_file.flush()
            properties_file.close()
        # write URL to provided text file path:
        universe_url_path = os.environ.get('UNIVERSE_URL_PATH', '')
        if universe_url_path:
            universe_url_file = open(universe_url_path, 'w')
            universe_url_file.write('{}\n'.format(universe_url))
            universe_url_file.flush()
            universe_url_file.close()
        num_artifacts = len(self._artifact_paths)
        if num_artifacts > 1:
            suffix = 's'
        else:
            suffix = ''
        self._github_updater.update(
            'success',
            'Copied stub universe and {} artifact{}'.format(num_artifacts, suffix),
            universe_url)


    def build(self, http_url_root):
        '''copies artifacts and a new stub universe into the http root directory'''
        try:
            universe_path = self._package_builder.build_package()
        except Exception as e:
            err = 'Failed to create stub universe: {}'.format(str(e))
            self._github_updater.update('error', err)
            raise

        # wipe files in dir
        if not os.path.isdir(self._http_dir):
            os.makedirs(self._http_dir)
        for filename in os.listdir(self._http_dir):
            path = os.path.join(self._http_dir, filename)
            logger.info('Deleting preexisting file in artifact dir: {}'.format(path))
            os.remove(path)

        # print universe url early
        universe_url = self._copy_artifact(http_url_root, universe_path)
        logger.info('---')
        logger.info('Built and copied stub universe:')
        logger.info(universe_url)
        logger.info('---')
        logger.info('Copying {} artifacts into {}:'.format(len(self._artifact_paths), self._http_dir))

        for path in self._artifact_paths:
            self._copy_artifact(http_url_root, path)

        self._spam_universe_url(universe_url)

        # print to stdout, while the rest is all stderr:
        print(universe_url)

        return universe_url

    def launch_http(self):
        # kill any prior matching process
        procname = 'publish_httpd_{}.py'.format(self._pkg_name)
        try:
            subprocess.check_call('killall -9 {}'.format(procname).split())
            logger.info("Killed previous HTTP process(es): {}".format(procname))
        except:
            logger.info("No previous HTTP process found: {}".format(procname))

        if self._http_port == 0:
            # hack: grab/release a suitable ephemeral port and hope nobody steals it in the meantime
            sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            sock.bind((self._http_host, 0))
            port = sock.getsockname()[1]
            sock.close()
        else:
            port = self._http_port

        http_url_root = 'http://{}:{}'.format(self._http_host, port)

        self._package_builder = universe_builder.UniversePackageBuilder(
            self._pkg_name, self._pkg_version,
            self._input_dir_path, http_url_root, self._artifact_paths)

        # hack: write httpd script then run it directly
        httpd_py_content = '''#!/usr/bin/env python3
import os, socketserver
from http.server import SimpleHTTPRequestHandler
rootdir = '{}'
host = '{}'
port = {}
json_content_type = '{}'

class CustomTypeHandler(SimpleHTTPRequestHandler):
    def __init__(self, req, client_addr, server):
        SimpleHTTPRequestHandler.__init__(self, req, client_addr, server)
    def guess_type(self, path):
        if path.endswith('.json'):
            return json_content_type
        return SimpleHTTPRequestHandler.guess_type(self, path)

os.chdir(rootdir)
httpd = socketserver.TCPServer((host, port), CustomTypeHandler)
print('Serving %s at http://%s:%s' % (rootdir, host, port))
httpd.serve_forever()
'''.format(self._http_dir, self._http_host, port, self._package_builder.content_type())

        httpd_py_path = os.path.join(self._http_dir, procname)
        if not os.path.isdir(self._http_dir):
            os.makedirs(self._http_dir)
        httpd_py_file = open(httpd_py_path, 'w+')
        httpd_py_file.write(httpd_py_content)
        httpd_py_file.flush()
        httpd_py_file.close()

        os.chmod(httpd_py_path, 0o744)
        logger.info('Launching HTTPD: {}'.format(httpd_py_path))
        subprocess.Popen([httpd_py_path, "2&1>", "/dev/null"])

        return http_url_root

    def add_repo_to_cli(self, repo_url):
        try:
            devnull = open(os.devnull,'wb')
            subprocess.check_call('dcos -h'.split(), stdout=devnull, stderr=devnull)
        except:
            logger.info('No "dcos" command in $PATH, skipping automatic repo configuration')
            return False

        repo_name = self._pkg_name + '-local'
        # check for any preexisting universes and remove them -- the cluster requires no duplicate uris
        logger.info('Checking for duplicate repositories: name={}, url={}'.format(repo_name, repo_url))
        cur_universes = subprocess.check_output('dcos package repo list --json'.split()).decode('utf-8')
        for repo in json.loads(cur_universes)['repositories']:
            # {u'name': u'Universe', u'uri': u'https://universe.mesosphere.com/repo'}
            if repo['name'] == repo_name or repo['uri'] == repo_url:
                logger.info('Removing duplicate repository: {} {}'.format(repo['name'], repo['uri']))
                subprocess.check_call('dcos package repo remove {}'.format(repo['name']).split())
        logger.info('Adding repository: {} {}'.format(repo_name, repo_url))
        subprocess.check_call('dcos package repo add --index=0 {} {}'.format(repo_name, repo_url).split(' '))
        return True


def print_help(argv):
    logger.info('Syntax: {} <package-name> <template-package-dir> [artifact files ...]'.format(argv[0]))
    logger.info('  Example: $ {} kafka /path/to/universe/jsons/ /path/to/artifact1.zip /path/to/artifact2.zip /path/to/artifact3.zip'.format(argv[0]))
    logger.info('In addition, environment variables named \'TEMPLATE_SOME_PARAMETER\' will be inserted against the provided package template (with params of the form \'{{some-parameter}}\')')


def main(argv):
    if len(argv) < 3:
        print_help(argv)
        return 1
    # the package name:
    package_name = argv[1]
    # local path where the package template is located:
    package_dir_path = argv[2].rstrip('/')
    # artifact paths (to copy along with stub universe)
    artifact_paths = argv[3:]
    logger.info('''###
Package:         {}
Template path:   {}
Artifacts:       {}
###'''.format(package_name, package_dir_path, ', '.join(artifact_paths)))

    publisher = HTTPPublisher(package_name, package_dir_path, artifact_paths)
    http_url_root = publisher.launch_http()
    universe_url = publisher.build(http_url_root)
    repo_added = publisher.add_repo_to_cli(universe_url)
    logger.info('---')
    logger.info('(Re)install your package using the following commands:')
    logger.info('dcos package uninstall {}'.format(package_name))
    logger.info('dcos node ssh --master-proxy --leader ' +
                '"docker run mesosphere/janitor /janitor.py -r {0}-role -p {0}-principal -z dcos-service-{0}"'.format(package_name))
    if not repo_added:
        logger.info('dcos package repo remove {}-local'.format(package_name))
        logger.info('dcos package repo add --index=0 {}-local {}'.format(package_name, universe_url))
    logger.info('dcos package install --yes {}'.format(package_name))
    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv))
