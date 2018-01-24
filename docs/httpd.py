#!/usr/bin/python

# Launches a local HTTP server for a specified directory path, then blocks until killed

DEFAULT_HOST = 'localhost'

import os, os.path, socket, sys
try:
    import SimpleHTTPServer, SocketServer
except KeyboardInterrupt:
    # for whatever reason, it gets stuck here sometimes...
    print('Exiting1...')
    sys.exit(0)


def serve_http(host, port, rootdir):
    if port == 0:
        # hack: grab/release a suitable ephemeral port and hope nobody steals it in the meantime
        sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        sock.bind((host, 0))
        port = sock.getsockname()[1]
        sock.close()

    os.chdir(rootdir)
    httpd = SocketServer.TCPServer((host, port), SimpleHTTPServer.SimpleHTTPRequestHandler)
    print('Visit >>> http://{}:{}/dcos-commons/ <<< to view {}'.format(host, port, rootdir))
    print('Ctrl+C to exit.')
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        httpd.shutdown()
        httpd.server_close()


def syntax(argv):
    print('''Syntax: {} <path> <port>
- A port of '0' will result in selecting a random ephemeral port.
- An 'HTTP_HOST' envvar may be used to override the listen host (default "{}")'''.format(argv[0], DEFAULT_HOST))


def main(argv):
    if len(argv) < 3:
        syntax(argv)
        return 1
    host = os.environ.get('HTTP_HOST', DEFAULT_HOST)
    path = argv[1]
    port = int(argv[2])
    if not os.path.isdir(path):
        syntax(argv)
        print('Path must be a directory')
        return 1
    serve_http(host, port, os.path.abspath(path))
    return 0


if __name__ == '__main__':
    try:
        exitcode = main(sys.argv)
    except KeyboardInterrupt:
        exitcode = 0
    print('Exiting2...')
