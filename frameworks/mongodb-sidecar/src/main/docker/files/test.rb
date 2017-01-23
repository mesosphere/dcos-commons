#!/usr/bin/env ruby
require 'rubygems'
require 'bundler/setup'
require 'httparty'
require 'cabin'
require 'json'
require 'jsonpath'
require 'sinatra/base'
require 'em/pure_ruby'
require 'zk'
require 'singleton'

require_relative 'sidecar/mesos'
require_relative 'sidecar/mongo'
require_relative 'sidecar/zk'
require_relative 'sidecar/web'

@framework_name          = ENV['APP_NAME'] || 'mongodb-sidecar'
@mesos_url               = ENV['MESOS_URL'] || 'http://172.17.0.2/mesos'
@zookeeper_url           = ENV['ZK_URL'] || 'zk-1.zk:2181'
@mongo_binary            = ENV['MONGO_BINARY'] || '/usr/local/bin/mongo'

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

mongo = Mongo.instance
mongo.setup(@mongo_binary, zk, @logger)
@logger.info("Mongo inited", {mongo_info: mongo.inspect})

zk.clean!

puts "Server: #{mongo.server_url}"
unused = mesos.find_unused_servers
puts "Unused: #{unused}"


begin
  # mongo.init_replicaset(unused)
  # sleep 5
  # puts mongo.rs_status
rescue Exception => e
  puts e.backtrace
end
