#!/usr/bin/env python3

import json
import logging
import os
import os.path
import shutil
import ssl
import sys
import tempfile
import time
import urllib.request


logger = logging.getLogger(__name__)
logging.basicConfig(level=logging.DEBUG, format="%(message)s")


def get_cli_filename():
    if sys.platform == 'win32':
        return 'dcos.exe'
    elif sys.platform in ('linux2', 'linux', 'darwin'):
        return 'dcos'
    else:
        raise Exception('Unsupported platform: {}'.format(sys.platform))


def get_download_platform():
    if sys.platform == 'win32':
        return 'windows'
    elif sys.platform == 'linux2' or sys.platform == 'linux':
        return 'linux'
    elif sys.platform == 'darwin':
        return 'darwin'
    else:
        raise Exception('Unsupported platform: {}'.format(sys.platform))


def get_cluster_version(dcos_url):
    """Given a cluster url, return its version string, such as 1.7, 1.8,
    1.8.8, 1.9, 1.9-dev, etc"""
    version_url = "%s/%s" % (dcos_url, "dcos-metadata/dcos-version.json")
    # Since our test clusters have weird certs that won't validate, turn off
    # validation
    try:
        # this will handle a variety of versions, including some 2.7.x
        # (haven't investigated which) and all recent 3.x
        noverify_context = ssl.SSLContext()
    except:
        if hasattr(ssl, 'PROTOCOL_TLS'):
            # 2.7.13
            noverify_context = ssl.SSLContext(ssl.PROTOCOL_TLS)
        elif hasattr(ssl, 'OP_NO_SSLv2'):
            # 2.7.9-2.7.12
            logger.warn("Old python; Asking for something better than sslv2 or 3")
            noverify_context =  ssl.SSLContext(ssl.PROTOCOL_SSLv23)
            # explicitly switch these off.  This is recommended for ancient
            # versions as per https://docs.python.org/2/library/ssl.html#protocol-versions
            noverify_context.options |=  ssl.OP_NO_SSLv2
            noverify_context.options |=  ssl.OP_NO_SSLv3
        else:
            # before 2.7.9
            logger.error("*VERY* old python.  Very weak/unsafe encryption ahoy.")
            noverify_context =  ssl.SSLContext(ssl.PROTOCOL_SSLv23)

    response = urllib.request.urlopen(version_url, context=noverify_context)
    encoding = 'utf-8' # default
    try:
        #python3
        provided_encoding = response.headers.get_content_charset()
        if provided_encoding:
            encoding =  provided_encoding
    except:
        pass
    json_s = response.read().decode(encoding)
    ver_s = json.loads(json_s)['version']
    return ver_s

def _get_tempfilename(a_dir):
    temp_target_f = tempfile.NamedTemporaryFile(dir=a_dir, delete=False)
    temp_target_f.close()
    return temp_target_f.name


def _mark_executable(path):
    os.chmod(path, 0o755)

def install_cli(src_file, write_dir):
    """Copy an existing cli to a target directory path, updating the target
    atomically."""
    output_filepath = os.path.join(write_dir, get_cli_filename())
    logger.info('Copying {} to {}'.format(src_file, output_filepath))
    try:
        # actually copy to unique filename, then rename into place atomically.
        temp_target = _get_tempfilename(write_dir)
        shutil.copyfile(src_file, temp_target)
        _mark_executable(temp_target)
        # This could fail, which means probably it's already running and maybe
        # it's even the right version, but not hiding the exception.
        os.rename(temp_target, output_filepath)
    finally:
        if os.path.exists(temp_target):
            os.unlink(temp_target)
    return output_filepath

def download_cli(dcos_url, write_dir):
    """Download the correct cli version for a given cluster url, placing it in
    a target directory and causing the target executable to update atomically"""
    url_template = 'https://downloads.dcos.io/binaries/cli/{}/x86-64/dcos-{}/{}'
    cluster_version = get_cluster_version(dcos_url)
    # we only care about the target release number
    if '-' in cluster_version:
        cluster_version, _ = cluster_version.split('-', 1) # "1.9-dev" -> 1.9
    major, minor = cluster_version.split('.')[:2] # 1.8.8 -> 1.8
    cluster_version = '%s.%s' % (major, minor)
    cli_url = url_template.format(get_download_platform(), cluster_version,
                                  get_cli_filename())
    # actually download to unique filename, then rename into place atomically.
    try:
        temp_target = _get_tempfilename(write_dir)
        output_filepath = os.path.join(write_dir, get_cli_filename())
        for attempt in (1,2,3):
            # this seems to flake out more than you'd expect, DNS mostly
            logger.info('Downloading {} to {} attempt {}/3'.format(cli_url,
                                                                   output_filepath,
                                                                   attempt))
            try:
                urllib.request.URLopener().retrieve(cli_url, temp_target)
                break
            except Exception as e:
                logger.info("Attempt {} failed: {}".format(attempt, e))
                if attempt == 3:
                    raise
                time.sleep(1)
        _mark_executable(temp_target)
        os.rename(temp_target, output_filepath)
    finally:
        if os.path.exists(temp_target):
            os.unlink(temp_target)
    logger.info("Download complete")
    return output_filepath


if __name__ == "__main__":
    def usage(f=sys.stderr):
        f.write("usage: cli_install.py <path_to_existing_cli> <target_dir>\n")
        f.write("  OR\n")
        f.write("usage: cli_install.py <cluster_url> <target_dir>\n")


    if not len(sys.argv) == 3:
        usage()
        sys.exit(1)

    source = sys.argv[1]
    target_dir = sys.argv[2]

    # convenience for command line
    if not os.path.exists(target_dir):
        os.makedirs(target_dir)

    if source.startswith('http://') or source.startswith('https://'):
        download_cli(source, target_dir)
    elif os.path.isfile(source):
        install_cli(source, target_dir)
    else:
        sys.stderr.write("No such file to copy: {}".format(source))
        usage()
        sys.exit(1)
