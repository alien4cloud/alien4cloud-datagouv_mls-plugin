package org.alien4cloud.plugin.datagouv_mls.datastore;

import org.alien4cloud.alm.deployment.configuration.flow.FlowExecutionContext;
import org.alien4cloud.tosca.model.definitions.AbstractPropertyValue;
import org.alien4cloud.tosca.model.definitions.ListPropertyValue;
import org.alien4cloud.tosca.model.templates.NodeTemplate;
import org.alien4cloud.plugin.datagouv_mls.model.Entity;

import java.util.List;
import java.util.Map;

public abstract class DataStore {

   public abstract String getTypeName();

   public abstract Map<String,Entity> getEntities (Map<String, AbstractPropertyValue> properties, NodeTemplate service, int startGuid, int curGuid,
                                                   FlowExecutionContext context);

   public int getNbSets(Map<String, AbstractPropertyValue> properties) {
       AbstractPropertyValue datasets = properties.get("datasets");
       if (datasets instanceof ListPropertyValue) {
          List<Object> vals = ((ListPropertyValue)datasets).getValue();
          return vals.size();
       } else {
          return 1;
       }
    }

    public String updateInput (String function, List<String> params, String user, String password) {
       return null;
    }
}
