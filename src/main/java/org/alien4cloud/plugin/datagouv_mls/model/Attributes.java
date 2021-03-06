package org.alien4cloud.plugin.datagouv_mls.model;

import lombok.Getter;
import lombok.Setter;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@Getter
@Setter

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class Attributes {

   String name;

   String qualifiedName;

   String bacASable;

   String version;

   String startTime;

   String endTime;

   String status;

   List<String> accessPointUser;

   Entity instanceOf;

   Entity memberOf;

   List<Entity> inputs;

   List<Entity> outputs;

   /* response fields */
   String tokenid;
   String pwdid;
}
