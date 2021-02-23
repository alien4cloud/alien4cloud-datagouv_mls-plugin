package org.alien4cloud.plugin.datagouv_mls.datastore;

import static alien4cloud.utils.AlienUtils.safe;
import alien4cloud.utils.PropertyUtil;
import org.alien4cloud.alm.deployment.configuration.flow.FlowExecutionContext;
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

import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class Kafka extends DataStore {

   public String getTypeName() {
      return "kafka_topic";
   }

    public Map<String,Entity> getEntities (Map<String, AbstractPropertyValue> properties, NodeTemplate service, int startGuid, int curGuid, FlowExecutionContext context) {
       Map<String,Entity> entities = new HashMap<String, Entity>();

       /* process topic */
       String stopic = "";
       stopic = PropertyUtil.getScalarValue(safe(service.getProperties()).get("topic_name"));

       if ((stopic == null) || stopic.trim().equals("")) {
          log.warn ("No topic_name set for " + service.getName());
          context.log().error("No topic_name set for " + service.getName());
       }

       /* process instance name */
       String instance = "";
       Capability endpoint = safe(service.getCapabilities()).get("kafka_topic");
       if (endpoint != null) {
          instance = PropertyUtil.getScalarValue(safe(endpoint.getProperties()).get("artemis_instance_name"));
       }

       if ((instance == null) || instance.trim().equals("")) {
          log.warn ("No artemis_instance_name set for " + service.getName());
          context.log().error("No artemis_instance_name set for " + service.getName());
       }

       Entity topic = new Entity();
       entities.put (String.valueOf(startGuid), topic);
       topic.setTypeName(getTypeName());
       topic.setGuid(String.valueOf(startGuid));
       startGuid--;

       TopicAttributes tattribs = new TopicAttributes();
       tattribs.setName(stopic);
       tattribs.setQualifiedName(stopic + "@" + instance);
       tattribs.setTopic(stopic);
       tattribs.setUri(stopic + "@" + instance);
       tattribs.setClusterName(instance);
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
