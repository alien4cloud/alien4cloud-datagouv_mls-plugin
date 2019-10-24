package org.alien4cloud.plugin.datagouv_mls.model;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@Getter
@Setter

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class Entity {

   String typeName;

   String guid;

   Attributes attributes;

   List<Classification> classifications;
}
