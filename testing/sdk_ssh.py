'''Utilities relating to underlying creation of SSH sessions.
Service tests should NOT directly invoke this code. Use sdk_cmd instead.

TODO(nickbp): Replace this with something that isn't paramiko.

************************************************************************
FOR THE TIME BEING WHATEVER MODIFICATIONS ARE APPLIED TO THIS FILE
SHOULD ALSO BE APPLIED TO sdk_ssh IN ANY OTHER PARTNER REPOS
************************************************************************
'''

import itertools
import logging
import os.path
import time
from _thread import RLock
from functools import wraps
from select import select

import paramiko

import sdk_utils

log = logging.getLogger(__name__)


def _connection_cache(func: callable):
    """Connection cache for SSH sessions. This is to prevent opening a
     new, expensive connection on every command run."""
    cache = dict()
    lock = RLock()

    @wraps(func)
    def func_wrapper(host: str, username: str, *args, **kwargs):
        key = "{h}-{u}".format(h=host, u=username)
        if key in cache:
            # connection exists, check if it is still valid before
            # returning it.
            conn = cache[key]
            if conn and conn.is_active() and conn.is_authenticated():
                return conn
            else:
                # try to close a bad connection and remove it from
                # the cache.
                if conn:
                    try:
                        conn.close()
                    except Exception:
                        pass
                del cache[key]

        # key is not in the cache, so try to recreate it
        # it may have been removed just above.
        if key not in cache:
            conn = func(host, username, *args, **kwargs)
            if conn is not None:
                cache[key] = conn
            return conn

        # not sure how to reach this point, but just in case.
        return None

    def get_cache() -> dict:
        return cache

    def purge(key: str = None):
        with lock:
            if key is None:
                conns = [(k, v) for k, v in cache.items()]
            elif key in cache:
                conns = ((key, cache[key]), )
            else:
                conns = list()

            for k, v in conns:
                try:
                    v.close()
                except Exception:
                    pass
                del cache[k]

    func_wrapper.get_cache = get_cache
    func_wrapper.purge = purge
    return func_wrapper


@_connection_cache
def _get_connection(host) -> paramiko.Transport or None:
    """Return an authenticated SSH connection.

    :param host: host or IP of the machine
    :type host: str
    :return: SSH connection
    :rtype: paramiko.Transport or None
    """
    username = 'core'
    key_path = '~/.ssh/id_rsa'
    key = _validate_key(key_path)
    transport = _get_transport(host, username, key)

    if transport:
        transport = _start_transport(transport, username, key)
        if transport.is_authenticated():
            return transport
        else:
            log.error("error: unable to authenticate {}@{} with key {}".format(username, host, key_path))
    else:
        log.error("error: unable to connect to {}".format(host))

    return None


def _run_command(host, command):
    conn = _get_connection(host)
    if not conn:
        return False, ""

    session = conn.open_session()
    session.exec_command(command)

    exit_code = session.recv_exit_status()

    # Wait for results.
    # Because `recv_ready()` can return False, but still have a
    # valid, open connection, it is not enough to ensure output
    # from a command execution is properly captured.
    while True:
        time.sleep(0.2)
        if session.recv_ready() or session.closed:
            break

    # read data that is ready
    output = ''
    while session.recv_ready():
        # lists of file descriptors that are ready for IO
        # read, write, "exceptional condition" (?)
        rl, wl, xl = select([session], [], [], 0.0)
        if len(rl) > 0:
            recv = str(session.recv(1024), "utf-8")
            print(recv, end='', flush=True)
            output += recv
    try:
        session.close()
    except Exception:
        pass

    return exit_code == 0, output


def _get_transport(host, username, key):
    """ Create a transport object

        :param host: the hostname to connect to
        :type host: str
        :param username: SSH username
        :type username: str
        :param key: key object used for authentication
        :type key: paramiko.RSAKey

        :return: a transport object
        :rtype: paramiko.Transport
    """

    if host == sdk_utils.dcos_ip():
        transport = paramiko.Transport(host)
    else:
        transport_master = paramiko.Transport(sdk_utils.dcos_ip())
        transport_master = _start_transport(transport_master, username, key)

        if not transport_master.is_authenticated():
            print("error: unable to authenticate {}@{} with key {}".format(username, sdk_utils.dcos_ip(), key))
            return False

        try:
            channel = transport_master.open_channel('direct-tcpip', (host, 22), ('127.0.0.1', 0))
        except paramiko.SSHException:
            print("error: unable to connect to {}".format(host))
            return False

        transport = paramiko.Transport(channel)

    return transport


def _start_transport(transport, username, key):
    """ Begin a transport client and authenticate it

        :param transport: the transport object to start
        :type transport: paramiko.Transport
        :param username: SSH username
        :type username: str
        :param key: key object used for authentication
        :type key: paramiko.RSAKey

        :return: the transport object passed
        :rtype: paramiko.Transport
    """

    transport.start_client()

    agent = paramiko.agent.Agent()
    keys = itertools.chain((key,) if key else (), agent.get_keys())
    for test_key in keys:
        try:
            transport.auth_publickey(username, test_key)
            break
        except paramiko.AuthenticationException:
            pass
    else:
        raise ValueError('No valid key supplied')

    return transport


def _validate_key(key_path):
    """ Validate a key

        :param key_path: path to a key to use for authentication
        :type key_path: str

        :return: key object used for authentication
        :rtype: paramiko.RSAKey
    """

    key_path = os.path.expanduser(key_path)

    if not os.path.isfile(key_path):
        return False

    return paramiko.RSAKey.from_private_key_file(key_path)
