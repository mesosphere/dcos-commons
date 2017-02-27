class MyMongo
  include Singleton

  def setup(mongo_path, zk, logger, mongo_password, rs_name = 'rs')
    @mongo_path = mongo_path
    @zk = zk
    @logger = logger
    @mongo_password = mongo_password
    @rs_name = rs_name
  end

  # needed to proxying to zk from mongo instance
  def zk
    @zk
  end

  def driver
    @mongo ||= Mongo::Client.new(server_url, {user: 'admin', password: @mongo_password})
  end

  def discovered_servers
    driver.cluster.servers.map(&:address).map(&:to_s)
  end

  def rs_status(server = nil)
    # db_eval(server_url, "rs.status()")
    status = driver.command({replSetGetStatus: 1}).documents[0]
    if server
      status[:members].select{|m| m[:name] == server}[0]
    else
      status
    end
  end

  def rs_config
    driver.command({replSetGetConfig: 1}).documents[0][:config]
  end

  def rs_reconfig(config)
    config[:version] += 1
    driver.command({replSetReconfig: config})
  end

  def forcemaster(server)
    config = rs_config
    config[:members].select{|m| m[:host] == server}[0][:priority] = 1
    config[:members].select{|m| m[:host] != server}.each {|s| s[:priority] = 0.5}
    rs_reconfig(config)
  end

  def reset_priority
    config = rs_config
    config[:members].each {|s| s[:priority] = 1}
    rs_reconfig(config)
  end

  def member_priority(server)
    rs_config[:members].select{|m| m[:host] == server}[0][:priority]
  end

  def remove(server)
    config = rs_config
    config[:members] = config[:members].select{|m| m[:host] != server}
    rs_reconfig(config)
  end

  def optime_lag(server)
    status = rs_status
    server_status = status[:members].select{|m| m[:name] == server}[0]
    if server_status[:state] == 1
      return 0
    elsif server_status[:state] == 2
      master_status = status[:members].select{|m| m[:state] == 1}[0]
      # breaks 3.2.x
      # return status[:optimes][:lastCommittedOpTime][:ts].seconds - server_status[:optime][:ts].seconds
      return master_status[:optime][:ts].seconds - server_status[:optime][:ts].seconds
    end
  end

  def master_status
    # db_eval(server_url, "rs.isMaster()")
    JSON.pretty_generate(driver.cluster.next_primary.description.config)
  end

  def init_replicaset(server_list)
    if server_list.empty?
      @logger.warn("No servers found for RS initialization, waiting for one")
    else
      master = server_list.shift

      @logger.info("Initializing rs on", { host: master})
      rs_initiate(master)
      sleep 10
      add_to_replicaset(server_list) unless server_list.empty?
    end
  end

  def add_to_replicaset(server_list)
    if server_list.empty?
      @logger.info("State is not changed, nothing to do")
    else
      server_list.each do |server|
        @logger.info("Adding host to rs", { host: server })
        rs_add(server)
        sleep 10
      end
    end
  end

  def remove_from_replicaset(server_list)
    if server_list.empty?
      @logger.info("State is not changed, nothing to do")
    else
      server_list.each do |server|
        @logger.info("Removing host from rs", { master: master, host: server })
        rs_remove(server)
      end
    end
  end

  # private

  def server_url(server: nil, db: 'admin', format: 'mongo')
    if server
      "mongodb://#{server}/#{db}"
    elsif format == 'mongo'
      "mongodb://#{@zk.available_servers.join(',')}/#{db}?replicaSet=#{@rs_name}"
    else
      "#{@rs_name}/#{@zk.available_servers.join(',')}"
    end
  end

  def db_eval(host_url, expression)
    `#{@mongo_path}/mongo #{host_url} -u admin -p #{@mongo_password} --quiet --eval 'JSON.stringify(#{expression})'`.split(/\n+/).last
  end

  def rs_initiate(host)
    config = %{var cfg = {_id:"#{@rs_name}", "members":[{_id:0, "host":"#{host}"}]}; printjson(rs.initiate(cfg));}

    @logger.info("RS init config:", config: config)
    reply = JSON.parse `#{@mongo_path}/mongo #{server_url(server: host)} -u admin -p #{@mongo_password} --quiet --eval '#{config}'`
    if reply["ok"] == 1
      @logger.info("Replica set initilized on host", host: host)
      @zk.set_initiated
      @zk.persist_member(host)
    else
      @logger.error("Can't add host to replicaset", error: reply)
    end
  end

  def rs_add(host)
    reply = db_eval(server_url, %{rs.add("#{host}")})
    @logger.warn("RS added", reply: reply)
    @zk.persist_member(host)
  end

  def rs_remove(host)
    db_eval(server_url, %{rs.remove("#{host}")})
    @zk.remove_member(host)
  end

  def add_user(db, name, password)
    # begin
    #   driver.command({createUser: "#{name}", pwd: "#{password}", roles: [{role: "readWrite", db: "#{db}"}]})
    # rescue Exception => e
    #   e
    # end

    db_eval(server_url, %{db.getSiblingDB("#{db}").createUser({user: "#{name}", pwd: "#{password}", roles: [{role: "readWrite", db: "#{db}"}]})})
  end

  def backup
    time_now = Time.now.strftime("%Y%m%d%H%M%S")
    filename = "dump-#{time_now}"
    secondary = driver.cluster.servers.select{|s| s.secondary?}.sample
    `#{@mongo_path}/mongodump -h #{secondary.address.to_s} -u admin -p #{@mongo_password} --oplog -o #{filename}`
    filename
  end

  def restore(name)
    `#{@mongo_path}/mongorestore -h #{server_url(format: 'cmd')} -u admin -p #{@mongo_password} --drop --oplogReplay #{name}`
  end
end
