package org.alien4cloud.plugin.datagouv_mls.model.accumulo;

import org.alien4cloud.plugin.datagouv_mls.model.Attributes;
import org.alien4cloud.plugin.datagouv_mls.model.Entity;

import lombok.Getter;
import lombok.Setter;

import com.fasterxml.jackson.annotation.JsonInclude;

@Getter
@Setter

@JsonInclude(JsonInclude.Include.NON_NULL)
public class NamespaceAttributes extends Attributes {
   Entity cluster;
}
