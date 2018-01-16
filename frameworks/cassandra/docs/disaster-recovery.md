---
layout: layout.pug
title: Disaster Recovery
navigationTitle: Disaster Recovery
menuWeight: 80
excerpt:

---

# Backup

## Backing Up to S3

You can backup an entire cluster's data and schema to Amazon S3 using the `backup-s3` plan. This plan requires the following parameters to run:
- `SNAPSHOT_NAME`: the name of this snapshot. Snapshots for individual nodes will be stored as S3 folders inside of a top level `snapshot` folder.
- `CASSANDRA_KEYSPACES`: the Cassandra keyspaces to backup. The entire keyspace, as well as its schema, will be backed up for each keyspace specified.
- `AWS_ACCESS_KEY_ID`: the access key ID for the AWS IAM user running this backup.
- `AWS_SECRET_ACCESS_KEY`: the secret access key for the AWS IAM user running this backup.
- `AWS_REGION`: the region of the S3 bucket being used to store this backup.
- `S3_BUCKET_NAME`: the name of the S3 bucket to store this backup in.

Make sure that you provision your nodes with enough disk space to perform a backup. Apache Cassandra backups are stored on disk before being uploaded to S3, and will take up as much space as the data currently in the tables, so you'll need half of your total available space to be free to backup every keyspace at once.

As noted in the documentation for the [backup/restore strategy configuration option](#backup-restore-strategy), it is possible to run transfers to S3 either in serial or in parallel, but care must be taken not to exceed any throughput limits you may have in your cluster. Throughput depends on a variety of factors, including uplink speed, proximity to region where the backups are being uploaded and downloaded, and the performance of the underlying storage infrastructure. You should perform periodic tests in your local environment to understand what you can expect from S3.

You can configure whether snapshots are created and uploaded in serial, the default, or in parallel (see [backup/restore strategy](#backup-restore-strategy)), but the serial backup/restore strategy is recommended.

You can initiate this plan from the command line:
```
SNAPSHOT_NAME=<my_snapshot>
CASSANDRA_KEYSPACES="space1 space2"
AWS_ACCESS_KEY_ID=<my_access_key_id>
AWS_SECRET_ACCESS_KEY=<my_secret_access_key>
AWS_REGION=us-west-2
S3_BUCKET_NAME=backups
dcos cassandra plan start backup-s3 -p SNAPSHOT_NAME=$SNAPSHOT_NAME -p "CASSANDRA_KEYSPACES=$CASSANDRA_KEYSPACES" -p AWS_ACCESS_KEY_ID=$AWS_ACCESS_KEY_ID -p AWS_SECRET_ACCESS_KEY=$AWS_SECRET_ACCESS_KEY -p AWS_REGION=$AWS_REGION -p S3_BUCKET_NAME=$S3_BUCKET_NAME
```

If you're backing up multiple keyspaces, they must be separated by spaces and wrapped in quotation marks when supplied to the `plan start` command, as in the example above. If the `CASSANDRA_KEYSPACES` parameter isn't supplied, then every keyspace in your cluster will be backed up.

**IMPORTANT**: To ensure that sensitive information, such as your AWS secret access key, remains secure, make sure that you've set the `core.dcos_url` configuration property in the DC/OS CLI to an HTTPS URL.

## Backing up to Azure

You can also back up to Microsoft Azure using the `backup-azure` plan. This plan requires the following parameters to run:

- `SNAPSHOT_NAME`: the name of this snapshot. Snapshots for individual nodes will be stored as gzipped tarballs with the name `node-<POD_INDEX>.tar.gz`.
- `CASSANDRA_KEYSPACES`: the Cassandra keyspaces to backup. The entire keyspace, as well as its schema, will be backed up for each keyspace specified.
- `CLIENT_ID`: the client ID for the Azure service principal running this backup.
- `TENANT_ID`: the tenant ID for the tenant that the service principal belongs to.
- `CLIENT_SECRET`: the service principal's secret key.
- `AZURE_STORAGE_ACCOUNT`: the name of the storage account that this backup will be sent to.
- `AZURE_STORAGE_KEY`: the secret key associated with the storage account.
- `CONTAINER_NAME`: the name of the container to store this backup in.

You can initiate this plan from the command line in the same way as the Amazon S3 backup plan:
```
dcos cassandra plan start backup-azure -p SNAPSHOT_NAME=$SNAPSHOT_NAME -p "CASSANDRA_KEYSPACES=$CASSANDRA_KEYSPACES" -p CLIENT_ID=$CLIENT_ID -p TENANT_ID=$TENANT_ID -p CLIENT_SECRET=$CLIENT_SECRET -p AZURE_STORAGE_ACCOUNT=$AZURE_STORAGE_ACCOUNT -p AZURE_STORAGE_KEY=$AZURE_STORAGE_KEY -p CONTAINER_NAME=$CONTAINER_NAME
```

# Restore

All restore plans will restore the schema from every keyspace backed up with the backup plan and populate those keyspaces with the data they contained at the time the snapshot was taken. Downloading and restoration of backups will use the configured backup/restore strategy. This plan assumes that the keyspaces being restored do not already exist in the current cluster, and will fail if any keyspace with the same name is present.

## Restoring From S3

Restoring cluster data is similar to backing it up. The `restore-s3` plan assumes that your data is stored in an S3 bucket in the format that `backup-s3` uses. The restore plan has the following parameters:
- `SNAPSHOT_NAME`: the snapshot name from the `backup-s3` plan.
- `AWS_ACCESS_KEY_ID`: the access key ID for the AWS IAM user running this restore.
- `AWS_SECRET_ACCESS_KEY`: the secret access key for the AWS IAM user running this restore.
- `AWS_REGION`: the region of the S3 bucket being used to store the backup being restored.
- `S3_BUCKET_NAME`: the name of the S3 bucket where the backup is stored.

To initiate this plan from the command line:
```
SNAPSHOT_NAME=<my_snapshot>
AWS_ACCESS_KEY_ID=<my_access_key_id>
AWS_SECRET_ACCESS_KEY=<my_secret_access_key>
AWS_REGION=us-west-2
S3_BUCKET_NAME=backups
dcos cassandra plan start backup-s3 "SNAPSHOT_NAME=$SNAPSHOT_NAME,AWS_ACCESS_KEY_ID=$AWS_ACCESS_KEY_ID,AWS_SECRET_ACCESS_KEY=$AWS_SECRET_ACCESS_KEY,AWS_REGION=$AWS_REGION,S3_BUCKET_NAME=$S3_BUCKET_NAME"
```

## Restoring From Azure

You can restore from Microsoft Azure using the `restore-azure` plan. This plan requires the following parameters to run:

- `SNAPSHOT_NAME`: the name of this snapshot. Snapshots for individual nodes will be stored as gzipped tarballs with the name `node-<POD_INDEX>.tar.gz`.
- `CLIENT_ID`: the client ID for the Azure service principal running this backup.
- `TENANT_ID`: the tenant ID for the tenant that the service principal belongs to.
- `CLIENT_SECRET`: the service principal's secret key.
- `AZURE_STORAGE_ACCOUNT`: the name of the storage account that this backup will be sent to.
- `AZURE_STORAGE_KEY`: the secret key associated with the storage account.
- `CONTAINER_NAME`: the name of the container to store this backup in.

You can initiate this plan from the command line in the same way as the Amazon S3 restore plan:
```
dcos cassandra plan start restore-azure -p SNAPSHOT_NAME=$SNAPSHOT_NAME -p CLIENT_ID=$CLIENT_ID -p TENANT_ID=$TENANT_ID -p CLIENT_SECRET=$CLIENT_SECRET -p AZURE_STORAGE_ACCOUNT=$AZURE_STORAGE_ACCOUNT -p AZURE_STORAGE_KEY=$AZURE_STORAGE_KEY -p CONTAINER_NAME=$CONTAINER_NAME
```
