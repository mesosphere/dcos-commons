#!/usr/bin/env ruby
require 'rubygems'
require 'bundler'
require 'singleton'
require 'em/pure_ruby'

Bundler.require

require_relative 'sidecar/mesos'
require_relative 'sidecar/mongo'
require_relative 'sidecar/zk'
require_relative 'sidecar/web'

@framework_name          = ENV['APP_NAME'] || 'mongodb-sidecar'
@mesos_url               = ENV['MESOS_URL'] || 'http://master.mezos/mesos'
@zookeeper_url           = ENV['ZK_URL'] || 'zk-1.zk:2181'
@mongo_binary            = ENV['MONGO_BINARY'] || '/usr/local/bin/mongo'
@mongo_password          = ENV['MONGODB_PASSWORD']
@mongo_rs_name           = ENV['MONGODB_REPLSET']
@delay                   = ENV['DELAY'].to_i || 10

@logger                  = Cabin::Channel.new
@logger.level            = ENV['LOG_LEVEL'] || :info
@logger.subscribe(STDOUT)

@logger.info("STARTING mongodb-sidecar helper", {
  zk_url: @zookeeper_url,
  mesos_url: @mesos_url,
  framework_name: @framework_name,
  mongo_binary: @mongo_binary
})


# initializing instances
@zk = MyZK.instance
@zk.setup(@zookeeper_url, @framework_name)
@logger.info("ZK inited", {zk_info: @zk.inspect})

@mesos = Mesos.instance
@mesos.setup(@mesos_url, @framework_name, @zk, @logger)
@logger.info("Mesos inited", {mesos_info: @mesos.version.to_s})

@mongo = MyMongo.instance
@mongo.setup(@mongo_binary, @zk, @logger, @mongo_password, @mongo_rs_name)
@logger.info("Mongo inited", {mongo_info: @mongo.inspect})
