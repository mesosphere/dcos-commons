---
layout: layout.pug
navigationTitle: 
title: Cassandra Settings
menuWeight: 24
excerpt:

---

You can configure most of the settings exposed in Apache Cassandra's `cassandra.yaml` configuration file in DC/OS Apache Cassandra. For information about these settings, see the Apache Cassandra [documentation](http://cassandra.apache.org/doc/latest/configuration/cassandra_config_file.html). Settings that you can configure include:

*   `cluster_name`
*   `num_tokens`
*   `hinted_handoff_enabled`
*   `max_hint_window_in_ms`
*   `hinted_handoff_throttle_in_kb`
*   `max_hints_delivery_threads`
*   `hints_flush_period_in_ms`
*   `max_hints_file_size_in_mb`
*   `batchlog_replay_throttle_in_kb`
*   `authenticator`
*   `authorizer`
*   `partitioner`
*   `key_cache_save_period`
*   `row_cache_size_in_mb`
*   `row_cache_save_period`
*   `commitlog_sync_period_in_ms`
*   `commitlog_segment_size_in_mb`
*   `commitlog_total_space_in_mb`
*   `concurrent_reads`
*   `concurrent_writes`
*   `concurrent_counter_writes`
*   `concurrent_materialized_view_writes`
*   `memtable_allocation_type`
*   `index_summary_resize_interval_in_minutes`
*   `storage_port`
*   `ssl_storage_port`
*   `start_native_transport`
*   `native_transport_port`
*   `start_rpc`
*   `rpc_port`
*   `rpc_keepalive`
*   `thrift_framed_transport_size_in_mb`
*   `tombstone_warn_threshold`
*   `tombstone_failure_threshold`
*   `column_index_size_in_kb`
*   `batch_size_warn_threshold_in_kb`
*   `batch_size_fail_threshold_in_kb`
*   `compaction_throughput_mb_per_sec`
*   `sstable_preemptive_open_interval_in_mb`
*   `read_request_timeout_in_ms`
*   `range_request_timeout_in_ms`
*   `write_request_timeout_in_ms`
*   `counter_write_request_timeout_in_ms`
*   `internode_compression`
*   `cas_contention_timeout_in_ms`
*   `truncate_request_timeout_in_ms`
*   `request_timeout_in_ms`
*   `dynamic_snitch_update_interval_in_ms`
*   `dynamic_snitch_reset_interval_in_ms`
*   `dynamic_snitch_badness_threshold`
*   `auto_snapshot`
*   `roles_update_interval_in_ms`
*   `permissions_update_interval_in_ms`
*   `key_cache_keys_to_save`
*   `row_cache_keys_to_save`
*   `counter_cache_keys_to_save`
*   `file_cache_size_in_mb`
*   `memtable_heap_space_in_mb`
*   `memtable_offheap_space_in_mb`
*   `memtable_cleanup_threshold`
*   `memtable_flush_writers`
*   `listen_on_broadcast_address`
*   `internode_authenticator`
*   `native_transport_max_threads`
*   `native_transport_max_frame_size_in_mb`
*   `native_transport_max_concurrent_connections`
*   `native_transport_max_concurrent_connections_per_ip`
*   `rpc_min_threads`
*   `rpc_max_threads`
*   `rpc_send_buff_size_in_bytes`
*   `rpc_recv_buff_size_in_bytes`
*   `concurrent_compactors`
*   `stream_throughput_outbound_megabits_per_sec`
*   `inter_dc_stream_throughput_outbound_megabits_per_sec`
*   `streaming_socket_timeout_in_ms`
*   `phi_convict_threshold`
*   `buffer_pool_use_heap_if_exhausted`
*   `disk_optimization_strategy`
*   `max_value_size_in_mb`
*   `otc_coalescing_strategy`
