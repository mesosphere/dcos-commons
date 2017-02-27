#!/usr/bin/env ruby
require 'rubygems'
require 'bundler'
require 'singleton'
require 'em/pure_ruby'

Bundler.require

require_relative 'sidecar/azure'
require_relative 'sidecar/mesos'
require_relative 'sidecar/mongo'
require_relative 'sidecar/web'
require_relative 'sidecar/zk'

@framework_name               = ENV['APP_NAME'] || 'mongodb-sidecar'
@mesos_url                    = ENV['MESOS_URL'] || 'http://master.mezos/mesos'
@zookeeper_url                = ENV['ZK_URL'] || 'zk-1.zk:2181'
@mongo_path                   = ENV['MONGO_PATH'] || '/usr/local/bin'
@mongo_password               = ENV['MONGODB_PASSWORD']
@mongo_rs_name                = ENV['MONGODB_REPLSET']
@azure_storage_account_name   = ENV['AZURE_STORAGE_ACCOUNT_NAME']
@azure_storage_access_key     = ENV['AZURE_STORAGE_ACCESS_KEY']
@azure_storage_container_name = ENV['AZURE_STORAGE_CONTAINER_NAME']

@delay                   = ENV['DELAY'].to_i || 10

@logger                  = Cabin::Channel.new
@logger.level            = ENV['LOG_LEVEL'] || :info
@logger.subscribe(STDOUT)

@logger.info("STARTING mongodb-sidecar helper", {
  zk_url: @zookeeper_url,
  mesos_url: @mesos_url,
  framework_name: @framework_name,
  mongo_path: @mongo_path
})


# initializing instances
zk = MyZK.instance
zk.setup(@zookeeper_url, @framework_name)
@logger.info("ZK inited", {zk_info: zk.inspect})

mesos = Mesos.instance
mesos.setup(@mesos_url, @framework_name, zk, @logger)
@logger.info("Mesos inited", {mesos_info: mesos.version.to_s})

mongo = MyMongo.instance
mongo.setup(@mongo_path, zk, @logger, @mongo_password, @mongo_rs_name)
@logger.info("Mongo inited", {mongo_info: mongo.inspect})

if @azure_storage_account_name && @azure_storage_access_key && @azure_storage_container_name
  azure = MyAzure.instance
  azure.setup(@azure_storage_account_name, @azure_storage_access_key, @azure_storage_container_name, @logger)
else
  azure = nil
end

Thread::abort_on_exception = true
main_thread = Thread.new do
  # initialize or add unuzed servers to replica
  while(true) do
    begin
      if zk.replicaset_ititiated?
        mongo.add_to_replicaset(mesos.find_unused_servers)
      else
        mongo.init_replicaset(mesos.find_unused_servers)
      end
      sleep(@delay)
    rescue SystemExit, Interrupt
      exit
    rescue Exception => e
      @logger.error("something bad happened", error: e.inspect)
      @logger.debug(e.backtrace)
      abort(e)
    end
  end
end

SinatraServer.set :logger, @logger
SinatraServer.set :mongo, mongo
SinatraServer.set :azure, azure
SinatraServer.run!

# It should never gets past here
# main_thread.join
