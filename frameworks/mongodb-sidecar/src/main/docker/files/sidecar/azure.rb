class MyAzure
  include Singleton

  def setup(storage_account_name, storage_access_key, container_name = 'test-container', logger)
    Azure.config.storage_account_name = storage_account_name
    Azure.config.storage_access_key = storage_access_key
    @azure_blob_service = Azure::Blob::BlobService.new
    @logger = logger
    begin
      @container = @azure_blob_service.create_container(container_name)
    rescue Exception => e
      @logger.warn("Can't create container", error: e)
      @container = @azure_blob_service.get_container_metadata(container_name)
    end
  end

  def blob_service
    @azure_blob_service
  end

  def list
    containers = @azure_blob_service.list_containers()
    containers.each do |container|
      blobs = @azure_blob_service.list_blobs(container.name)
      blobs.each do |blob|
        puts blob.name
      end
    end
  end

  def upload(filename)
    content = File.open(filename, "rb") { |file| file.read }
    blob = @azure_blob_service.create_block_blob(@container.name, File.basename(filename), content)
  end

  def download(blobname)
    blob, content = @azure_blob_service.get_blob(@container.name, blobname)
    File.open(blobname,"wb") {|f| f.write(content)}
  end
end
