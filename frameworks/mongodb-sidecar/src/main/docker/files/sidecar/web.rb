class SinatraServer < Sinatra::Application
  set :bind, '0.0.0.0'
  set :server, %w[thin webrick]

  get '/' do
    @zk_rs_members = settings.mongo.zk.available_servers
    if @zk_rs_members.empty?
      erb :initializing
    else
      # @master_status = settings.mongo.master_status
      # @rs_status = settings.mongo.rs_status
      # @server_url = settings.mongo.server_url
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
    settings.logger.warn("Forcing host to be primary", {host: host})
    settings.mongo.forcemaster(host)
    redirect '/'
  end

  get '/remove/:host' do |host|
    settings.logger.warn("Deleting agent from ReplicaSet:", {host: host})
    settings.mongo.remove(host)
    redirect '/'
  end

  get '/reset_priority' do
    settings.logger.warn("Enforcing priority: 1 for all servers:", {priority: 1})
    settings.mongo.reset_priority
    redirect '/'
  end

  get '/backup/azure' do
    #dump
    filename = settings.mongo.backup

    #archive
    `tar cvjf #{filename}.tar.bz2 #{filename}`

    #encrypt
    #generate random key & encrypt it with public key
    `openssl rand -base64 128 -out #{filename}.key.bin`
    `openssl rsautl -encrypt -inkey public.pem -pubin -in #{filename}.key.bin -out #{filename}.key.bin.enc`

    #encrypt archive with random key
    `openssl enc -aes-256-cbc -salt -in #{filename}.tar.bz2 -out #{filename}.tar.bz2.enc -pass file:./#{filename}.key.bin`

    #upload
    uploaded = []
    uploaded << settings.azure.upload("#{filename}.tar.bz2.enc")
    uploaded << settings.azure.upload("#{filename}.key.bin.enc")

    settings.logger.info("Backup successful", files: uploaded)
    #clean
    `rm -rf #{filename}*`
    redirect '/'
  end

  get '/backup/azure/restore/:name' do |name|
    settings.azure.download("#{name}.key.bin.enc")
    settings.azure.download("#{name}.tar.bz2.enc")

    #decrypt random key with private key
    `openssl rsautl -decrypt -inkey private.pem -in #{name}.key.bin.enc -out #{name}.key.bin`
    #decrypt archive with random key
    `openssl enc -d -aes-256-cbc -in #{name}.tar.bz2.enc -out #{name}.tar.bz2 -pass file:./#{name}.key.bin`

    #unzip
    `tar xvf #{name}.tar.bz2`

    settings.mongo.restore(name)
    redirect '/'
  end
end
