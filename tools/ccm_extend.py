#!/usr/bin/env python3

import json
import requests
import os

base_url = 'https://ccm.mesosphere.com'

# Load the cluster_info.json to get the cluster name
with open('cluster_info.json', 'r') as cluster_file:
    cluster_info = json.loads(cluster_file.read())

cluster_name = cluster_info['deployment_name']
ccm_token = os.environ['CCM_TOKEN']

# Get all the clusters
headers = {'Authorization': 'Token {}'.format(CCM_TOKEN)}

all_clusters = json.loads(requests.get(base_url+'/api/cluster/active/all', headers=headers).text)
for cluster in all_clusters:
    if cluster['name'] == cluster_name:
        cluster_id = cluster['id']

response = requests.put(base_url+'/api/cluster/{}/'.format(cluster_id), data = {'time':'240'}, headers=headers)
print(response.text)
