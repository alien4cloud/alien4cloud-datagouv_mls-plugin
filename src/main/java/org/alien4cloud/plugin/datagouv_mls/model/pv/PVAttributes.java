package org.alien4cloud.plugin.datagouv_mls.model.pv;

import org.alien4cloud.plugin.datagouv_mls.model.Attributes;

import lombok.Getter;
import lombok.Setter;

import com.fasterxml.jackson.annotation.JsonInclude;

@Getter
@Setter

@JsonInclude(JsonInclude.Include.NON_NULL)
public class PVAttributes extends Attributes {
   String accessModes;
   String storageClass;
}
