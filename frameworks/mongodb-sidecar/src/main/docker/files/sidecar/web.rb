require 'sinatra/base'

class SinatraServer < Sinatra::Application
  set :bind, '0.0.0.0'
  set :server, %w[thin webrick]

  get '/' do
    settings.logger.info("test")
    'Hello world!'
  end

  get '/rs_status' do
    status = settings.mongo.rs_status
    settings.logger.info("rs_status", {status: status})
    status
  end

  delete '/remove/:host' do |host|
    settings.logger.warn("Deleting agent from ReplicaSet:", {host: host})
    settings.mongo.rs_remove(host)
  end
end
