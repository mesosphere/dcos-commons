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
@delay                   = ENV['SIDECAR_DELAY'] || 10

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
zk = MyZK.instance
zk.setup(@zookeeper_url, @framework_name)
@logger.info("ZK inited", {zk_info: zk.inspect})

mesos = Mesos.instance
mesos.setup(@mesos_url, @framework_name, zk, @logger)
@logger.info("Mesos inited", {mesos_info: mesos.version.to_s})

mongo = MyMongo.instance
mongo.setup(@mongo_binary, zk, @logger)
@logger.info("Mongo inited", {mongo_info: mongo.inspect})


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
SinatraServer.run!

# It should never gets past here
# main_thread.join
