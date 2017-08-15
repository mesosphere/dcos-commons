#!/usr/bin/env python3

import json
import requests
import os
import sys
import time

import logging

logging.basicConfig(level=logging.INFO)
log = logging.getLogger('ccm_extend')
log.setLevel(logging.INFO)


base_url = 'https://ccm.mesosphere.com'

# Load the cluster_info.json to get the cluster name
with open('cluster_info.json', 'r') as cluster_file:
    cluster_info = json.loads(cluster_file.read())

cluster_name = cluster_info['deployment_name']
ccm_token = os.environ['CCM_TOKEN']

# Get all the clusters
headers = {'Authorization': 'Token {}'.format(ccm_token),
           'Content-Type': 'application/json'}

# Determine the cluster id of the cluster
# It may take some time for CCM to have all of the clusters information. Poll
# until it receives it. Try for 60 minutes, because we really don't want to waste a cluster we just created.
give_up_time = time.time() + 60 * 60
cluster_id = None

while give_up_time > time.time():
    log.info("Looking up cluster id.")
    # Get all active clusters
    active_cluster_url = '{}/{}'.format(base_url, 'api/cluster/active/all')
    r = requests.get(active_cluster_url, headers=headers)

    try:
        r.raise_for_status()
    except requests.exceptions.HTTPError as e:
        log.error('HTTP request returned with status code %s', r.status_code)
        log.error('%s', e)
        continue

    try:
        all_clusters = r.json()
    except ValueError as e:
        log.error('Could not decode JSON from response: %s', r.text)
        log.error('%s', e)
        continue

    log.info('Found %s clusters. Looking for %s', len(all_clusters), cluster_name)
    for cluster in all_clusters:
        if cluster['name'] == cluster_name:
            cluster_id = cluster['id']
            break

    if cluster_id is not None:
        log.info("Found cluster %s with id=%s", cluster_name, cluster_id)
        break

    log.info("Cluster id not found. Sleeping for 60 seconds...")
    time.sleep(60)

if cluster_id is None:
    log.info("Never retrieved cluster id from CCM for cluster %s!", cluster_name)
    sys.exit(1)

# This API call increases the cluster length by an additional 4 hours.
# See: https://ccm.mesosphere.com/api-docs/#!/cluster/Cluster_Detail_PUT
try:
    this_cluster_url = '{}/api/cluster/{}/'.format(base_url, cluster_id)
    r = requests.put(this_cluster_url, json={"time": "240"}, headers=headers)
    r.raise_for_status()
except requests.exceptions.HTTPError as e:
    log.error(e)
    log.error(r)
    raise e

log.info(r.text)
