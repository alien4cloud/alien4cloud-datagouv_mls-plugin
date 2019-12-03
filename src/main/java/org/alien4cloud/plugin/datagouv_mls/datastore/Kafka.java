package org.alien4cloud.plugin.datagouv_mls.datastore;

import static alien4cloud.utils.AlienUtils.safe;
import alien4cloud.utils.PropertyUtil;
import org.alien4cloud.tosca.model.definitions.AbstractPropertyValue;
import org.alien4cloud.tosca.model.definitions.ListPropertyValue;
import org.alien4cloud.tosca.model.definitions.ScalarPropertyValue;
import org.alien4cloud.tosca.model.templates.Capability;
import org.alien4cloud.tosca.model.templates.NodeTemplate;
import org.alien4cloud.tosca.model.templates.ServiceNodeTemplate;
import org.alien4cloud.tosca.normative.constants.ToscaFunctionConstants;
import org.alien4cloud.plugin.datagouv_mls.model.Attributes;
import org.alien4cloud.plugin.datagouv_mls.model.Entity;
import org.alien4cloud.plugin.datagouv_mls.model.kafka.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Kafka extends DataStore {

   public String getTypeName() {
      return "kafka_topic";
   }

    public Map<String,Entity> getEntities (Map<String, AbstractPropertyValue> properties, NodeTemplate service, int startGuid, int curGuid) {
       Map<String,Entity> entities = new HashMap<String, Entity>();

       /* get ip address from service attribute */
       String ipAddress = "localhost";
       if (service instanceof ServiceNodeTemplate) {
          ServiceNodeTemplate serviceNodeTemplate = (ServiceNodeTemplate)service;
          ipAddress = safe(serviceNodeTemplate.getAttributeValues()).get("capabilities.kafka_topic.ip_address");
       }

       /* process topic */
       String stopic = "";
       stopic = PropertyUtil.getScalarValue(safe(service.getProperties()).get("topic_name"));
       Entity topic = new Entity();
       entities.put (String.valueOf(startGuid), topic);
       topic.setTypeName(getTypeName());
       topic.setGuid(String.valueOf(startGuid));
       startGuid--;

       TopicAttributes tattribs = new TopicAttributes();
       tattribs.setName(stopic);
       tattribs.setQualifiedName(stopic + "@" + ipAddress);
       tattribs.setTopic(stopic);
       tattribs.setUri(stopic + "@" + ipAddress);
       tattribs.setClusterName(ipAddress);
       topic.setAttributes(tattribs);

       return entities;
    }

    public String updateInput (String function, List<String> params, String user, String password) {
       if (function.equals(ToscaFunctionConstants.GET_PROPERTY) && params.get(2).equals("kafka_topic") && params.get(3).equals("username")) {
          return user;
       } else if (function.equals(ToscaFunctionConstants.GET_PROPERTY) && params.get(2).equals("kafka_topic") && params.get(3).equals("password")) {
          return password;
       } else {
          return null;
       }
    }

}