class Mongo
  include Singleton

  def setup(mongo_binary, zk, logger, rs_name = 'rs')
    @mongo_binary = mongo_binary
    @zk = zk
    @logger = logger
    @rs_name = rs_name
  end

  # def rs_status(server = @zk.available_servers.first)
  #   mongo_host, mongo_port = server.split(/:/)
  #   # mongo_rpc port is hardcoded to mongo_port + 1000
  #   mongo_rpc = "http://#{mongo_host}:#{mongo_port.to_i + 1000}/replSetGetStatus"
  #   mongo_state = HTTParty.get(mongo_rpc)
  #   @logger.debug("mongo-state", {
  #     mongo_rpc_url: mongo_rpc,
  #     body:      mongo_state.body,
  #     code:      mongo_state.code,
  #     message:   mongo_state.message,
  #     headers:   mongo_state.headers
  #   })
  #   return mongo_state
  # end

  def rs_status
    db_eval(server_url, "rs.isMaster()")
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

  def server_url(server = nil)
    if server
      server
    else
      "#{@zk.available_servers.join(',')}/?replicaSet=#{@rs_name}"
    end
  end

  def db_eval(host_url, expression)
    `#{@mongo_binary} #{host_url} --eval "JSON.stringify(#{expression})"`.split(/\n+/).last
  end

  def rs_initiate(host)
    db_eval(server_url(host), "rs.initiate()")
    @zk.set_initiated
    @zk.persist_member(host)
  end

  def rs_add(host)
    db_eval(server_url, "rs.add('#{host}')")
    @zk.persist_member(host)
  end

  def rs_remove(host)
    db_eval(server_url, "rs.remove('#{host}')")
    @zk.remove_member(host)
  end

  # def find_mongo_master(server_list)
  #   server_list.each do |server|
  #     mong_state = rs_status(server)
  #     master = mongo_state['members'].select{ |member| member['stateStr'] == 'PRIMARY'}[0]['name'] rescue nil
  #     return master if master
  #   end
  #   @logger.error("No mongo master found, exiting")
  #   abort
  # end
end
