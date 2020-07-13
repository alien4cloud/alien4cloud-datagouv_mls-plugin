package org.alien4cloud.plugin.datagouv_mls.model;

import java.util.List;
import java.util.Map;

import lombok.Getter;
import lombok.Setter;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

@Getter
@Setter

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class Application {

   List<Entity> entities;

   Map<String,Entity> referredEntities;

   /* response fields in case of error */
   String code;
   String message;
}
