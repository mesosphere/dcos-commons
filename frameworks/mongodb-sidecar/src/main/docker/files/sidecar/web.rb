class SinatraServer < Sinatra::Application
  set :bind, '0.0.0.0'
  set :server, %w[thin webrick]

  get '/' do
    @zk_rs_members = settings.mongo.zk.available_servers
    if @zk_rs_members.empty?
      erb :initializing
    else
      @master_status = settings.mongo.master_status
      @rs_status = settings.mongo.rs_status
      @server_url = settings.mongo.server_url
      @mongo = settings.mongo
      erb :index
    end
  end

  get '/master_status' do
    settings.mongo.master_status
  end

  get '/rs_status' do
    settings.mongo.rs_status
  end

  # https://docs.mongodb.com/manual/tutorial/force-member-to-be-primary/
  get '/forcemaster/:host' do |host|
    settings.mongo.forcemaster(host)
    redirect '/'
  end

  get '/remove/:host' do |host|
    settings.logger.warn("Deleting agent from ReplicaSet:", {host: host})
    settings.mongo.remove(host)
    redirect '/'
  end


end
