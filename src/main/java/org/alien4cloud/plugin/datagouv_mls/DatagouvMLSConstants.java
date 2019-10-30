package org.alien4cloud.plugin.datagouv_mls;

import org.alien4cloud.plugin.datagouv_mls.datastore.*;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DatagouvMLSConstants {

    // meta properties values
    public static final String COMPONENT_TYPE = "A4C_META_Type de composant";
    public static final String MLS_FR_LEVEL = "A4C_META_MLS_France_level";
    public static final String MLS_FR_LEVELW = "A4C_META_MLS_France_levelwrite";

    // type name for generated JSON
    public static final String MODULE_NAME = "artemis_module_imported";
    public static final String CLASSIFICATION_NAME = "MLS_France";

    public static final String APPLI_NAME = "artemis_application_instance";
    public static final String MODULE_INSTANCE_NAME = "artemis_module_instance";

    // known datastores
    public static Map<String, Class> dataStoreTypes = Stream.of(new Object[][] { 
        { "artemis.redis.pub.capabilities.Redis", Redis.class }, 
        { "artemis.mongodb.pub.capabilities.MongoDb", Mongodb.class }, 
        { "artemis.mariadb.pub.capabilities.Mariadb", Mariadb.class }, 
        { "artemis.postgresql.pub.capabilities.PostgreSQLEndpoint", Postgresql.class },
        { "artemis.accumulo.pub.capabilities.Accumulo", Accumulo.class },
        { "artemis.cassandra.pub.capabilities.CassandraDb", Cassandra.class },
        { "artemis.elasticsearch.pub.capabilities.ElasticSearchRestAPI", Elasticsearch.class },
        { "artemis.kafka.pub.capabilities.KafkaTopic", Kafka.class },
        { "artemis.hadoop.pub.capabilities.HdfsRepository", Hadoop.class },
        { "artemis.ceph.pub.capabilities.CephBucketEndpoint", Ceph.class }

    }).collect(Collectors.toMap(data -> (String) data[0], data -> (Class) data[1]));


}
