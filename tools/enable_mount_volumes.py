#!/usr/bin/env python3
"""
enable_mount_voluems - Creator and Configurator of MOUNT volumes

Features:
1. Create GP2 SSD volumes for each private agent
2. Attaches volumes to each private agent
3. Formats volumes and configures fstab entires
4. Configures Mesos Agent and relaunches instances for changes to take effect.

Note: Currently, enable_mount_volumes only works with AWS DC/OS clusters.
"""

import boto3
import botocore
import logging
import os
import os.path
#import pprint
import sys
import time
import uuid

from fabric.api import run, env
from fabric.tasks import execute

logger = logging.getLogger(__name__)
logging.basicConfig(level=logging.INFO, format="%(message)s")


def tag_match(instance, key, value):
    tags = instance.get('Tags')
    if not tags:
        return False
    for tag in tags:
        if tag.get('Key') == key and tag.get('Value') == value:
            return True
    return False


def filter_reservations_tags(reservations, filter_key, filter_value):
    filtered_reservations = []
    logger.info('Values for {} (searching for "{}"):'.format(filter_key, filter_value))
    for reservation in reservations:
        instances = reservation['Instances']
        if tag_match(reservation['Instances'][0], filter_key, filter_value):
            filtered_reservations.append(reservation)
    return filtered_reservations


def filter_gateway_instance(instances):
    for instance in instances:
        if tag_match(instance, 'role', 'mesos-master'):
            return instance


def enumerate_instances(reservations):
    bucket = []
    for reservation in reservations:
        instances = reservation['Instances']
        for instance in instances:
            bucket.append(instance)

    return bucket


# A private slave doesn't have a PublicDnsName
def filter_instances_private(instances):
    return [instance for instance in instances if len(instance.get('PublicDnsName', '')) == 0]


def create_volume(client, zone):
    response = client.create_volume(
        Size=24,
        AvailabilityZone=zone,
        VolumeType='gp2',
        Encrypted=False
    )

    logger.info('Create volume response: {}'.format(response))

    return response


def attach_volume(client, volume_id, instance_id, device='/dev/xvdm'):
    response = client.attach_volume(
        VolumeId=volume_id,
        InstanceId=instance_id,
        Device=device)

    logger.info('Attach volume response: {}'.format(response))

    return response


def configure_delete_on_termination(client, volume_id, instance_id, device='/dev/xvdm'):
    response = client.modify_instance_attribute(
        InstanceId=instance_id,
        BlockDeviceMappings=[
            {
                'DeviceName': device,
                'Ebs': {
                    'VolumeId': volume_id,
                    'DeleteOnTermination': True
                }
            },
        ]
    )

    logger.info('Instance attribute modification response: {}'.format(response))

    return response


def tag_volume(client, volume_id):
    response = client.create_tags(
        Resources=[volume_id],
        Tags=[
            {
                'Key': 'ccm_volume_name',
                'Value': 'infinity-' + str(uuid.uuid1())
            }
        ]
    )

    return response


def detach_volume(client, volume_id, instance_id, device='/dev/xvdm'):
    response = client.detach_volume(
        VolumeId=volume_id,
        InstanceId=instance_id,
        Device=device)

    logger.info('Volume detach response: {}'.format(response))

    return response


def configure_partition(device, partition_index, start, end, stdout):
    device_partition = '{}{}'.format(device, partition_index) # e.g. /dev/xvdm1
    mount_location = '/dcos/volume{}'.format(partition_index - 1) # e.g. /dcos/volume0
    run('sudo parted -s {} mkpart primary ext4 {} {}'.format(device, start, end), 
            stdout=stdout)
    run('sudo mkfs -t ext4 {}{}'.format(device_partition), stdout=stdout)
    run('sudo mkdir -p {}'.format(mount_location), stdout=stdout)
    run('sudo mount {} {}'.format(device_partition, mount_location),
            stdout=stdout)
    run('sudo sh -c "echo \'{} {} ext4 defaults 0 2\' >> /etc/fstab"'.format(device_partition, mount_location),
            stdout=stdout)


def configure_device(device='/dev/xvdm', stdout=sys.stdout):
    """
    Format the attached EBS volume as two MOUNT volumes and adds entries into fstab.
    DC/OS will autodetect the '/dcos/volume#' volumes.
    """
    device_name = os.path.basename(device)
    run('until [[ "$(lsblk -o NAME -r | grep {} | wc -l)" -gt "0" ]]; do echo "Waiting for {}"; sleep 2; done'.format(device_name, device_name))
    run('sudo parted -s {} mklabel gpt'.format(device))

    configure_partition(device, 1, "0%", "50%", stdout=stdout)
    configure_partition(device, 2, "50%", "100%", stdout=stdout)


def configure_mesos(stdout):
    """
    Configures the newly created EBS volume as a Mesos agent resource
    """
    run("sudo systemctl stop dcos-mesos-slave", stdout=stdout)
    run("sudo rm -f /var/lib/mesos/slave/meta/slaves/latest", stdout=stdout)
    run("sudo rm -f /var/lib/dcos/mesos-resources", stdout=stdout)
    run("sudo systemctl start dcos-mesos-slave", stdout=stdout)


def main(stack_id = '', stdout=sys.stdout):
    # Read inputs from environment
    aws_access_key = os.environ.get('AWS_ACCESS_KEY_ID', '')
    aws_secret_key = os.environ.get('AWS_SECRET_ACCESS_KEY', '')
    stack_id = str(os.environ.get('STACK_ID', stack_id))
    if not aws_access_key or not aws_secret_key or not stack_id:
        logger.error('AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY, and STACK_ID envvars are required.')
        return 1
    region_name = os.environ.get('AWS_DEFAULT_REGION', 'us-west-2')

    # Create EC2 client
    ec2 = boto3.client('ec2',
                       aws_access_key_id=aws_access_key,
                       aws_secret_access_key=aws_secret_key,
                       region_name=region_name)

    # Get all provisioned instances
    instances = ec2.describe_instances()
    #logger.info('Instances: {}'.format(pprint.pformat(instances)))
    all_reservations = instances.get('Reservations')
    #logger.info('Reservations: {}'.format(pprint.pformat(all_reservations)))

    # Filter instances for the given stack-id
    stack_id_key = 'aws:cloudformation:stack-id'
    reservations = filter_reservations_tags(all_reservations, stack_id_key, stack_id)
    if not reservations:
        logger.error('Unable to find any reservations with {} = {}.'.format(stack_id_key, stack_id))
        return 1
    logger.info('Found {} reservations with {} = {}'.format(len(reservations), stack_id_key, stack_id))

    # Extract all the instance objects
    instances = enumerate_instances(reservations)
    #logger.info('Reservation instances:\n{}'.format(pprint.pformat(instances)))

    # Extract the public host from our list of instances
    gateway_instance = filter_gateway_instance(instances)
    #logger.info('Gateway instance:\n{}'.format(pprint.pformat(gateway_instance)))

    # This gateway ip will be used as a jump host for SSH into private nodes
    gateway_ip = gateway_instance.get('PublicIpAddress')
    logger.info('Gateway IP: {}'.format(gateway_ip))

    # Attach EBS volumes to private instances only
    private_instances = filter_instances_private(instances)

    for instance in private_instances:
        # If an instance is not running, ignore it.
        if instance.get('State').get('Name') != 'running':
            logger.info('Ignoring instance that is not running: {}'.format(instance))
            continue

        instance_id = instance['InstanceId']
        azone = instance['Placement']['AvailabilityZone']

        # Create volume for the instance in the same AvailabilityZone
        volume = create_volume(ec2, azone)
        logger.info('Creating volume: {}'.format(volume))

        volume_id = volume['VolumeId']

        # Wait for volume to be available.
        volume_waiter = ec2.get_waiter('volume_available')

        attempts = 0
        max_attempts = 16
        wait_time = 1
        while attempts < max_attempts:
            attempts += 1
            try:
                volume_waiter.wait(VolumeIds=[volume_id])
                logger.info('Volume: {} is now available'.format(volume_id))
                break
            except botocore.exceptions.WaiterError as e:
                logger.error('Error occured: {}'.format(e))
                raise e
            except botocore.exceptions.ClientError as e:
                logger.error('Error occured: {}'.format(e))
                if e.response['Error']['Code'] == 'RequestLimitExceeded':
                    curr_wait_time = 2**attempts * wait_time
                    logger.error('Going to wait for: {} before retrying.'.format(curr_wait_time))
                    time.sleep(curr_wait_time)
                else:
                    raise e


        # Attach the volume to our instance.
        att_res = attach_volume(ec2, volume_id=volume_id, instance_id=instance_id)
        logger.info('Attaching volume: {}'.format(att_res))

        # Wait for volume to attach.
        volume_attach = ec2.get_waiter('volume_in_use')

        attempts = 0
        max_attempts = 16
        wait_time = 1
        while attempts < max_attempts:
            attempts += 1
            try:
                volume_attach.wait(VolumeIds=[volume_id])
                logger.info('Volume: {} is now attached to instance: {}'.format(volume_id, instance_id))
                break
            except botocore.exceptions.WaiterError as e:
                logger.error('Error occured: {}'.format(e))
                raise e
            except botocore.exceptions.ClientError as e:
                logger.error('Error occured: {}'.format(e))
                if e.response['Error']['Code'] == 'RequestLimitExceeded':
                    curr_wait_time = 2**attempts * wait_time
                    logger.error('Going to wait for: {} before retrying.'.format(curr_wait_time))
                    time.sleep(curr_wait_time)
                else:
                    raise e


        conf_res = configure_delete_on_termination(ec2, volume_id=volume_id, instance_id=instance_id)
        logger.info('Delete on termination: {}'.format(conf_res))

        tag_res = tag_volume(ec2, volume_id=volume_id)
        logger.info('Tag volume: {}'.format(tag_res))

        private_ip = instance.get('PrivateIpAddress')
        env.hosts = [private_ip]
        env.gateway = gateway_ip
        env.user = 'core'

        logger.info('Creating partitions on agent: {}'.format(private_ip))
        execute(configure_device, '/dev/xvdm', stdout)

        logger.info('Restarting agent so that it sees the partitions: {}'.format(private_ip))
        execute(configure_mesos, stdout)

    logger.info('Mount volumes enabled. Exiting now...')
    return 0


if __name__ == '__main__':
    sys.exit(main())
