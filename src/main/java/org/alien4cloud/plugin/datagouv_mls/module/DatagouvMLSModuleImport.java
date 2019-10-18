package org.alien4cloud.plugin.datagouv_mls.module;

import alien4cloud.model.common.Tag;
import org.alien4cloud.tosca.catalog.events.AfterArchiveIndexed;
import org.alien4cloud.tosca.model.types.NodeType;
import org.alien4cloud.plugin.datagouv_mls.DatagouvMLSConfiguration;
import org.alien4cloud.plugin.datagouv_mls.DatagouvMLSConstants;
import org.alien4cloud.plugin.datagouv_mls.model.*;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;

import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

import com.fasterxml.jackson.databind.ObjectMapper;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

@Slf4j
@Component("datagouv_mls-module-import")
public class DatagouvMLSModuleImport implements ApplicationListener<AfterArchiveIndexed> {

    @Resource
    private DatagouvMLSConfiguration configuration;

    // Kafka producer
    Producer<String,String> producer;

    @Override
    public void onApplicationEvent(AfterArchiveIndexed event) {
       try {
           if (event.getArchiveRoot() != null) {
             log.info ("Processing " + event.getArchiveRoot().getArchive().getName() + "::" + event.getArchiveRoot().getArchive().getVersion());

             String version =  event.getArchiveRoot().getArchive().getVersion();

             Map<String, NodeType> nodeTypes = event.getArchiveRoot().getNodeTypes();
             if (nodeTypes != null) {

                List<Entity> entities = new ArrayList<Entity>();

                for (String nodename : nodeTypes.keySet()) {
                   NodeType node = nodeTypes.get(nodename);

                   boolean keepIt = false;
                   String level = null, levelw = null;

                   if (node.getTags() != null) {
                      for (Tag tag : node.getTags()) {
                         if (tag.getName().equals(DatagouvMLSConstants.COMPONENT_TYPE) && tag.getValue().equalsIgnoreCase("Module")) {
                           keepIt = true;
                         } else if (tag.getName().equals(DatagouvMLSConstants.MLS_FR_LEVEL)) {
                            level = tag.getValue();
                         } else if (tag.getName().equals(DatagouvMLSConstants.MLS_FR_LEVELW)) {
                            levelw = tag.getValue();
                         }
                      }
                   }

                   if (keepIt) {
                     log.info ("Processing node " + nodename);

                     Entity entity = new Entity();
                     entity.setTypeName(DatagouvMLSConstants.MODULE_NAME);
                     Attributes attributes = new Attributes();
                     attributes.setName(nodename.substring(nodename.lastIndexOf(".")+1));
                     attributes.setQualifiedName(nodename);
                     attributes.setVersion(version);
                     entity.setAttributes(attributes);
                     List<Classification> classifications = new ArrayList<Classification>();
                     Classification classification = new Classification();
                     classification.setTypeName(DatagouvMLSConstants.CLASSIFICATION_NAME);
                     Levels levels = new Levels();
                     levels.setLevel(level);
                     levels.setLevelwrite(levelw);
                     classification.setAttributes(levels);
                     classifications.add(classification);
                     entity.setClassifications(classifications);

                     entities.add(entity);

                   }
                }

                if (!entities.isEmpty()) {
                   String json = (new ObjectMapper()).writeValueAsString(entities);
                   doPublish(json);
                }
             }
           }
       } catch (Exception e) {
         log.error ("Got exception: " + e.getMessage());
       }
    }


    @PostConstruct
    public void init() {
        if ((configuration.getKafkaServers() == null) || (configuration.getTopic() == null)) {
            log.error("Kafka server is not configured.");
        } else {
            Properties props = new Properties();
            props.put("bootstrap.servers", configuration.getKafkaServers());

            producer = new KafkaProducer<String, String>(props, new StringSerializer(), new StringSerializer());
        }
    }

    @PreDestroy
    public void term() {
        if (producer != null) {
            // Close the kafka producer
            producer.close();
        }
    }

    private void doPublish(String json) {
        producer.send(new ProducerRecord<>(configuration.getTopic(),null,json));
        log.debug("=> KAFKA[{}] : {}",configuration.getTopic(),json);
    }

}
