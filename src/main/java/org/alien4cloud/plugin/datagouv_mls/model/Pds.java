package org.alien4cloud.plugin.datagouv_mls.model;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@Getter
@Setter

@JsonIgnoreProperties(ignoreUnknown = true)
public class Pds {
   String zone;
   String pds;
   String erreurZone;

   List<Module> modules;

   /* response fields in case of error */
   String code;
   String message;
}
