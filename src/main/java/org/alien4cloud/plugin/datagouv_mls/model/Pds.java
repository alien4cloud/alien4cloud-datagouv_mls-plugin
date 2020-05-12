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
   String erreurZone;

   List<Module> modules;
}
