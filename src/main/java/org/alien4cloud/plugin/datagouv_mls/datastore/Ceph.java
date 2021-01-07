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
import org.alien4cloud.plugin.datagouv_mls.model.ceph.*;

import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class Ceph extends DataStore {

   public String getTypeName() {
      return "aws_s3_pseudo_dir";
   }

    public Map<String,Entity> getEntities (Map<String, AbstractPropertyValue> properties, NodeTemplate service, int startGuid, int curGuid, FlowExecutionContext context) {
       Map<String,Entity> entities = new HashMap<String, Entity>();

       /* set guids */
       String bucketGuid = String.valueOf(curGuid);

       /* process pseudodir */
       String spseudodir = "";
       String protocol = "";
       String sbucket = "";
       String instance = "";
       Capability endpoint = safe(service.getCapabilities()).get("http");
       if (endpoint != null) {
          spseudodir = PropertyUtil.getScalarValue(safe(endpoint.getProperties()).get("pseudo_dir"));
          protocol = PropertyUtil.getScalarValue(safe(endpoint.getProperties()).get("protocol"));
          sbucket = PropertyUtil.getScalarValue(safe(endpoint.getProperties()).get("bucket_name"));
          instance = PropertyUtil.getScalarValue(safe(endpoint.getProperties()).get("artemis_instance_name"));
       }

       if ((spseudodir == null) || spseudodir.trim().equals("")) {
          log.warn ("No pseudo_dir set for " + service.getName());
          context.log().error("No pseudo_dir set for " + service.getName());
       }
       if ((sbucket == null) || sbucket.trim().equals("")) {
          log.warn ("No bucket_name set for " + service.getName());
          context.log().error("No bucket_name set for " + service.getName());
       }

       if ((instance == null) || instance.trim().equals("")) {
          log.warn ("No artemis_instance_name set for " + service.getName());
          context.log().error("No artemis_instance_name set for " + service.getName());
       }

       Entity pseudodir = new Entity();
       entities.put (String.valueOf(startGuid), pseudodir);
       pseudodir.setTypeName(getTypeName());
       pseudodir.setGuid(String.valueOf(startGuid));
       startGuid--;

       PseudodirAttributes pattribs = new PseudodirAttributes();
       pattribs.setName(spseudodir);
       pattribs.setObjectPrefix(spseudodir);
       pattribs.setQualifiedName(protocol + "://" + instance + "/" + sbucket + "/" + spseudodir);

       Entity ibucket = new Entity();
       ibucket.setGuid(bucketGuid);
       ibucket.setTypeName("aws_s3_bucket");
       ibucket.setQualifiedName(protocol + "://" + instance + "/" + sbucket);

       pattribs.setBucket(ibucket);
       pseudodir.setAttributes(pattribs);

       /* process bucket */
       Entity bucket = new Entity();
       bucket.setGuid(bucketGuid);
       bucket.setTypeName("aws_s3_bucket");
       Attributes battribs = new Attributes();
       battribs.setName(sbucket);
       battribs.setQualifiedName(protocol + "://" + instance + "/" + sbucket);
       bucket.setAttributes(battribs);
       entities.put (bucketGuid, bucket);

       return entities;
    }

    public String updateInput (String function, List<String> params, String user, String password) {
       if (function.equals(ToscaFunctionConstants.GET_PROPERTY) && params.get(2).equals("http") && params.get(3).equals("username")) {
          return user;
       } else if (function.equals(ToscaFunctionConstants.GET_PROPERTY) && params.get(2).equals("http") && params.get(3).equals("password")) {
          return password;
       } else {
          return null;
       }
    }

}
