from prometheus_client.parser import text_string_to_metric_families

import requests
import statsd
import os
import time
import sys
import urllib3

class CockroachEmitter:
    ''' Scrape prometheus formatted metrics from CockroachDB,
        and emit them continuously on statsd.'''
    CONNECTION_RETRY_INTERVAL = 60
    POLL_INTERVAL = 20

    def __init__(self):
        statsd_host = os.getenv("STATSD_UDP_HOST")
        statsd_port = os.getenv("STATSD_UDP_PORT")
        self.conn = statsd.StatsClient(statsd_host, statsd_port)
        self.run = False

    def start(self):
        PORT_GUI = os.getenv("PORT_GUI")
        url = "http://localhost:{}/_status/vars".format(PORT_GUI)

        self.run = True
        while self.run:
            try:
                prometheus_text = self.get_prometheus_text(url)
                self.parse_prometheus_text(prometheus_text)
                time.sleep(self.POLL_INTERVAL)
            except (ConnectionRefusedError, \
                    urllib3.exceptions.NewConnectionError, \
                    urllib3.exceptions.MaxRetryError, \
                    requests.exceptions.ConnectionError) as e:
                print("[CONNECTION ERROR] {}".format(e), file=sys.stderr)
                print("[CONNECTION ERROR] Waiting {} seconds to retry...".format(self.CONNECTION_RETRY_INTERVAL), file=sys.stderr)
                time.sleep(self.CONNECTION_RETRY_INTERVAL)

    def stop(self):
        self.run=False

    def get_prometheus_text(self, url):
        ''' Scrape prometheus formatted metrics from CockroachDB'''
        r = requests.get(url)
        prometheus_text = r.text
        return prometheus_text

    def parse_prometheus_text(self, prometheus_text):
        '''Parse metrics and emit them to statsd'''
        succeeded = 0
        failed = 0
        for family in text_string_to_metric_families(prometheus_text):
            for sample in family.samples:
                name, labels, value = sample
                try:
                    self.conn.gauge(name, value, delta=True)
                    succeeded += 1
                except:
                    failed += 1
        print("[metrics] successfully sent {}/{} items to statsd".format(succeeded, succeeded + failed), file=sys.stderr)

if __name__ == "__main__":
    emitter = CockroachEmitter()
    emitter.start()
