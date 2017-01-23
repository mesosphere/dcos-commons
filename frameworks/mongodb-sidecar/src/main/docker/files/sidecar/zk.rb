class MyZK
  include Singleton

  def setup(zk_url, framework_name)
    @zk ||= ZK.new(zk_url)
    @zk_prefix = "/dcos/frameworks/#{framework_name}"
  end

  def inspect
    @zk.inspect
  end

  # query zookeeper for replicaset state
  def replicaset_ititiated?
    path = "#{@zk_prefix}/initiated"
    @zk.exists?(path)
  end

  # set zk flag upon initialisation
  def set_initiated
    key = "#{@zk_prefix}/initiated"
    @zk.mkdir_p(key) unless @zk.exists?(key)
  end

  def available_servers
    key = "#{@zk_prefix}/rs_members"
    @zk.exists?(key) ? @zk.children(key) : []
  end

  def persist_member(host)
    key = "#{@zk_prefix}/rs_members/#{host}"
    @zk.mkdir_p(key)
  end

  def remove_member(host)
    key = "#{@zk_prefix}/rs_members/#{host}"
    @zk.delete(key)
  end

  def clean!
    @zk.rm_rf(@zk_prefix)
  end
end
