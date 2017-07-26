#!/usr/bin/env python3

import json
import requests
import os
import time

base_url = 'https://ccm.mesosphere.com'

# Load the cluster_info.json to get the cluster name
with open('cluster_info.json', 'r') as cluster_file:
    cluster_info = json.loads(cluster_file.read())

cluster_name = cluster_info['deployment_name']
ccm_token = os.environ['CCM_TOKEN']

# Get all the clusters
headers = {'Authorization': 'Token {}'.format(ccm_token)}

# Determine the cluster id of the cluster
# It may take some time for CCM to have all of the clusters information. Poll
# until it receives it. Try for 60 minutes, because we really don't want to waste a cluster we just created.
give_up_time = time.time() + 60 * 60
cluster_id = None
while give_up_time > time.time():
    print("Looking up cluster id.")
    # Get all active clusters
    all_clusters = json.loads(requests.get(base_url+'/api/cluster/active/all', headers=headers).text)
    for cluster in all_clusters:
        if cluster['name'] == cluster_name:
            cluster_id = cluster['id']
            break

    if cluster_id is not None:
        print("Found cluster id!")
        break

    print("Cluster id not found. Sleeping for 60 seconds...")
    time.sleep(60)

if cluster_id is None:
    print("Never retrieved cluster id from CCM!")
    os.exit(1)

# This API call increases the cluster length by an additional 4 hours.
# See: https://ccm.mesosphere.com/api-docs/#!/cluster/Cluster_Detail_PUT
response = requests.put(base_url+'/api/cluster/{}/'.format(cluster_id), data={'time': '240'}, headers=headers)
print(response.text)
