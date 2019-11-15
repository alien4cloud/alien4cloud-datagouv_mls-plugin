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
import org.alien4cloud.plugin.datagouv_mls.model.ceph.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Ceph extends DataStore {

   public String getTypeName() {
      return "aws_s3_pseudo_dir";
   }

    public Map<String,Entity> getEntities (Map<String, AbstractPropertyValue> properties, NodeTemplate service, int startGuid, int curGuid) {
       Map<String,Entity> entities = new HashMap<String, Entity>();

       /* set guids */
       String bucketGuid = String.valueOf(curGuid);

       /* get ip address from service attribute */
       String ipAddress = "localhost";
       if (service instanceof ServiceNodeTemplate) {
          ServiceNodeTemplate serviceNodeTemplate = (ServiceNodeTemplate)service;
          ipAddress = safe(serviceNodeTemplate.getAttributeValues()).get("capabilities.http.ip_address");
       }

       /* process pseudodir */
       String spseudodir = "";
       String protocol = "";
       String sbucket = "";
       Capability endpoint = safe(service.getCapabilities()).get("http");
       if (endpoint != null) {
          spseudodir = PropertyUtil.getScalarValue(safe(endpoint.getProperties()).get("pseudo_dir"));
          protocol = PropertyUtil.getScalarValue(safe(endpoint.getProperties()).get("protocol"));
          sbucket = PropertyUtil.getScalarValue(safe(endpoint.getProperties()).get("bucket_name"));
       }
       Entity pseudodir = new Entity();
       entities.put (String.valueOf(startGuid), pseudodir);
       pseudodir.setTypeName(getTypeName());
       pseudodir.setGuid(String.valueOf(startGuid));
       startGuid--;

       PseudodirAttributes pattribs = new PseudodirAttributes();
       pattribs.setName(spseudodir);
       pattribs.setObjectPrefix(spseudodir);
       pattribs.setQualifiedName(protocol + "://" + ipAddress + "/" + sbucket + "/" + spseudodir);

       Entity ibucket = new Entity();
       ibucket.setGuid(bucketGuid);
       ibucket.setTypeName("aws_s3_bucket");
       ibucket.setQualifiedName(protocol + "://" + ipAddress + "/" + sbucket);

       pattribs.setBucket(ibucket);
       pseudodir.setAttributes(pattribs);

       /* process bucket */
       Entity bucket = new Entity();
       bucket.setGuid(bucketGuid);
       bucket.setTypeName("aws_s3_bucket");
       Attributes battribs = new Attributes();
       battribs.setName(sbucket);
       battribs.setQualifiedName(protocol + "://" + ipAddress + "/" + sbucket);
       bucket.setAttributes(battribs);
       entities.put (bucketGuid, bucket);

       return entities;
    }

    public String updateInput (String function, List<String> params, String user, String password) {
       if (function.equals(ToscaFunctionConstants.GET_PROPERTY) && params.get(2).equals("http") && params.get(3).equals("access_key")) {
          return user;
       } else if (function.equals(ToscaFunctionConstants.GET_PROPERTY) && params.get(2).equals("http") && params.get(3).equals("secret_key")) {
          return password;
       } else {
          return null;
       }
    }

}
