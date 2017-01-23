class Mesos
  include Singleton

  def setup(mesos_url, framework_name, zk, logger)
    @mesos_url = mesos_url
    @framework_name = framework_name
    @zk = zk
    @logger = logger
  end

  def version
    HTTParty.get("#{@mesos_url}/version")
  end

  # query mesos state for running servers instances
  # query zk for already joined to rs members
  def find_unused_servers
    mesos_state = HTTParty.get("#{@mesos_url}/state")

    begin
      query = "$..frameworks[?(@.name == '#{@framework_name}')]..tasks[?(@.name =~ /server/)]..ip_address"
      mesos_hosts = JsonPath.on(mesos_state.to_json, query).map{|ip| "#{ip}:27017"}

      # fwk = mesos_state['frameworks'].select {|f| f['name'] == @framework_name}[0]
      # unless fwk
      #   @logger.error("No framework found", {framework_name: @framework_name})
      #   raise "No framework found"
      # end
      # hosts = fwk['tasks']
      #         .select{|t| t['name'] =~ /server/}
      #         .map{|t| "#{t['statuses'][0]['container_status']['network_infos'][0]['ip_addresses'][0]['ip_address']}:27017"}

      result = mesos_hosts - @zk.available_servers
      if result.empty?
        @logger.info("No uninitialized endpoints found")
      else
        @logger.info("Uninitialized endpoints", { hosts: result })
      end
      return result
    rescue Exception => e
      abort(e.inspect)
    end
  end
end
