package org.alien4cloud.plugin.datagouv_mls.model;

import lombok.Getter;
import lombok.Setter;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@Getter
@Setter

@JsonIgnoreProperties(ignoreUnknown = true)
public class Module {

   String  name;
   String  pdsModuleInstancie;
   Integer pdsModuleInstancieNum;
   String  pdsEcritureModuleInstancie;
   Integer pdsEcritureModuleInstancieNum;
   String  pdsModuleImporte;
   Integer pdsModuleImporteNum;

   public String toString() {
      return "name:" + name
               + ",pdsModuleInstancie:" + pdsModuleInstancie
               + ",pdsModuleInstancieNum:" + pdsModuleInstancieNum
               + ",pdsEcritureModuleInstancie:" + pdsEcritureModuleInstancie
               + ",pdsEcritureModuleInstancieNum:" + pdsEcritureModuleInstancieNum
               + ",pdsModuleImporte:" + pdsModuleImporte
               + ",pdsModuleImporteNum:" + pdsModuleImporteNum;
   }
}
